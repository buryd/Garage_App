package com.garagedoor.monitor.watch

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.garagedoor.monitor.GarageApplication
import com.garagedoor.monitor.data.DoorNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GarageWatchService : Service() {
    private var scope: CoroutineScope? = null
    private var watching = false
    private lateinit var notifier: DoorNotifier

    override fun onCreate() {
        super.onCreate()
        notifier = DoorNotifier(this)
        notifier.ensureChannels()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as GarageApplication).repository
        val notification = notifier.ongoing(repository.snapshot.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                DoorNotifier.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(DoorNotifier.ONGOING_ID, notification)
        }
        if (!watching) {
            watching = true
            val jobScope = scope
            jobScope?.launch {
                repository.snapshot.collect { snapshot ->
                    val manager = getSystemService(android.app.NotificationManager::class.java)
                    manager.notify(DoorNotifier.ONGOING_ID, notifier.ongoing(snapshot))
                }
            }
            jobScope?.launch {
                while (isActive) {
                    delay(30_000)
                    val manager = getSystemService(android.app.NotificationManager::class.java)
                    manager.notify(
                        DoorNotifier.ONGOING_ID,
                        notifier.ongoing(repository.snapshot.value),
                    )
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watching = false
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
