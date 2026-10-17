package com.garagedoor.monitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.garagedoor.monitor.MainActivity
import com.garagedoor.monitor.R
import com.garagedoor.monitor.model.DoorPhase
import com.garagedoor.monitor.model.DoorSnapshot
import com.garagedoor.monitor.model.formatDuration
import com.garagedoor.monitor.model.openPercent
import com.garagedoor.monitor.model.phase

class DoorNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val status = NotificationChannel(
            STATUS_CHANNEL,
            context.getString(R.string.channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            ALERT_CHANNEL,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
        }
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(alerts)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ongoing(snapshot: DoorSnapshot): android.app.Notification {
        val phase = snapshot.phase()
        val openFor = (System.currentTimeMillis() - snapshot.stateSinceEpochMs).coerceAtLeast(0L)
        val (title, text) = when (phase) {
            DoorPhase.Closed -> context.getString(R.string.notify_closed) to
                context.getString(R.string.detail_closed)
            DoorPhase.Open -> context.getString(R.string.notify_open) to
                context.getString(R.string.notify_open_for, formatDuration(openFor))
            DoorPhase.Opening -> context.getString(R.string.notify_opening) to
                context.getString(R.string.detail_opening)
            DoorPhase.Closing -> context.getString(R.string.notify_closing) to
                context.getString(R.string.detail_closing)
            DoorPhase.Partial -> context.getString(R.string.notify_partial) to
                context.getString(R.string.notify_partial_for, openPercent(snapshot.position))
            DoorPhase.Offline -> context.getString(R.string.notify_offline) to
                context.getString(R.string.detail_offline)
        }
        return NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_door)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun showLeftOpen(openForMs: Long) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_door)
            .setContentTitle(context.getString(R.string.notify_alert_title))
            .setContentText(
                context.getString(R.string.notify_alert_body, formatDuration(openForMs)),
            )
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ALERT_ID, notification)
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val STATUS_CHANNEL = "door_status"
        const val ALERT_CHANNEL = "door_alerts"
        const val ONGOING_ID = 1001
        const val ALERT_ID = 1002
    }
}
