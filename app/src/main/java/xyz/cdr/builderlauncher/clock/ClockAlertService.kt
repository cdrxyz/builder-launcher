package xyz.cdr.builderlauncher.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R
import xyz.cdr.builderlauncher.data.SettingsRepository

class ClockAlertService : Service() {
    private var track: AudioTrack? = null
    private var player: Thread? = null
    @Volatile private var playing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val alert = ClockStore(this).snapshot().alert
        if (alert == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notification(this, alert)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFY,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFY, notification)
        }
        startTone()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTone()
        super.onDestroy()
    }

    private fun startTone() {
        stopTone()
        val sound = SettingsRepository(this).settings.value.clockSound
        val pcm = ClockTone.pcm(sound)
        if (pcm.isEmpty()) return
        runCatching { playPcm(pcm) }
    }

    private fun playPcm(pcm: ShortArray) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(ClockTone.SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val min = AudioTrack.getMinBufferSize(
            ClockTone.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(pcm.size * 2)
        val next = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(min)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = next
        playing = true
        val startedAt = System.currentTimeMillis()
        next.play()
        player = Thread {
            val bytes = ShortArray(pcm.size)
            while (playing) {
                next.setVolume(Clock.fadeGain(System.currentTimeMillis() - startedAt))
                pcm.copyInto(bytes)
                var offset = 0
                while (playing && offset < bytes.size) {
                    val written = next.write(bytes, offset, bytes.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
        }.also { it.start() }
    }

    private fun stopTone() {
        playing = false
        player?.join(250)
        player = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    companion object {
        const val CHANNEL = "clock"
        const val NOTIFY = 7103
        const val ACTION_STOP = "xyz.cdr.builderlauncher.clock.STOP_ALERT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ClockAlertService::class.java),
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, ClockAlertService::class.java),
            )
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL, "Clock", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }

        fun notification(context: Context, alert: ClockAlert): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_ALERT, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = when (alert.kind) {
                ClockAlertKind.TIMER -> "Time is up"
                ClockAlertKind.ALARM -> alert.label.ifBlank { "Alarm" }
            }
            val body = when (alert.kind) {
                ClockAlertKind.TIMER -> Clock.formatTimer(alert.durationMs)
                ClockAlertKind.ALARM -> Clock.formatAlarm(alert.hour, alert.minute)
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setSound(null)
                .build()
        }

        const val EXTRA_ALERT = "clock_alert"
    }
}
