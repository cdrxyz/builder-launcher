package xyz.cdr.builderlauncher.podcasts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R

class PodcastPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                PodcastPlayer.pause()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                PodcastPlayer.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Podcast" }
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val notification = notification(this, title, body)
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
        return START_STICKY
    }

    override fun onDestroy() {
        PodcastPlayer.persist()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "xyz.cdr.builderlauncher.podcasts.PAUSE"
        const val ACTION_STOP = "xyz.cdr.builderlauncher.podcasts.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        private const val CHANNEL = "podcasts"
        private const val NOTIFY = 42

        fun start(context: Context, title: String, body: String) {
            val intent = Intent(context, PodcastPlaybackService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java).setAction(ACTION_PAUSE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun notification(context: Context, title: String, body: String): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Podcasts", NotificationManager.IMPORTANCE_LOW),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val pause = PendingIntent.getService(
                context,
                1,
                Intent(context, PodcastPlaybackService::class.java).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(0, "Pause", pause)
                .build()
        }
    }
}
