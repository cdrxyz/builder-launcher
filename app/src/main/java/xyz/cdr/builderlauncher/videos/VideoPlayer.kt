package xyz.cdr.builderlauncher.videos

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoPlayback(
    val videoId: String? = null,
    val title: String = "",
    val playing: Boolean = false,
    val audioOnly: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class VideoPlayer(context: Context) {
    private val app = context.applicationContext
    private var player: ExoPlayer? = null
    private var surface: SurfaceView? = null
    private val _state = MutableStateFlow(VideoPlayback())
    val state: StateFlow<VideoPlayback> = _state.asStateFlow()

    fun attach(surface: SurfaceView?) {
        this.surface = surface
        player?.setVideoSurfaceView(if (_state.value.audioOnly) null else surface)
    }

    fun play(video: SavedVideo, picked: PickedVideo, audioOnly: Boolean, startMs: Long = 0L) {
        val exo = obtain() ?: return
        val source = source(picked, audioOnly) ?: return
        exo.setMediaSource(source)
        exo.prepare()
        exo.seekTo(startMs.coerceAtLeast(0L))
        exo.playWhenReady = true
        exo.play()
        if (!audioOnly) exo.setVideoSurfaceView(surface)
        else exo.clearVideoSurface()
        _state.value = VideoPlayback(video.id, video.title, playing = true, audioOnly = audioOnly, positionMs = startMs, durationMs = video.durationMs)
    }

    fun pause() {
        player?.pause()
        val current = _state.value
        if (current.videoId != null) _state.value = current.copy(playing = false, positionMs = player?.currentPosition ?: current.positionMs)
    }

    fun resume() {
        val current = _state.value
        if (current.videoId == null) return
        player?.play()
        _state.value = current.copy(playing = true)
    }

    fun toggle() {
        if (_state.value.playing) pause() else resume()
    }

    fun stop() {
        player?.stop()
        _state.value = VideoPlayback()
    }

    private fun source(picked: PickedVideo, audioOnly: Boolean) = when {
        audioOnly -> item(picked.audioUrl)
        picked.muxed -> item(picked.videoUrl)
        else -> {
            val picture = item(picked.videoUrl)
            val audio = item(picked.audioUrl)
            if (picture != null && audio != null) MergingMediaSource(picture, audio) else picture ?: audio
        }
    }

    private fun item(url: String): ProgressiveMediaSource? {
        if (url.isBlank()) return null
        val factory = DefaultHttpDataSource.Factory().setUserAgent(Videos.PLAYER_UA)
        return ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
    }

    private fun obtain(): ExoPlayer? {
        player?.let { return it }
        return runCatching {
            ExoPlayer.Builder(app)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                .build()
                .also { created ->
                    created.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            val current = _state.value
                            if (current.videoId != null) _state.value = current.copy(playing = isPlaying, positionMs = created.currentPosition)
                        }
                    })
                    player = created
                }
        }.getOrNull()
    }
}

object VideoMedia {
    var player: VideoPlayer? = null
    var videos: VideosRepository? = null

    fun bind(player: VideoPlayer) {
        this.player = player
    }

    fun stop(context: android.content.Context) {
        player?.stop()
        VideoPlaybackService.stop(context)
    }
}
