package com.garagedoor.monitor.data

import android.content.Context
import com.garagedoor.monitor.controller.GarageController
import com.garagedoor.monitor.controller.NetworkGarageController
import com.garagedoor.monitor.controller.SimulatedGarageController
import com.garagedoor.monitor.controller.SwitchingGarageController
import com.garagedoor.monitor.controller.controllerBaseUrl
import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.DoorEvent
import com.garagedoor.monitor.model.DoorLimits
import com.garagedoor.monitor.model.DoorPhase
import com.garagedoor.monitor.model.DoorSnapshot
import com.garagedoor.monitor.model.EventKind
import com.garagedoor.monitor.model.Travel
import com.garagedoor.monitor.model.WatchSettings
import com.garagedoor.monitor.model.formatDuration
import com.garagedoor.monitor.model.isDue
import com.garagedoor.monitor.model.openPercent
import com.garagedoor.monitor.model.phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class DoorRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val notifier = DoorNotifier(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()

    private val restored = readSnapshot()
    private val simulated: GarageController = SimulatedGarageController(
        scope = scope,
        restored = restored.copy(travel = Travel.Idle),
    )
    private val controller = SwitchingGarageController(scope, simulated)

    val snapshot: StateFlow<DoorSnapshot> = controller.snapshot
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<WatchSettings> = _settings.asStateFlow()
    private val _events = MutableStateFlow(readEvents())
    val events: StateFlow<List<DoorEvent>> = _events.asStateFlow()

    private val nextId = AtomicLong((_events.value.maxOfOrNull { it.id } ?: 0L) + 1L)
    private var previous = controller.snapshot.value
    private var closeIsAutomatic = false
    private var alertedForSince: Long? = null
    private var autoCloseArmedFor: Long? = null

    init {
        notifier.ensureChannels()
        if (restored.travel != Travel.Idle) {
            scope.launch {
                when {
                    restored.position >= 1f -> record(EventKind.Opened, "Fully open")
                    restored.position <= 0f -> record(EventKind.Closed, "Fully closed")
                    else -> record(
                        EventKind.Stopped,
                        "The watch restarted while the door was moving.",
                    )
                }
            }
        }
        scope.launch { watchTransitions() }
        scope.launch { watchTimers() }
        applyController(readSettings())
    }

    fun request(command: Command, automatic: Boolean = false) {
        if (command == Command.Close) {
            closeIsAutomatic = automatic
        } else {
            closeIsAutomatic = false
        }
        controller.command(command)
    }

    fun updateSettings(settings: WatchSettings) {
        val linkChanged = settings.controllerHost != _settings.value.controllerHost ||
            settings.controllerToken != _settings.value.controllerToken
        _settings.value = settings
        prefs.edit()
            .putInt(KEY_ALERT, settings.alertAfterMinutes)
            .putInt(KEY_AUTO, settings.autoCloseAfterMinutes)
            .putBoolean(KEY_WATCH, settings.backgroundWatch)
            .putString(KEY_HOST, settings.controllerHost)
            .putString(KEY_TOKEN, settings.controllerToken)
            .apply()
        if (linkChanged) applyController(settings)
    }

    private fun applyController(settings: WatchSettings) {
        val baseUrl = controllerBaseUrl(settings.controllerHost)
        if (baseUrl == null) {
            controller.use(simulated)
            return
        }
        controller.use(
            NetworkGarageController(
                scope = scope,
                baseUrl = baseUrl,
                token = settings.controllerToken,
                initial = controller.snapshot.value,
            ),
        )
    }

    private suspend fun watchTransitions() {
        controller.snapshot.collect { current ->
            val prior = previous
            previous = current
            persistSnapshot(current, force = prior.travel != current.travel)
            if (prior.travel != Travel.Idle && current.travel == Travel.Idle) {
                when {
                    current.position == 1f && prior.travel == Travel.Opening -> {
                        closeIsAutomatic = false
                        record(EventKind.Opened, "Fully open")
                    }
                    current.position == 0f && prior.travel == Travel.Closing -> {
                        val automatic = closeIsAutomatic
                        closeIsAutomatic = false
                        if (automatic) {
                            record(EventKind.AutoClosed, "Left open, so the app sent a close command")
                        } else {
                            record(EventKind.Closed, "Fully closed")
                        }
                    }
                    else -> {
                        closeIsAutomatic = false
                        record(
                            EventKind.Stopped,
                            "Stopped at ${openPercent(current.position)}% open",
                        )
                    }
                }
            }
        }
    }

    private suspend fun watchTimers() {
        while (true) {
            delay(1_000)
            val current = controller.snapshot.value
            val watch = _settings.value
            val idleAndNotShut = current.travel == Travel.Idle &&
                current.online &&
                current.phase() != DoorPhase.Closed
            if (!idleAndNotShut) continue
            val now = System.currentTimeMillis()
            val since = current.stateSinceEpochMs
            if (isDue(now, since, watch.alertAfterMinutes) && alertedForSince != since) {
                alertedForSince = since
                val openFor = now - since
                record(EventKind.LeftOpen, "Open for ${formatDuration(openFor)}")
                notifier.showLeftOpen(openFor)
            }
            if (
                isDue(now, since, watch.autoCloseAfterMinutes) &&
                autoCloseArmedFor != since &&
                current.travel == Travel.Idle
            ) {
                autoCloseArmedFor = since
                request(Command.Close, automatic = true)
            }
        }
    }

    private var lastPersistAt = 0L

    private fun persistSnapshot(snapshot: DoorSnapshot, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 1_000L) return
        lastPersistAt = now
        prefs.edit()
            .putFloat(KEY_POS, snapshot.position)
            .putLong(KEY_SINCE, snapshot.stateSinceEpochMs)
            .putString(KEY_TRAVEL, snapshot.travel.name)
            .apply()
    }

    private suspend fun record(kind: EventKind, detail: String) {
        gate.withLock {
            val event = DoorEvent(
                id = nextId.getAndIncrement(),
                epochMs = System.currentTimeMillis(),
                kind = kind,
                detail = detail,
            )
            val updated = (listOf(event) + _events.value).take(DoorLimits.EVENT_LIMIT)
            _events.value = updated
            val array = JSONArray()
            updated.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("t", item.epochMs)
                        .put("k", item.kind.name)
                        .put("d", item.detail),
                )
            }
            prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
        }
    }

    private fun readSnapshot(): DoorSnapshot {
        val now = System.currentTimeMillis()
        val travel = prefs.getString(KEY_TRAVEL, Travel.Idle.name)
            ?.let { runCatching { Travel.valueOf(it) }.getOrDefault(Travel.Idle) }
            ?: Travel.Idle
        return DoorSnapshot(
            position = prefs.getFloat(KEY_POS, 0f).coerceIn(0f, 1f),
            travel = travel,
            stateSinceEpochMs = prefs.getLong(KEY_SINCE, now),
            updatedEpochMs = now,
            online = true,
        )
    }

    private fun readSettings(): WatchSettings = WatchSettings(
        alertAfterMinutes = prefs.getInt(KEY_ALERT, 10),
        autoCloseAfterMinutes = prefs.getInt(KEY_AUTO, 0),
        backgroundWatch = prefs.getBoolean(KEY_WATCH, true),
        controllerHost = prefs.getString(KEY_HOST, "").orEmpty(),
        controllerToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
    )

    private fun readEvents(): List<DoorEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val kind = runCatching { EventKind.valueOf(item.optString("k")) }.getOrNull() ?: continue
                add(
                    DoorEvent(
                        id = item.optLong("id"),
                        epochMs = item.optLong("t"),
                        kind = kind,
                        detail = item.optString("d"),
                    ),
                )
            }
        }
    }

    companion object {
        private const val PREFS = "garage_door"
        private const val KEY_POS = "position"
        private const val KEY_SINCE = "since"
        private const val KEY_TRAVEL = "travel"
        private const val KEY_ALERT = "alert_minutes"
        private const val KEY_AUTO = "auto_close_minutes"
        private const val KEY_WATCH = "background_watch"
        private const val KEY_HOST = "controller_host"
        private const val KEY_TOKEN = "controller_token"
        private const val KEY_EVENTS = "events"
    }
}
