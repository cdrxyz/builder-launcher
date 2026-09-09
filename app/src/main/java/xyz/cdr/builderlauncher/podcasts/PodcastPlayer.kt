package xyz.cdr.builderlauncher.podcasts

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(
    val episodeId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val skipSilence: Boolean = Podcasts.DEFAULT_SKIP_SILENCE,
)

object PodcastPlayer {
    private var app: Context? = null
    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    var onProgress: ((String, Long, Long, Boolean) -> Unit)? = null

    fun attach(context: Context) {
        app = context.applicationContext
    }

    fun play(episode: PodcastEpisode, source: File?, startMs: Long = 0L) {
        val id = episode.id
        val current = _state.value
        if (current.episodeId == id && player != null) {
            if (!current.playing) resume()
            return
        }
        stopInternal(save = true, release = false)
        val exo = obtain() ?: return
        val uri = if (source != null && source.exists()) {
            Uri.fromFile(source)
        } else {
            Uri.parse(episode.enclosureUrl)
        }
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        val start = startMs.coerceAtLeast(0L)
        if (start > 0L) runCatching { exo.seekTo(start) }
        applySpeed(exo, _state.value.speed)
        exo.skipSilenceEnabled = _state.value.skipSilence
        exo.playWhenReady = true
        _state.value = PlaybackState(
            episodeId = id,
            playing = false,
            positionMs = start,
            durationMs = episode.durationMs,
            speed = _state.value.speed,
            skipSilence = _state.value.skipSilence,
        )
    }

    fun pause() {
        val exo = player ?: return
        runCatching { exo.pause() }
        snapshot(playing = false, save = true)
    }

    fun resume() {
        val exo = player ?: return
        runCatching { exo.play() }
        snapshot(playing = true, save = false)
    }

    fun toggle(episode: PodcastEpisode, source: File?, startMs: Long = 0L) {
        val current = _state.value
        if (current.episodeId == episode.id && player != null) {
            if (current.playing) pause() else resume()
        } else {
            play(episode, source, startMs)
        }
    }

    fun seek(positionMs: Long) {
        val exo = player ?: return
        val cap = durationOf(exo)
        runCatching { exo.seekTo(positionMs.coerceIn(0L, cap.coerceAtLeast(0L))) }
        snapshot(playing = playing(exo), save = true)
    }

    fun skip(deltaMs: Long) {
        seek(Podcasts.skip(_state.value.positionMs, _state.value.durationMs, deltaMs))
    }

    fun setSpeed(speed: Float) {
        val snapped = Podcasts.snapSpeed(speed)
        _state.value = _state.value.copy(speed = snapped)
        val exo = player ?: return
        applySpeed(exo, snapped)
    }

    fun setSkipSilence(enabled: Boolean) {
        _state.value = _state.value.copy(skipSilence = enabled)
        player?.skipSilenceEnabled = enabled
    }

    fun poll() {
        val exo = player ?: return
        snapshot(playing = playing(exo), save = false)
    }

    fun persist() {
        val exo = player
        snapshot(playing = exo != null && playing(exo), save = true)
    }

    fun stop() {
        stopInternal(save = true, release = true)
    }

    private fun snapshot(playing: Boolean, save: Boolean) {
        val exo = player ?: return
        val id = _state.value.episodeId ?: return
        val pos = runCatching { exo.currentPosition }.getOrDefault(_state.value.positionMs).coerceAtLeast(0L)
        val dur = durationOf(exo).coerceAtLeast(_state.value.durationMs)
        val next = PlaybackState(id, playing, pos, dur, _state.value.speed, _state.value.skipSilence)
        if (!save && !Podcasts.shouldPublishPlayback(_state.value, next)) return
        _state.value = next
        if (save) onProgress?.invoke(id, pos, dur, false)
    }

    private fun stopInternal(save: Boolean, release: Boolean) {
        if (save) runCatching { snapshot(playing = false, save = true) }
        val exo = player
        if (release) {
            player = null
            runCatching { exo?.release() }
        } else {
            runCatching { exo?.stop() }
            runCatching { exo?.clearMediaItems() }
        }
        _state.value = PlaybackState(speed = _state.value.speed, skipSilence = _state.value.skipSilence)
    }

    private fun abandon(exo: ExoPlayer) {
        if (player === exo) player = null
        runCatching { exo.release() }
        _state.value = PlaybackState(speed = _state.value.speed, skipSilence = _state.value.skipSilence)
    }

    private fun playing(exo: ExoPlayer): Boolean =
        runCatching { exo.isPlaying }.getOrDefault(false)

    private fun durationOf(exo: ExoPlayer): Long {
        val d = runCatching { exo.duration }.getOrDefault(C.TIME_UNSET)
        return if (d == C.TIME_UNSET || d < 0L) _state.value.durationMs else d
    }

    private fun applySpeed(exo: ExoPlayer, speed: Float) {
        val applied = runCatching {
            exo.playbackParameters = PlaybackParameters(speed)
            true
        }.getOrDefault(false)
        if (!applied && speed != 1.0f) {
            runCatching { exo.playbackParameters = PlaybackParameters(1.0f) }
            _state.value = _state.value.copy(speed = 1.0f)
        }
    }

    private fun obtain(): ExoPlayer? {
        player?.let { return it }
        val ctx = app ?: return null
        val created = ExoPlayer.Builder(ctx).build()
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        created.skipSilenceEnabled = _state.value.skipSilence
        created.playbackParameters = PlaybackParameters(_state.value.speed)
        created.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player !== created) return
                    if (playbackState == Player.STATE_ENDED) {
                        val id = _state.value.episodeId ?: return
                        val dur = durationOf(created).coerceAtLeast(1L)
                        _state.value = PlaybackState(
                            id,
                            false,
                            dur,
                            dur,
                            _state.value.speed,
                            _state.value.skipSilence,
                        )
                        onProgress?.invoke(id, dur, dur, true)
                    } else {
                        snapshot(playing = playing(created), save = false)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player !== created) return
                    snapshot(playing = isPlaying, save = !isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player === created) abandon(created)
                }
            },
        )
        player = created
        return created
    }
}
