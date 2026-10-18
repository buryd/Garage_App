package com.garagedoor.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garagedoor.monitor.R
import com.garagedoor.monitor.model.ConfirmKind
import com.garagedoor.monitor.model.DoorPhase
import com.garagedoor.monitor.model.WatchSettings
import com.garagedoor.monitor.model.formatDuration
import com.garagedoor.monitor.model.openPercent
import com.garagedoor.monitor.ui.DoorUiState
import com.garagedoor.monitor.ui.components.DoorGraphic
import com.garagedoor.monitor.ui.components.InfoCard
import com.garagedoor.monitor.ui.theme.GarageTheme
import com.garagedoor.monitor.ui.theme.statusColor

@Composable
fun DoorScreen(
    state: DoorUiState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val phaseTitle = phaseTitle(state.phase)
    val detail = phaseDetail(state)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wordmark),
                style = MaterialTheme.typography.titleLarge,
            )
            LivePill(online = state.phase != DoorPhase.Offline)
        }
        Spacer(Modifier.height(8.dp))
        DoorGraphic(
            position = state.position,
            phase = state.phase,
            description = stringResource(R.string.cd_door, phaseTitle),
        )
        Text(
            text = phaseTitle,
            style = MaterialTheme.typography.displayLarge,
            color = statusColor(state.phase),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        WatchCard(state)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when (state.phase) {
                    DoorPhase.Closed -> onOpen()
                    DoorPhase.Open, DoorPhase.Partial -> onClose()
                    DoorPhase.Opening, DoorPhase.Closing -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStop()
                    }
                    DoorPhase.Offline -> Unit
                }
            },
            enabled = state.phase != DoorPhase.Offline,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = actionColors(state.phase),
        ) {
            Text(
                text = when (state.phase) {
                    DoorPhase.Closed -> stringResource(R.string.action_open)
                    DoorPhase.Open, DoorPhase.Partial -> stringResource(R.string.action_close)
                    DoorPhase.Opening, DoorPhase.Closing -> stringResource(R.string.action_stop)
                    DoorPhase.Offline -> stringResource(R.string.action_offline)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (state.phase == DoorPhase.Partial) {
            TextButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_open_rest))
            }
        }
        if (state.phase == DoorPhase.Opening || state.phase == DoorPhase.Closing) {
            Text(
                text = stringResource(R.string.stop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = if (state.settings.controllerHost.isBlank()) {
                stringResource(R.string.controller_line)
            } else {
                stringResource(R.string.controller_line_remote)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
    }

    val confirm = state.confirm
    if (confirm != null) {
        val closing = confirm == ConfirmKind.Close
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = {
                Text(
                    stringResource(
                        if (closing) R.string.confirm_close_title else R.string.confirm_open_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (closing) R.string.confirm_close_body else R.string.confirm_open_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (closing) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onConfirm()
                    },
                ) {
                    Text(
                        stringResource(
                            if (closing) R.string.confirm_close_action else R.string.confirm_open_action,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WatchCard(state: DoorUiState) {
    when (state.phase) {
        DoorPhase.Closed -> InfoCard(stringResource(R.string.card_watching)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.settings.alertAfterMinutes > 0) {
                    stringResource(R.string.card_closed_alert, state.settings.alertAfterMinutes)
                } else {
                    stringResource(R.string.card_closed_no_alert)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DoorPhase.Open, DoorPhase.Partial -> InfoCard(stringResource(R.string.card_while_open)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = alertLine(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = autoCloseLine(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        DoorPhase.Opening, DoorPhase.Closing -> InfoCard(stringResource(R.string.card_moving)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.card_moving_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DoorPhase.Offline -> InfoCard(stringResource(R.string.card_offline_title)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.card_offline_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun alertLine(state: DoorUiState): String {
    val remaining = state.millisUntilAlert
    return when {
        state.settings.alertAfterMinutes <= 0 -> stringResource(R.string.card_closed_no_alert)
        remaining == null -> stringResource(R.string.card_closed_no_alert)
        remaining <= 0L -> stringResource(R.string.card_alert_sent)
        else -> stringResource(R.string.card_alert_in, formatDuration(remaining))
    }
}

@Composable
private fun autoCloseLine(state: DoorUiState): String {
    val remaining = state.millisUntilAutoClose
    return when {
        state.settings.autoCloseAfterMinutes <= 0 || remaining == null ->
            stringResource(R.string.card_autoclose_off)
        remaining <= 0L -> stringResource(R.string.card_autoclose_now)
        else -> stringResource(R.string.card_autoclose_in, formatDuration(remaining))
    }
}

@Composable
private fun phaseTitle(phase: DoorPhase): String = stringResource(
    when (phase) {
        DoorPhase.Closed -> R.string.phase_closed
        DoorPhase.Open -> R.string.phase_open
        DoorPhase.Opening -> R.string.phase_opening
        DoorPhase.Closing -> R.string.phase_closing
        DoorPhase.Partial -> R.string.phase_partial
        DoorPhase.Offline -> R.string.phase_offline
    },
)

@Composable
private fun phaseDetail(state: DoorUiState): String = when (state.phase) {
    DoorPhase.Closed -> stringResource(R.string.detail_closed)
    DoorPhase.Open -> stringResource(R.string.detail_open, formatDuration(state.openForMillis ?: 0L))
    DoorPhase.Opening -> stringResource(R.string.detail_opening)
    DoorPhase.Closing -> stringResource(R.string.detail_closing)
    DoorPhase.Partial -> stringResource(R.string.detail_partial, openPercent(state.position))
    DoorPhase.Offline -> stringResource(R.string.detail_offline)
}

@Composable
private fun LivePill(online: Boolean) {
    val dot = if (online) Color(0xFF2E9D5C) else MaterialTheme.colorScheme.outline
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
            Text(
                text = stringResource(if (online) R.string.live else R.string.offline),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun actionColors(phase: DoorPhase) = ButtonDefaults.buttonColors(
    containerColor = when (phase) {
        DoorPhase.Closed -> Color(0xFF1F6B45)
        DoorPhase.Open, DoorPhase.Partial -> Color(0xFFC4552A)
        DoorPhase.Opening, DoorPhase.Closing -> Color(0xFF8E2F2F)
        DoorPhase.Offline -> MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor = Color.White,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DoorScreenPreview() {
    GarageTheme {
        DoorScreen(
            state = DoorUiState(
                phase = DoorPhase.Open,
                position = 1f,
                openForMillis = 4 * 60_000L + 12_000L,
                millisUntilAlert = 6 * 60_000L,
                millisUntilAutoClose = null,
                confirm = null,
                settings = WatchSettings(alertAfterMinutes = 10, autoCloseAfterMinutes = 0),
                events = emptyList(),
                nowEpochMs = 0L,
            ),
            onOpen = {},
            onClose = {},
            onStop = {},
            onConfirm = {},
            onDismissConfirm = {},
        )
    }
}
