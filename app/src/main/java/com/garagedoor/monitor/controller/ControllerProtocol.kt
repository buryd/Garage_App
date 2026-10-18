package com.garagedoor.monitor.controller

import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.DoorSnapshot
import com.garagedoor.monitor.model.Travel
import com.garagedoor.monitor.model.phase
import java.net.URI

data class DoorReading(
    val position: Float,
    val travel: Travel,
)

fun controllerBaseUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://$trimmed"
    }
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (host.isBlank()) return null
    val port = if (uri.port == -1) 8080 else uri.port
    val path = uri.path?.trimEnd('/').orEmpty()
    return "${uri.scheme}://$host:$port$path"
}

fun commandJson(command: Command): String =
    """{"command":"${command.name.lowercase()}"}"""

fun parseDoorReading(body: String): DoorReading? {
    val positionMatch = POSITION.find(body) ?: return null
    val travelMatch = TRAVEL.find(body) ?: return null
    val position = positionMatch.groupValues[1].toFloatOrNull() ?: return null
    if (position.isNaN()) return null
    val travel = when (travelMatch.groupValues[1].lowercase()) {
        "opening" -> Travel.Opening
        "closing" -> Travel.Closing
        "idle" -> Travel.Idle
        else -> return null
    }
    return DoorReading(position.coerceIn(0f, 1f), travel)
}

private val POSITION = Regex("\"position\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
private val TRAVEL = Regex("\"travel\"\\s*:\\s*\"([A-Za-z]+)\"")

fun applyReading(previous: DoorSnapshot, reading: DoorReading, now: Long): DoorSnapshot {
    val next = previous.copy(
        position = reading.position,
        travel = reading.travel,
        updatedEpochMs = now,
        online = true,
    )
    val restingBucketChanged = reading.travel == Travel.Idle &&
        previous.travel == Travel.Idle &&
        previous.phase() != next.phase()
    val resetSince = !previous.online || previous.travel != reading.travel || restingBucketChanged
    return if (resetSince) next.copy(stateSinceEpochMs = now) else next
}
