package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

enum class AlarmToneOption(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String
) {
    ULTRA_LOUD_SIREN("ultra_siren", "Ultra Loud Siren", "🚨", "High-intensity dual frequency alarm (Maximum volume)"),
    DIGITAL_BEEP("digital_beep", "Digital Clock Beep", "⏱️", "Classic rapid electronic alarm beep"),
    RADAR_ALERT("radar_alert", "Radar Fast Pulse", "⚡", "Urgent pulsing alert chime"),
    CLASSIC_BELL("classic_bell", "Vibrant Bell Chime", "🔔", "Clean resonant bell chime"),
    SYSTEM_DEFAULT("system_default", "Phone Default Alarm", "📱", "Your device's default alarm sound"),
    CUSTOM_PICKER("custom_picker", "Custom Phone Ringtone", "🎵", "Select any audio from phone settings");

    companion object {
        fun fromId(id: String): AlarmToneOption {
            return entries.find { it.id == id } ?: ULTRA_LOUD_SIREN
        }
    }
}

object AlarmSoundManager {
    private const val TAG = "AlarmSoundManager"
    private var previewMediaPlayer: MediaPlayer? = null
    private var previewAudioTrack: AudioTrack? = null
    private var previewJob: Job? = null

    /**
     * Maximize the device's alarm stream volume to ensure the alarm is loud.
     */
    fun ensureMaxAlarmVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            // Ensure volume is at least 95% of max volume
            if (currentVol < maxVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting max alarm volume: ${e.message}")
        }
    }

    /**
     * Play preview for 3.5 seconds.
     */
    fun playPreview(
        context: Context,
        toneOption: AlarmToneOption,
        customUriStr: String? = null,
        onFinished: () -> Unit = {}
    ) {
        stopPreview()
        ensureMaxAlarmVolume(context)

        previewJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (toneOption == AlarmToneOption.SYSTEM_DEFAULT || (toneOption == AlarmToneOption.CUSTOM_PICKER && !customUriStr.isNullOrBlank())) {
                    playUriSound(context, toneOption, customUriStr, isLooping = true) { player ->
                        previewMediaPlayer = player
                    }
                } else {
                    playSynthesizedTone(toneOption) { track ->
                        previewAudioTrack = track
                    }
                }
                delay(3500L)
            } catch (e: Exception) {
                Log.e(TAG, "Preview error: ${e.message}")
            } finally {
                stopPreview()
                launch(Dispatchers.Main) { onFinished() }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        } catch (_: Exception) {}

        try {
            previewAudioTrack?.stop()
            previewAudioTrack?.release()
            previewAudioTrack = null
        } catch (_: Exception) {}
    }

    private fun playUriSound(
        context: Context,
        toneOption: AlarmToneOption,
        customUriStr: String?,
        isLooping: Boolean,
        onPlayerCreated: (MediaPlayer) -> Unit
    ) {
        val uri = if (toneOption == AlarmToneOption.CUSTOM_PICKER && !customUriStr.isNullOrBlank()) {
            Uri.parse(customUriStr)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        val player = MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            setVolume(1.0f, 1.0f)
            this.isLooping = isLooping
            prepare()
            start()
        }
        onPlayerCreated(player)
    }

    /**
     * Synthesize high-amplitude PCM audio for maximum volume and responsiveness.
     */
    fun generateTonePcm(toneOption: AlarmToneOption, sampleRate: Int = 44100): ShortArray {
        val durationSeconds = when (toneOption) {
            AlarmToneOption.ULTRA_LOUD_SIREN -> 1.6
            AlarmToneOption.DIGITAL_BEEP -> 1.0
            AlarmToneOption.RADAR_ALERT -> 1.2
            AlarmToneOption.CLASSIC_BELL -> 1.5
            else -> 1.2
        }

        val totalSamples = (sampleRate * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        val maxAmplitude = 32767.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sampleValue = when (toneOption) {
                AlarmToneOption.ULTRA_LOUD_SIREN -> {
                    // Warbling alternating high-intensity sirens (880Hz to 1760Hz)
                    val freq = 880.0 + 880.0 * (0.5 + 0.5 * sin(2 * Math.PI * 4.0 * t))
                    val sine = sin(2 * Math.PI * freq * t)
                    // Add harsh square wave harmonic for extreme loudness
                    val harmonic = if (sine > 0) 1.0 else -1.0
                    (sine * 0.7 + harmonic * 0.3) * maxAmplitude
                }

                AlarmToneOption.DIGITAL_BEEP -> {
                    // 4 short burst beeps at 1400Hz followed by pause
                    val cycleTime = t % 0.25
                    if (cycleTime < 0.12) {
                        val sine = sin(2 * Math.PI * 1400.0 * t)
                        sine * maxAmplitude
                    } else {
                        0.0
                    }
                }

                AlarmToneOption.RADAR_ALERT -> {
                    // Rapid ascending chirp (600Hz -> 1800Hz) repeated 3 times
                    val chirpTime = t % 0.4
                    val freq = 600.0 + (1200.0 * (chirpTime / 0.4))
                    val env = (1.0 - (chirpTime / 0.4)).coerceIn(0.0, 1.0)
                    sin(2 * Math.PI * freq * t) * env * maxAmplitude
                }

                AlarmToneOption.CLASSIC_BELL -> {
                    // Rich resonant bell chord with decay (440Hz + 880Hz + 1320Hz)
                    val cycleTime = t % 0.75
                    val env = kotlin.math.exp(-3.5 * cycleTime)
                    val tone = (sin(2 * Math.PI * 523.25 * t) +
                            0.6 * sin(2 * Math.PI * 1046.5 * t) +
                            0.4 * sin(2 * Math.PI * 1567.98 * t)) / 2.0
                    tone * env * maxAmplitude
                }

                else -> {
                    sin(2 * Math.PI * 1000.0 * t) * maxAmplitude
                }
            }
            buffer[i] = sampleValue.toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    /**
     * Play synthesized PCM tone continuously using AudioTrack.
     */
    fun playSynthesizedTone(
        toneOption: AlarmToneOption,
        onTrackReady: (AudioTrack) -> Unit
    ) {
        val sampleRate = 44100
        val pcmData = generateTonePcm(toneOption, sampleRate)
        val bufferSize = pcmData.size * 2

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_ALARM,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
            )
        }

        track.write(pcmData, 0, pcmData.size)
        track.setLoopPoints(0, pcmData.size, -1) // Infinite loop
        track.setVolume(1.0f)
        track.play()
        onTrackReady(track)
    }
}
