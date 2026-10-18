package com.garagedoor.monitor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.garagedoor.monitor.R
import com.garagedoor.monitor.model.WatchSettings
import com.garagedoor.monitor.ui.components.InfoCard

private val alertChoices = listOf(0, 5, 10, 15, 30, 60)
private val autoCloseChoices = listOf(0, 15, 30, 60, 120)

@Composable
fun SettingsScreen(
    settings: WatchSettings,
    onAlertMinutes: (Int) -> Unit,
    onAutoCloseMinutes: (Int) -> Unit,
    onBackgroundWatch: (Boolean) -> Unit,
    onController: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingAutoClose by remember { mutableStateOf<Int?>(null) }
    var host by remember(settings.controllerHost) { mutableStateOf(settings.controllerHost) }
    var token by remember(settings.controllerToken) { mutableStateOf(settings.controllerToken) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        InfoCard(stringResource(R.string.settings_alert)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_alert_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MinuteChips(
                choices = alertChoices,
                selected = settings.alertAfterMinutes,
                onSelect = onAlertMinutes,
            )
        }
        InfoCard(stringResource(R.string.settings_autoclose)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_autoclose_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MinuteChips(
                choices = autoCloseChoices,
                selected = settings.autoCloseAfterMinutes,
                onSelect = { minutes ->
                    if (minutes > 0 && settings.autoCloseAfterMinutes == 0) {
                        pendingAutoClose = minutes
                    } else {
                        onAutoCloseMinutes(minutes)
                    }
                },
            )
        }
        InfoCard(stringResource(R.string.settings_watch)) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_watch_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Switch(
                    checked = settings.backgroundWatch,
                    onCheckedChange = onBackgroundWatch,
                )
            }
        }
        InfoCard(stringResource(R.string.settings_safety)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_safety_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard(stringResource(R.string.settings_controller)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_controller_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_private_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            val context = LocalContext.current
            TextButton(
                onClick = {
                    val market = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.tailscale.ipn"),
                    )
                    val opened = runCatching { context.startActivity(market) }.isSuccess
                    if (!opened) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn"),
                            ),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.settings_private_network_install))
            }
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_controller_address)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_controller_token)) },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = { onController(host, token) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.settings_controller_save))
            }
        }
    }

    val pending = pendingAutoClose
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingAutoClose = null },
            title = { Text(stringResource(R.string.autoclose_dialog_title)) },
            text = { Text(stringResource(R.string.autoclose_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAutoCloseMinutes(pending)
                        pendingAutoClose = null
                    },
                ) {
                    Text(stringResource(R.string.autoclose_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAutoClose = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinuteChips(
    choices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(minuteLabel(minutes)) },
            )
        }
    }
}

@Composable
private fun minuteLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.off)
    minutes % 60 == 0 && minutes >= 60 -> stringResource(R.string.hours, minutes / 60)
    else -> stringResource(R.string.minutes, minutes)
}
