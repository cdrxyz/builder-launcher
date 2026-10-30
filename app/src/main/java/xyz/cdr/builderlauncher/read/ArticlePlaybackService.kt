package xyz.cdr.builderlauncher.read

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.R
import xyz.cdr.builderlauncher.podcasts.PodcastPlaybackService
import xyz.cdr.builderlauncher.podcasts.Podcasts

class ArticlePlaybackService : Service() {
    private var session: MediaSession? = null
    private var scope: CoroutineScope? = null
    private var title = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val created = MediaSession(this, "saved-articles")
        created.setSessionActivity(PodcastPlaybackService.nowPlayingIntent(this))
        created.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    ArticleMedia.player?.resume()
                    publish()
                }

                override fun onPause() {
                    ArticleMedia.player?.pause()
                    publish()
                }

                override fun onStop() {
                    ArticleMedia.player?.pause()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    ArticleMedia.player?.seekSession(pos)
                    publish()
                }

                override fun onFastForward() = skip(forward = true)

                override fun onRewind() = skip(forward = false)

                override fun onSkipToNext() = skip(forward = true)

                override fun onSkipToPrevious() = skip(forward = false)
            },
        )
        created.isActive = true
        session = created
        val parent = SupervisorJob()
        val sc = CoroutineScope(Dispatchers.Main.immediate + parent)
        scope = sc
        sc.launch {
            ArticleMedia.player?.state?.collect { publish() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { ArticleMedia.title }
        startInForeground()
        publish()
        return START_STICKY
    }

    override fun onDestroy() {
        session?.isActive = false
        session?.release()
        session = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun publish() {
        val playback = ArticleMedia.player?.state?.value ?: return
        val playing = playback.playing
        val active = !playback.articleId.isNullOrBlank()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(Podcasts.mediaActions(playing))
                .setState(
                    when {
                        playing -> PlaybackState.STATE_PLAYING
                        active -> PlaybackState.STATE_PAUSED
                        else -> PlaybackState.STATE_STOPPED
                    },
                    playback.positionMs,
                    if (playing) playback.speed else 0f,
                )
                .build(),
        )
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { Reads.SAVED_ARTICLES })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, Reads.SAVED_ARTICLES)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, Reads.SAVED_ARTICLES)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, playback.durationMs)
                .build(),
        )
        if (!active) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        startInForeground()
    }

    private fun startInForeground() {
        val notification = notification(this, title, session, ArticleMedia.player?.state?.value?.playing == true)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFY, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFY, notification)
        }
    }

    private fun skip(forward: Boolean) {
        ArticleMedia.player?.skipSession(Podcasts.mediaSkipMs(forward))
        publish()
    }

    companion object {
        const val ACTION_STOP = "xyz.cdr.builderlauncher.read.STOP"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "saved-articles"
        private const val NOTIFY = 43

        fun start(context: Context, title: String) {
            val intent = Intent(context, ArticlePlaybackService::class.java).putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ArticlePlaybackService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun notification(context: Context, title: String, session: MediaSession?, playing: Boolean): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, Reads.SAVED_ARTICLES, NotificationManager.IMPORTANCE_LOW))
            }
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val style = Notification.MediaStyle()
            if (session != null) style.setMediaSession(session.sessionToken)
            return builder
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle(title.ifBlank { Reads.SAVED_ARTICLES })
                .setContentText(Reads.SAVED_ARTICLES)
                .setContentIntent(PodcastPlaybackService.nowPlayingIntent(context))
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(style)
                .build()
        }
    }
}
