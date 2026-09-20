package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.example.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ALARM"
        const val ACTION_START_TRACKING = "com.example.action.START_TRACKING"
        const val ACTION_UPDATE_TRACKING = "com.example.action.UPDATE_TRACKING"
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_TIME_TEXT = "extra_time_text"
        const val EXTRA_IS_MUTED = "extra_is_muted"
        const val CHANNEL_ID = "focus_alarm_channel_high"
        const val TRACKING_CHANNEL_ID = "focus_timer_running_channel"
        const val NOTIFICATION_ID = 2001
        const val TRACKING_NOTIFICATION_ID = 2002

        private val _isAlarmRinging = MutableStateFlow(false)
        val isAlarmRinging = _isAlarmRinging.asStateFlow()

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }

        fun startTracking(context: Context, title: String, timeText: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TIME_TEXT, timeText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Study Session"
                val timeText = intent.getStringExtra(EXTRA_TIME_TEXT) ?: "Running"
                startTrackingForeground(title, timeText)
            }
            ACTION_UPDATE_TRACKING -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Study Session"
                val timeText = intent.getStringExtra(EXTRA_TIME_TEXT) ?: "Running"
                updateTrackingNotification(title, timeText)
            }
            ACTION_START_ALARM -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Study Session"
                val isMuted = intent.getBooleanExtra(EXTRA_IS_MUTED, false)
                startAlarm(title, isMuted)
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTrackingForeground(title: String, timeText: String) {
        // If alarm is already ringing, don't override with tracking
        if (_isAlarmRinging.value) return

        createTrackingNotificationChannel()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("⏱️ Study Session Active: $title")
            .setContentText("Focusing: $timeText (Running in background)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TRACKING_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(TRACKING_NOTIFICATION_ID, notification)
        }
    }

    private fun updateTrackingNotification(title: String, timeText: String) {
        if (_isAlarmRinging.value) return
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("⏱️ Study Session Active: $title")
            .setContentText("Focusing: $timeText (Running in background)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(TRACKING_NOTIFICATION_ID, notification)
    }

    private fun createTrackingNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Active Focus Timer"
            val descriptionText = "Shows continuous countdown while your study timer is active"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(TRACKING_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startAlarm(title: String, isMuted: Boolean = false) {
        _isAlarmRinging.value = true

        // Acquire wake lock to keep screen/CPU awake
        acquireWakeLock()

        // Create Notification Channel
        createNotificationChannel()

        // PendingIntent to open App
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent to STOP ALARM directly from notification
        val stopIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Time is Up! ($title)")
            .setContentText("Focus session completed. Tap to open or stop alarm.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "STOP ALARM", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val prefs = getSharedPreferences("focus_timer_prefs", Context.MODE_PRIVATE)
        val isSoundMuted = isMuted || prefs.getBoolean("is_sound_muted", false)

        // Start playing alarm ringtone ONLY if sound is not muted
        if (!isSoundMuted) {
            playRingtone()
        }

        // Start vibration
        startVibration()
    }

    private fun playRingtone() {
        try {
            val prefs = getSharedPreferences("focus_timer_prefs", Context.MODE_PRIVATE)
            val isVolumeBoost = prefs.getBoolean("alarm_volume_boost", true)
            if (isVolumeBoost) {
                // Maximize volume only when user enabled Maximum Volume Boost
                AlarmSoundManager.ensureMaxAlarmVolume(applicationContext)
            }
            val toneId = prefs.getString("alarm_tone_id", "ultra_siren") ?: "ultra_siren"
            val toneOption = AlarmToneOption.fromId(toneId)
            val customUriStr = prefs.getString("alarm_custom_uri", null)

            mediaPlayer?.release()
            mediaPlayer = null
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null

            if (toneOption == AlarmToneOption.SYSTEM_DEFAULT || (toneOption == AlarmToneOption.CUSTOM_PICKER && !customUriStr.isNullOrBlank())) {
                val alarmUri = if (toneOption == AlarmToneOption.CUSTOM_PICKER && !customUriStr.isNullOrBlank()) {
                    android.net.Uri.parse(customUriStr)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    setVolume(1.0f, 1.0f)
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                // Play ultra loud synthesized tone
                AlarmSoundManager.playSynthesizedTone(toneOption) { track ->
                    audioTrack = track
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmService", "Failed to play custom alarm, falling back: ${e.message}")
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer.create(applicationContext, fallbackUri)?.apply {
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarm() {
        _isAlarmRinging.value = false

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        releaseWakeLock()

        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
        nm?.cancel(TRACKING_NOTIFICATION_ID)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "FocusTimer:AlarmWakeLock"
            )?.apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Timer Alarm"
            val descriptionText = "Continuous loud alarm when focus study session ends"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
