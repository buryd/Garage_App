package com.garagedoor.monitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garagedoor.monitor.GarageApplication
import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.ConfirmKind
import com.garagedoor.monitor.model.DoorEvent
import com.garagedoor.monitor.model.DoorPhase
import com.garagedoor.monitor.model.Travel
import com.garagedoor.monitor.model.WatchSettings
import com.garagedoor.monitor.model.deadlineEpochMs
import com.garagedoor.monitor.model.phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class DoorUiState(
    val phase: DoorPhase,
    val position: Float,
    val openForMillis: Long?,
    val millisUntilAlert: Long?,
    val millisUntilAutoClose: Long?,
    val confirm: ConfirmKind?,
    val settings: WatchSettings,
    val events: List<DoorEvent>,
    val nowEpochMs: Long,
)

class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as GarageApplication).repository
    private val confirm = MutableStateFlow<ConfirmKind?>(null)
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState: StateFlow<DoorUiState> = combine(
        repository.snapshot,
        repository.settings,
        repository.events,
        confirm,
        ticker,
    ) { snapshot, settings, events, pending, now ->
        val idleNotShut = snapshot.travel == Travel.Idle &&
            snapshot.online &&
            snapshot.phase().let { it == DoorPhase.Open || it == DoorPhase.Partial }
        DoorUiState(
            phase = snapshot.phase(),
            position = snapshot.position,
            openForMillis = if (idleNotShut) (now - snapshot.stateSinceEpochMs).coerceAtLeast(0L) else null,
            millisUntilAlert = if (idleNotShut) {
                deadlineEpochMs(snapshot.stateSinceEpochMs, settings.alertAfterMinutes)?.minus(now)
            } else {
                null
            },
            millisUntilAutoClose = if (idleNotShut) {
                deadlineEpochMs(snapshot.stateSinceEpochMs, settings.autoCloseAfterMinutes)?.minus(now)
            } else {
                null
            },
            confirm = pending,
            settings = settings,
            events = events,
            nowEpochMs = now,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialState(),
    )

    fun askToOpen() {
        confirm.value = ConfirmKind.Open
    }

    fun askToClose() {
        confirm.value = ConfirmKind.Close
    }

    fun dismissConfirm() {
        confirm.value = null
    }

    fun confirmPending() {
        when (confirm.value) {
            ConfirmKind.Open -> repository.request(Command.Open)
            ConfirmKind.Close -> repository.request(Command.Close)
            null -> Unit
        }
        confirm.value = null
    }

    fun stopDoor() {
        confirm.value = null
        repository.request(Command.Stop)
    }

    fun setAlertMinutes(minutes: Int) {
        repository.updateSettings(repository.settings.value.copy(alertAfterMinutes = minutes))
    }

    fun setAutoCloseMinutes(minutes: Int) {
        repository.updateSettings(repository.settings.value.copy(autoCloseAfterMinutes = minutes))
    }

    fun setBackgroundWatch(enabled: Boolean) {
        repository.updateSettings(repository.settings.value.copy(backgroundWatch = enabled))
    }

    private fun initialState(): DoorUiState {
        val snapshot = repository.snapshot.value
        val settings = repository.settings.value
        val now = System.currentTimeMillis()
        val idleNotShut = snapshot.travel == Travel.Idle &&
            snapshot.online &&
            snapshot.phase().let { it == DoorPhase.Open || it == DoorPhase.Partial }
        return DoorUiState(
            phase = snapshot.phase(),
            position = snapshot.position,
            openForMillis = if (idleNotShut) (now - snapshot.stateSinceEpochMs).coerceAtLeast(0L) else null,
            millisUntilAlert = if (idleNotShut) {
                deadlineEpochMs(snapshot.stateSinceEpochMs, settings.alertAfterMinutes)?.minus(now)
            } else {
                null
            },
            millisUntilAutoClose = if (idleNotShut) {
                deadlineEpochMs(snapshot.stateSinceEpochMs, settings.autoCloseAfterMinutes)?.minus(now)
            } else {
                null
            },
            confirm = null,
            settings = settings,
            events = repository.events.value,
            nowEpochMs = now,
        )
    }
}
