package xyz.cdr.builderlauncher.podcasts

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
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
)

object PodcastPlayer {
    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    var onProgress: ((String, Long, Long, Boolean) -> Unit)? = null

    fun play(episode: PodcastEpisode, source: File?, startMs: Long = 0L) {
        val id = episode.id
        val current = _state.value
        if (current.episodeId == id && player != null) {
            if (!current.playing) resume()
            return
        }
        stopInternal(save = true)
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        val ok = runCatching {
            if (source != null && source.exists()) {
                mp.setDataSource(source.absolutePath)
            } else {
                mp.setDataSource(episode.enclosureUrl)
            }
            true
        }.getOrDefault(false)
        if (!ok) {
            mp.release()
            _state.value = PlaybackState(speed = _state.value.speed)
            return
        }
        mp.setOnPreparedListener {
            val dur = runCatching { it.duration.toLong() }.getOrDefault(episode.durationMs)
                .coerceAtLeast(episode.durationMs)
            val start = startMs.coerceIn(0L, (dur - 1_000L).coerceAtLeast(0L))
            if (start > 0L) runCatching { it.seekTo(start.toInt()) }
            runCatching { it.start() }
            applySpeed(it, _state.value.speed, wantPlaying = true)
            val pos = runCatching { it.currentPosition.toLong() }.getOrDefault(start)
            _state.value = PlaybackState(id, true, pos, dur, _state.value.speed)
        }
        mp.setOnCompletionListener {
            val dur = runCatching { it.duration.toLong() }.getOrDefault(_state.value.durationMs)
                .coerceAtLeast(1L)
            _state.value = PlaybackState(id, false, dur, dur, _state.value.speed)
            onProgress?.invoke(id, dur, dur, true)
        }
        mp.setOnErrorListener { dead, _, _ ->
            abandon(dead)
            true
        }
        player = mp
        _state.value = PlaybackState(
            episodeId = id,
            playing = false,
            positionMs = startMs,
            durationMs = episode.durationMs,
            speed = _state.value.speed,
        )
        mp.prepareAsync()
    }

    fun pause() {
        val mp = player ?: return
        runCatching { if (mp.isPlaying) mp.pause() }
        snapshot(playing = false, save = true)
    }

    fun resume() {
        val mp = player ?: return
        runCatching { if (!mp.isPlaying) mp.start() }
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
        val mp = player ?: return
        val cap = runCatching { mp.duration.toLong() }.getOrDefault(_state.value.durationMs)
        runCatching { mp.seekTo(positionMs.coerceIn(0L, cap.coerceAtLeast(0L)).toInt()) }
        snapshot(playing = playing(mp), save = true)
    }

    fun skip(deltaMs: Long) {
        seek(Podcasts.skip(_state.value.positionMs, _state.value.durationMs, deltaMs))
    }

    fun setSpeed(speed: Float) {
        val snapped = Podcasts.snapSpeed(speed)
        _state.value = _state.value.copy(speed = snapped)
        val mp = player ?: return
        applySpeed(mp, snapped, wantPlaying = playing(mp) || _state.value.playing)
    }

    fun poll() {
        val mp = player ?: return
        snapshot(playing = playing(mp), save = false)
    }

    fun persist() {
        val mp = player
        snapshot(playing = mp != null && playing(mp), save = true)
    }

    fun stop() {
        stopInternal(save = true)
    }

    private fun snapshot(playing: Boolean, save: Boolean) {
        val mp = player ?: return
        val id = _state.value.episodeId ?: return
        val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.positionMs)
        val dur = runCatching { mp.duration.toLong() }.getOrDefault(_state.value.durationMs)
            .coerceAtLeast(_state.value.durationMs)
        val next = PlaybackState(id, playing, pos, dur, _state.value.speed)
        if (!save && !Podcasts.shouldPublishPlayback(_state.value, next)) return
        _state.value = next
        if (save) onProgress?.invoke(id, pos, dur, false)
    }

    private fun stopInternal(save: Boolean) {
        if (save) runCatching { snapshot(playing = false, save = true) }
        val mp = player
        player = null
        runCatching { mp?.reset() }
        runCatching { mp?.release() }
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    private fun abandon(mp: MediaPlayer) {
        if (player === mp) player = null
        runCatching { mp.reset() }
        runCatching { mp.release() }
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    private fun playing(mp: MediaPlayer): Boolean =
        runCatching { mp.isPlaying }.getOrDefault(false)

    private fun applySpeed(mp: MediaPlayer, speed: Float, wantPlaying: Boolean) {
        if (wantPlaying) runCatching { if (mp.isPlaying) mp.pause() }
        val applied = runCatching {
            mp.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1.0f)
            true
        }.getOrDefault(false)
        if (!applied && speed != 1.0f) {
            runCatching { mp.playbackParams = PlaybackParams().setSpeed(1.0f).setPitch(1.0f) }
            _state.value = _state.value.copy(speed = 1.0f)
        }
        if (wantPlaying) runCatching { mp.start() }
    }
}
