package com.example.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.action.TRIGGER_ALARM"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ALARM"
        const val ACTION_PAUSE_REMINDER = "com.example.action.PAUSE_REMINDER"
        const val EXTRA_SESSION_TITLE = "extra_session_title"
        private const val REMINDER_CHANNEL_ID = "focus_timer_reminders"
        private const val REMINDER_NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TRIGGER_ALARM -> {
                val title = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: "Study Session"
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START_ALARM
                    putExtra(AlarmService.EXTRA_TITLE, title)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_STOP_ALARM -> {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP_ALARM
                }
                context.startService(serviceIntent)
            }
            ACTION_PAUSE_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: "Study Session"
                showPauseReminderNotification(context, title)
            }
        }
    }

    private fun showPauseReminderNotification(context: Context, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Focus Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders when your study timer is left paused"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Timer paused: $title")
            .setContentText("Ready to resume your study session?")
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}
