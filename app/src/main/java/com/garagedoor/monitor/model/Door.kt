package com.garagedoor.monitor.model

import kotlin.math.roundToInt

enum class Travel {
    Idle,
    Opening,
    Closing,
}

enum class Command {
    Open,
    Close,
    Stop,
}

enum class DoorPhase {
    Closed,
    Open,
    Opening,
    Closing,
    Partial,
    Offline,
}

enum class EventKind {
    Opened,
    Closed,
    Stopped,
    AutoClosed,
    LeftOpen,
}

enum class ConfirmKind {
    Open,
    Close,
}

data class DoorSnapshot(
    val position: Float,
    val travel: Travel,
    val stateSinceEpochMs: Long,
    val updatedEpochMs: Long,
    val online: Boolean = true,
)

data class DoorEvent(
    val id: Long,
    val epochMs: Long,
    val kind: EventKind,
    val detail: String,
)

data class WatchSettings(
    val alertAfterMinutes: Int = 10,
    val autoCloseAfterMinutes: Int = 0,
    val backgroundWatch: Boolean = true,
    val controllerHost: String = "",
    val controllerToken: String = "",
)

object DoorLimits {
    const val CLOSED_AT = 0.02f
    const val OPEN_AT = 0.98f
    const val TRAVEL_MS = 12_000L
    const val EVENT_LIMIT = 80
}

fun DoorSnapshot.phase(): DoorPhase {
    if (!online) return DoorPhase.Offline
    return when (travel) {
        Travel.Opening -> DoorPhase.Opening
        Travel.Closing -> DoorPhase.Closing
        Travel.Idle -> when {
            position <= DoorLimits.CLOSED_AT -> DoorPhase.Closed
            position >= DoorLimits.OPEN_AT -> DoorPhase.Open
            else -> DoorPhase.Partial
        }
    }
}

fun deadlineEpochMs(sinceEpochMs: Long, minutes: Int): Long? {
    if (minutes <= 0) return null
    return sinceEpochMs + minutes * 60_000L
}

fun isDue(nowEpochMs: Long, sinceEpochMs: Long, minutes: Int): Boolean {
    val deadline = deadlineEpochMs(sinceEpochMs, minutes) ?: return false
    return nowEpochMs >= deadline
}

fun openPercent(position: Float): Int =
    (position.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
