package com.garagedoor.monitor.controller

import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.DoorLimits
import com.garagedoor.monitor.model.DoorSnapshot
import com.garagedoor.monitor.model.Travel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The wall unit the phone talks to.
 *
 * Position is 0 when the door is shut and 1 when it is fully open.
 * Open, close, and stop are the only commands. A hardware controller can
 * replace [SimulatedGarageController] without changing the screens.
 */
interface GarageController {
    val snapshot: StateFlow<DoorSnapshot>
    fun command(command: Command)
}

class SimulatedGarageController(
    private val scope: CoroutineScope,
    restored: DoorSnapshot,
    private val travelMillis: Long = DoorLimits.TRAVEL_MS,
) : GarageController {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(
        restored.copy(travel = Travel.Idle, online = true),
    )
    override val snapshot: StateFlow<DoorSnapshot> = _snapshot.asStateFlow()
    private var generation = 0

    override fun command(command: Command) {
        scope.launch {
            val started = mutex.withLock {
                val current = _snapshot.value
                val now = System.currentTimeMillis()
                when (command) {
                    Command.Stop -> {
                        if (current.travel != Travel.Idle) {
                            generation += 1
                            _snapshot.value = current.copy(
                                travel = Travel.Idle,
                                stateSinceEpochMs = now,
                                updatedEpochMs = now,
                            )
                        }
                        null
                    }
                    Command.Open -> arm(current, Travel.Opening, now)
                    Command.Close -> arm(current, Travel.Closing, now)
                }
            }
            if (started != null) {
                runTravel(started.token, started.direction)
            }
        }
    }

    private fun arm(current: DoorSnapshot, direction: Travel, now: Long): TravelStart? {
        if (current.travel == direction) return null
        val alreadyThere = when (direction) {
            Travel.Opening -> current.position >= DoorLimits.OPEN_AT
            Travel.Closing -> current.position <= DoorLimits.CLOSED_AT
            Travel.Idle -> true
        }
        if (alreadyThere) return null
        generation += 1
        _snapshot.value = current.copy(
            travel = direction,
            stateSinceEpochMs = now,
            updatedEpochMs = now,
        )
        return TravelStart(generation, direction)
    }

    private suspend fun runTravel(token: Int, direction: Travel) {
        val tickMs = 50L
        while (true) {
            delay(tickMs)
            val finished = mutex.withLock {
                if (token != generation) return@withLock true
                val current = _snapshot.value
                if (current.travel != direction) return@withLock true
                val delta = tickMs.toFloat() / travelMillis.toFloat()
                val raw = when (direction) {
                    Travel.Opening -> current.position + delta
                    Travel.Closing -> current.position - delta
                    Travel.Idle -> current.position
                }
                val atEnd = when (direction) {
                    Travel.Opening -> raw >= 1f
                    Travel.Closing -> raw <= 0f
                    Travel.Idle -> true
                }
                val now = System.currentTimeMillis()
                _snapshot.value = current.copy(
                    position = if (atEnd) {
                        if (direction == Travel.Opening) 1f else 0f
                    } else {
                        raw.coerceIn(0f, 1f)
                    },
                    travel = if (atEnd) Travel.Idle else direction,
                    stateSinceEpochMs = if (atEnd) now else current.stateSinceEpochMs,
                    updatedEpochMs = now,
                )
                atEnd
            }
            if (finished) return
        }
    }

    private data class TravelStart(val token: Int, val direction: Travel)
}
