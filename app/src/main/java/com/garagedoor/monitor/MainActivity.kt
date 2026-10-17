package com.garagedoor.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagedoor.monitor.ui.GarageApp
import com.garagedoor.monitor.ui.GarageViewModel
import com.garagedoor.monitor.ui.theme.GarageTheme
import com.garagedoor.monitor.watch.GarageWatchService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GarageTheme {
                val viewModel: GarageViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var notificationsGranted by remember {
                    mutableStateOf(hasNotificationPermission())
                }
                var asked by rememberSaveable { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    notificationsGranted = granted
                    viewModel.setBackgroundWatch(granted)
                }
                val permissionWasGranted = rememberUpdatedState(notificationsGranted)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                        val grantedNow = hasNotificationPermission()
                        if (permissionWasGranted.value && !grantedNow) {
                            viewModel.setBackgroundWatch(false)
                        }
                        notificationsGranted = grantedNow
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(state.settings.backgroundWatch, notificationsGranted) {
                    if (!state.settings.backgroundWatch) {
                        stopService(Intent(this@MainActivity, GarageWatchService::class.java))
                        return@LaunchedEffect
                    }
                    if (needsNotificationPermission() && !notificationsGranted) {
                        if (!asked) {
                            asked = true
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        return@LaunchedEffect
                    }
                    startDoorWatch()
                }

                GarageApp(
                    state = state,
                    onOpen = viewModel::askToOpen,
                    onClose = viewModel::askToClose,
                    onStop = viewModel::stopDoor,
                    onConfirm = viewModel::confirmPending,
                    onDismissConfirm = viewModel::dismissConfirm,
                    onAlertMinutes = viewModel::setAlertMinutes,
                    onAutoCloseMinutes = viewModel::setAutoCloseMinutes,
                    onBackgroundWatch = { enabled ->
                        if (!enabled) {
                            viewModel.setBackgroundWatch(false)
                            return@GarageApp
                        }
                        if (needsNotificationPermission() && !notificationsGranted) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setBackgroundWatch(true)
                        }
                    },
                )
            }
        }
    }

    private fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33

    private fun hasNotificationPermission(): Boolean {
        if (!needsNotificationPermission()) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startDoorWatch() {
        try {
            val intent = Intent(this, GarageWatchService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (_: IllegalStateException) {
            // The activity was no longer allowed to start a foreground service.
        } catch (_: SecurityException) {
            // Notification permission or the special-use service type was refused.
        }
    }
}
