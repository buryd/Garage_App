package com.garagedoor.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.garagedoor.monitor.R
import com.garagedoor.monitor.model.DoorEvent
import com.garagedoor.monitor.model.EventKind
import com.garagedoor.monitor.ui.theme.statusColor
import com.garagedoor.monitor.model.DoorPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(
    events: List<DoorEvent>,
    nowEpochMs: Long,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.activity_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
        return
    }

    val rows = activityRows(events, nowEpochMs)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ActivityRow.Header -> Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                is ActivityRow.Entry -> EventLine(row.event)
            }
        }
    }
}

@Composable
private fun EventLine(event: DoorEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = event.kind.icon(),
                contentDescription = null,
                tint = statusColor(event.kind.phaseTint()),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = event.kind.label(), style = MaterialTheme.typography.titleMedium)
            if (event.detail.isNotBlank()) {
                Text(
                    text = event.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
            Text(
                text = clockLabel(event.epochMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventKind.label(): String = stringResource(
    when (this) {
        EventKind.Opened -> R.string.event_opened
        EventKind.Closed -> R.string.event_closed
        EventKind.Stopped -> R.string.event_stopped
        EventKind.AutoClosed -> R.string.event_auto_closed
        EventKind.LeftOpen -> R.string.event_left_open
    },
)

private fun EventKind.icon(): ImageVector = when (this) {
    EventKind.Opened -> Icons.Filled.KeyboardArrowUp
    EventKind.Closed -> Icons.Filled.KeyboardArrowDown
    EventKind.Stopped -> Icons.Filled.Pause
    EventKind.AutoClosed -> Icons.Filled.Schedule
    EventKind.LeftOpen -> Icons.Filled.Notifications
}

private fun EventKind.phaseTint(): DoorPhase = when (this) {
    EventKind.Opened, EventKind.LeftOpen -> DoorPhase.Open
    EventKind.Closed, EventKind.AutoClosed -> DoorPhase.Closed
    EventKind.Stopped -> DoorPhase.Partial
}

private sealed interface ActivityRow {
    val key: String

    data class Header(val id: String, val label: String) : ActivityRow {
        override val key: String = "header-$id"
    }

    data class Entry(val event: DoorEvent) : ActivityRow {
        override val key: String = "event-${event.id}"
    }
}

@Composable
private fun activityRows(events: List<DoorEvent>, nowEpochMs: Long): List<ActivityRow> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
    val yesterday = stringResource(R.string.event_yesterday)
    val todayLabel = stringResource(R.string.event_today)
    val dateFormat = DateTimeFormatter.ofPattern("MMM d")
    val grouped = events.groupBy { event ->
        Instant.ofEpochMilli(event.epochMs).atZone(zone).toLocalDate()
    }
    return buildList {
        grouped.forEach { (date, items) ->
            val label = when (date) {
                today -> todayLabel
                today.minusDays(1) -> yesterday
                else -> date.format(dateFormat)
            }
            add(ActivityRow.Header(date.toString(), label))
            items.forEach { add(ActivityRow.Entry(it)) }
        }
    }
}

private fun clockLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
