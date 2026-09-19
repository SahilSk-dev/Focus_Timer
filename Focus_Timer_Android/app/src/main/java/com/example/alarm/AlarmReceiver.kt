package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.action.TRIGGER_ALARM"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ALARM"
        const val EXTRA_SESSION_TITLE = "extra_session_title"
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
        }
    }
}
