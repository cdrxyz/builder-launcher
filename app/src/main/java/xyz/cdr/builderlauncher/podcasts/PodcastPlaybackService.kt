package xyz.cdr.builderlauncher.podcasts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.MainActivity
import xyz.cdr.builderlauncher.R

class PodcastPlaybackService : Service() {
    private var session: MediaSession? = null
    private var job: Job? = null
    private var scope: CoroutineScope? = null
    private var showTitle = "Podcast"
    private var episodeTitle = ""
    private var artworkUrl = ""
    private var artwork: Bitmap? = null
    private var ready = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val created = MediaSession(this, "podcasts")
        created.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    PodcastPlayer.resume()
                    publish()
                }

                override fun onPause() {
                    PodcastPlayer.pause()
                    publish()
                }

                override fun onStop() {
                    PodcastPlayer.stop()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    PodcastPlayer.seek(pos)
                    publish()
                }

                override fun onFastForward() {
                    PodcastPlayer.skip(Podcasts.SKIP_MS)
                    publish()
                }

                override fun onRewind() {
                    PodcastPlayer.skip(-Podcasts.SKIP_MS)
                    publish()
                }
            },
        )
        created.isActive = true
        session = created
        val parent = SupervisorJob()
        job = parent
        val sc = CoroutineScope(Dispatchers.Main.immediate + parent)
        scope = sc
        sc.launch {
            PodcastPlayer.state.collect { publish() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                ready = true
                PodcastPlayer.pause()
                publish()
                return START_STICKY
            }
            ACTION_PLAY -> {
                ready = true
                PodcastPlayer.resume()
                publish()
                return START_STICKY
            }
            ACTION_STOP -> {
                ready = false
                PodcastPlayer.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        ready = true
        showTitle = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Podcast" }
        episodeTitle = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        artworkUrl = intent?.getStringExtra(EXTRA_ART).orEmpty()
        if (artworkUrl.isNotBlank()) {
            scope?.launch(Dispatchers.IO) {
                artwork = PodcastArtwork.get(artworkUrl)
                launch(Dispatchers.Main) { publish() }
            }
        }
        startInForeground()
        publish()
        return START_STICKY
    }

    override fun onDestroy() {
        PodcastPlayer.persist()
        session?.isActive = false
        session?.release()
        session = null
        scope?.cancel()
        scope = null
        job = null
        super.onDestroy()
    }

    private fun publish() {
        if (!ready) return
        val playback = PodcastPlayer.state.value
        val active = Podcasts.mediaSessionActive(playback.episodeId, stopped = playback.episodeId == null)
        val playing = playback.playing
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(Podcasts.mediaActions(playing))
                .setState(
                    when {
                        playing -> PlaybackState.STATE_PLAYING
                        playback.episodeId != null -> PlaybackState.STATE_PAUSED
                        else -> PlaybackState.STATE_STOPPED
                    },
                    playback.positionMs,
                    if (playing) playback.speed else 0f,
                )
                .build(),
        )
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, episodeTitle.ifBlank { "Podcast" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, showTitle)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, showTitle)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, playback.durationMs)
                .apply {
                    artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
                }
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
        val notification = notification(this, showTitle, episodeTitle, session, artwork, PodcastPlayer.state.value.playing)
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
    }

    companion object {
        const val ACTION_PAUSE = "xyz.cdr.builderlauncher.podcasts.PAUSE"
        const val ACTION_PLAY = "xyz.cdr.builderlauncher.podcasts.PLAY"
        const val ACTION_STOP = "xyz.cdr.builderlauncher.podcasts.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_ART = "art"
        private const val CHANNEL = "podcasts"
        private const val NOTIFY = 42

        fun start(context: Context, title: String, body: String, artworkUrl: String = "") {
            val intent = Intent(context, PodcastPlaybackService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_ART, artworkUrl)
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

        private fun notification(
            context: Context,
            show: String,
            episode: String,
            session: MediaSession?,
            art: Bitmap?,
            playing: Boolean,
        ): Notification {
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
            val toggle = PendingIntent.getService(
                context,
                1,
                Intent(context, PodcastPlaybackService::class.java)
                    .setAction(if (playing) ACTION_PAUSE else ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val style = Notification.MediaStyle()
            if (session != null) style.setMediaSession(session.sessionToken)
            style.setShowActionsInCompactView(0)
            return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(episode.ifBlank { "Podcast" })
                .setContentText(show)
                .setLargeIcon(art)
                .setContentIntent(open)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(style)
                .addAction(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Play",
                    toggle,
                )
                .build()
        }
    }
}
