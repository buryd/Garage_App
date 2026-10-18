package com.garagedoor.monitor.controller

import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.DoorSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetworkGarageController(
    scope: CoroutineScope,
    private val baseUrl: String,
    private val token: String,
    initial: DoorSnapshot,
) : GarageController {
    private val job = Job(scope.coroutineContext[Job])
    private val worker = CoroutineScope(scope.coroutineContext + job)
    private val _snapshot = MutableStateFlow(initial.copy(online = false))
    override val snapshot: StateFlow<DoorSnapshot> = _snapshot.asStateFlow()

    init {
        worker.launch {
            while (isActive) {
                val reading = fetchStatus()
                val now = System.currentTimeMillis()
                _snapshot.value = if (reading == null) {
                    _snapshot.value.copy(online = false, updatedEpochMs = now)
                } else {
                    applyReading(_snapshot.value, reading, now)
                }
                delay(POLL_MS)
            }
        }
    }

    override fun command(command: Command) {
        worker.launch {
            val reading = postCommand(command)
            val now = System.currentTimeMillis()
            _snapshot.value = if (reading == null) {
                _snapshot.value.copy(online = false, updatedEpochMs = now)
            } else {
                applyReading(_snapshot.value, reading, now)
            }
        }
    }

    override fun close() {
        job.cancel()
    }

    private suspend fun fetchStatus(): DoorReading? = withContext(Dispatchers.IO) {
        request("GET", "$baseUrl/status", null)
    }

    private suspend fun postCommand(command: Command): DoorReading? = withContext(Dispatchers.IO) {
        request("POST", "$baseUrl/command", commandJson(command))
    }

    private fun request(method: String, url: String, body: String?): DoorReading? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (token.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            parseDoorReading(text)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val TIMEOUT_MS = 5_000
    }
}

class SwitchingGarageController(
    private val scope: CoroutineScope,
    initial: GarageController,
) : GarageController {
    private val _snapshot = MutableStateFlow(initial.snapshot.value)
    override val snapshot: StateFlow<DoorSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var delegate: GarageController = initial
    private var collectJob: Job = scope.launch {
        initial.snapshot.collect { _snapshot.value = it }
    }

    fun use(next: GarageController) {
        val previous = delegate
        if (previous === next) return
        delegate = next
        collectJob.cancel()
        previous.close()
        collectJob = scope.launch {
            next.snapshot.collect { _snapshot.value = it }
        }
    }

    override fun command(command: Command) {
        delegate.command(command)
    }
}
