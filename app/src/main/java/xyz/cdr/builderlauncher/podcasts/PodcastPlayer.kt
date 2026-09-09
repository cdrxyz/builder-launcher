package xyz.cdr.builderlauncher.podcasts

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(
    val episodeId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
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
            _state.value = PlaybackState()
            return
        }
        mp.setOnPreparedListener {
            val dur = it.duration.toLong().coerceAtLeast(episode.durationMs)
            val start = startMs.coerceIn(0L, (dur - 1_000L).coerceAtLeast(0L))
            if (start > 0L) it.seekTo(start.toInt())
            it.start()
            _state.value = PlaybackState(id, true, it.currentPosition.toLong(), dur)
        }
        mp.setOnCompletionListener {
            val dur = it.duration.toLong().coerceAtLeast(1L)
            _state.value = PlaybackState(id, false, dur, dur)
            onProgress?.invoke(id, dur, dur, true)
        }
        mp.setOnErrorListener { _, _, _ ->
            _state.value = _state.value.copy(playing = false)
            true
        }
        player = mp
        _state.value = PlaybackState(
            episodeId = id,
            playing = false,
            positionMs = startMs,
            durationMs = episode.durationMs,
        )
        mp.prepareAsync()
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        snapshot(playing = false, save = true)
    }

    fun resume() {
        val mp = player ?: return
        if (!mp.isPlaying) mp.start()
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
        mp.seekTo(positionMs.toInt().coerceAtLeast(0))
        snapshot(playing = mp.isPlaying, save = true)
    }

    fun poll() {
        val mp = player ?: return
        snapshot(playing = mp.isPlaying, save = false)
    }

    fun persist() {
        snapshot(playing = player?.isPlaying == true, save = true)
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
        _state.value = PlaybackState(id, playing, pos, dur)
        if (save) onProgress?.invoke(id, pos, dur, false)
    }

    private fun stopInternal(save: Boolean) {
        if (save) runCatching { snapshot(playing = false, save = true) }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        _state.value = PlaybackState()
    }
}
