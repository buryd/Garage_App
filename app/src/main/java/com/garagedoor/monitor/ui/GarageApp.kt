package com.garagedoor.monitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.garagedoor.monitor.R
import com.garagedoor.monitor.ui.screens.ActivityScreen
import com.garagedoor.monitor.ui.screens.DoorScreen
import com.garagedoor.monitor.ui.screens.SettingsScreen

private enum class Dest(
    val route: String,
    val label: Int,
    val icon: ImageVector,
) {
    Door("door", R.string.nav_door, Icons.Filled.Home),
    Activity("activity", R.string.nav_activity, Icons.Filled.History),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun GarageApp(
    state: DoorUiState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onAlertMinutes: (Int) -> Unit,
    onAutoCloseMinutes: (Int) -> Unit,
    onBackgroundWatch: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Dest.entries.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Door.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Door.route) {
                DoorScreen(
                    state = state,
                    onOpen = onOpen,
                    onClose = onClose,
                    onStop = onStop,
                    onConfirm = onConfirm,
                    onDismissConfirm = onDismissConfirm,
                )
            }
            composable(Dest.Activity.route) {
                ActivityScreen(events = state.events, nowEpochMs = state.nowEpochMs)
            }
            composable(Dest.Settings.route) {
                SettingsScreen(
                    settings = state.settings,
                    onAlertMinutes = onAlertMinutes,
                    onAutoCloseMinutes = onAutoCloseMinutes,
                    onBackgroundWatch = onBackgroundWatch,
                )
            }
        }
    }
}
