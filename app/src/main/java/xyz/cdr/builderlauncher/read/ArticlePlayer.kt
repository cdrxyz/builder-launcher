package xyz.cdr.builderlauncher.read

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.cdr.builderlauncher.podcasts.Podcasts

data class PreparedGrok(val uri: Uri, val headers: Map<String, String>, val complete: Boolean = true)

interface ArticleControls {
    fun toggle(article: ReadArticle, startMs: Long, speed: Float)
    fun pause()
    fun seek(article: ReadArticle, positionMs: Long, speed: Float)
    fun skip(article: ReadArticle, deltaMs: Long, speed: Float)
    fun setSpeed(article: ReadArticle?, speed: Float)
}

class ArticlePlayer(context: Context) : ArticleControls {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(ReadPlayback())
    val state: StateFlow<ReadPlayback> = _state.asStateFlow()
    var onCheckpoint: ((String, Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    private var textDuration = 0L
    private var pendingWordMs = 0L
    private val tick = object : Runnable {
        override fun run() {
            publish(save = false)
            if (player != null) main.postDelayed(this, Podcasts.POSITION_PUBLISH_MS)
        }
    }

    fun arm(article: ReadArticle, startMs: Long, speed: Float, playing: Boolean) {
        textDuration = Reads.durationMs(article.text)
        pendingWordMs = startMs.coerceIn(0L, textDuration)
        _state.value = ReadPlayback(
            article.id,
            pendingWordMs,
            textDuration,
            playing = playing,
            speed = Podcasts.snapSpeed(speed),
        )
    }

    fun play(article: ReadArticle, uri: Uri, headers: Map<String, String>, startMs: Long, speed: Float) {
        val rate = Podcasts.snapSpeed(speed)
        textDuration = Reads.durationMs(article.text)
        pendingWordMs = startMs.coerceIn(0L, textDuration)
        val exo = obtain() ?: return
        runCatching { exo.stop() }
        if (uri.scheme == "file" || uri.scheme == "content") {
            exo.setMediaItem(MediaItem.fromUri(uri))
        } else {
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            val source = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(app, http))
                .createMediaSource(MediaItem.fromUri(uri))
            exo.setMediaSource(source)
        }
        exo.prepare()
        exo.playWhenReady = true
        applySpeed(exo, rate)
        _state.value = ReadPlayback(article.id, pendingWordMs, textDuration, playing = true, speed = rate)
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun isAudible(): Boolean {
        val exo = player ?: return false
        return exo.playbackState == Player.STATE_READY && exo.playWhenReady
    }

    fun playGrowing(article: ReadArticle, file: File, startMs: Long, speed: Float, stillWriting: () -> Boolean) {
        val rate = Podcasts.snapSpeed(speed)
        textDuration = Reads.durationMs(article.text)
        pendingWordMs = startMs.coerceIn(0L, textDuration)
        val exo = obtain() ?: return
        runCatching { exo.stop() }
        val item = MediaItem.Builder().setUri(Uri.fromFile(file)).setMimeType(androidx.media3.common.MimeTypes.AUDIO_MPEG).build()
        val source = ProgressiveMediaSource.Factory(GrowingFileDataSource.Factory(file, stillWriting))
            .createMediaSource(item)
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
        applySpeed(exo, rate)
        _state.value = ReadPlayback(article.id, pendingWordMs, textDuration, playing = true, speed = rate)
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun appendGrowing(file: File) {
        val exo = player ?: return
        val pos = exo.currentPosition
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        if (pos > 0L) exo.seekTo(pos)
        exo.playWhenReady = true
    }

    override fun toggle(article: ReadArticle, startMs: Long, speed: Float) {
        val current = _state.value
        if (current.articleId == article.id && current.playing) {
            pause()
            return
        }
        val exo = player
        if (current.articleId == article.id && exo != null) {
            exo.playWhenReady = true
            _state.value = current.copy(playing = true, speed = Podcasts.snapSpeed(speed))
            return
        }
        arm(article, startMs, speed, playing = true)
    }

    fun resume() {
        val exo = player ?: return
        if (_state.value.articleId == null) return
        exo.playWhenReady = true
        runCatching { exo.play() }
        _state.value = _state.value.copy(playing = true)
    }

    fun seekSession(positionMs: Long) {
        val id = _state.value.articleId ?: return
        val duration = _state.value.durationMs
        val word = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        val exo = player
        if (exo != null) {
            val audioDur = audioDuration(exo)
            if (audioDur > 0L) exo.seekTo(Reads.audioMsFromWord(word, audioDur, duration))
        }
        _state.value = _state.value.copy(positionMs = word)
        onCheckpoint?.invoke(id, word)
    }

    fun skipSession(deltaMs: Long) {
        seekSession(Reads.skip(_state.value.positionMs, _state.value.durationMs, deltaMs))
    }

    override fun pause() {
        player?.playWhenReady = false
        runCatching { player?.pause() }
        val current = _state.value
        if (current.articleId != null) {
            publish(save = true)
            _state.value = _state.value.copy(playing = false)
        }
    }

    fun persist() {
        publish(save = true)
    }

    override fun seek(article: ReadArticle, positionMs: Long, speed: Float) {
        val duration = Reads.durationMs(article.text)
        val word = positionMs.coerceIn(0L, duration)
        pendingWordMs = word
        textDuration = duration
        val exo = player
        if (exo != null && _state.value.articleId == article.id) {
            val audioDur = audioDuration(exo)
            if (audioDur > 0L) exo.seekTo(Reads.audioMsFromWord(word, audioDur, duration))
        }
        _state.value = ReadPlayback(article.id, word, duration, playing = _state.value.playing, speed = Podcasts.snapSpeed(speed))
        onCheckpoint?.invoke(article.id, word)
    }

    override fun skip(article: ReadArticle, deltaMs: Long, speed: Float) {
        val base = if (_state.value.articleId == article.id) _state.value.positionMs else 0L
        seek(article, Reads.skip(base, Reads.durationMs(article.text), deltaMs), speed)
    }

    override fun setSpeed(article: ReadArticle?, speed: Float) {
        val rate = Podcasts.snapSpeed(speed)
        _state.value = _state.value.copy(speed = rate)
        player?.let { applySpeed(it, rate) }
        if (article != null && _state.value.playing && _state.value.articleId == article.id) return
    }

    fun release() {
        pause()
        main.removeCallbacks(tick)
        player?.release()
        player = null
    }

    private fun obtain(): ExoPlayer? {
        player?.let { return it }
        val created = ExoPlayer.Builder(app).build()
        created.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            true,
        )
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== created) return
                if (playbackState == Player.STATE_READY) {
                    val audioDur = audioDuration(created)
                    if (audioDur > 0L && pendingWordMs > 0L) {
                        created.seekTo(Reads.audioMsFromWord(pendingWordMs, audioDur, textDuration))
                        pendingWordMs = 0L
                    }
                    publish(save = false)
                }
                if (playbackState == Player.STATE_ENDED) {
                    val id = _state.value.articleId ?: return
                    _state.value = _state.value.copy(positionMs = textDuration, playing = false)
                    onCheckpoint?.invoke(id, textDuration)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player !== created) return
                publish(save = !isPlaying)
                _state.value = _state.value.copy(playing = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player === created) {
                    _state.value = _state.value.copy(playing = false)
                    val reason = error.message?.lineSequence()?.firstOrNull()?.take(80)?.ifBlank { null }
                        ?: "Could not play audio."
                    onError?.invoke(reason)
                }
            }
        })
        player = created
        return created
    }

    private fun publish(save: Boolean) {
        val exo = player ?: return
        val id = _state.value.articleId ?: return
        val audioDur = audioDuration(exo)
        val audioPos = runCatching { exo.currentPosition }.getOrDefault(0L)
        val word = Reads.wordMsFromAudio(audioPos, audioDur, textDuration)
        _state.value = _state.value.copy(positionMs = word, durationMs = textDuration)
        if (save) onCheckpoint?.invoke(id, word)
    }

    private fun audioDuration(exo: ExoPlayer): Long {
        val d = runCatching { exo.duration }.getOrDefault(C.TIME_UNSET)
        return if (d == C.TIME_UNSET || d < 0L) 0L else d
    }

    private fun applySpeed(exo: ExoPlayer, speed: Float) {
        runCatching { exo.playbackParameters = PlaybackParameters(speed) }
    }
}

class ChosenSpeaker(
    private val system: ReadSpeaker,
    private val grok: ArticlePlayer,
    private val useGrok: () -> Boolean,
    private val startGrok: (ReadArticle, Long, Float) -> Unit,
    private val cancelGrok: () -> Unit,
    private val hasLocal: (ReadArticle) -> Boolean = { false },
) : ArticleControls {
    override fun toggle(article: ReadArticle, startMs: Long, speed: Float) {
        if (Reads.shouldSpeakStored(hasLocal(article), useGrok())) {
            system.pause()
            val current = grok.state.value
            val same = current.articleId == article.id
            if (Reads.shouldPausePlayback(same && current.playing, grok.isAudible())) {
                cancelGrok()
                grok.pause()
                return
            }
            val start = if (same) current.positionMs else startMs
            startGrok(article, start, speed)
        } else {
            cancelGrok()
            grok.pause()
            system.toggle(article, startMs, speed)
        }
    }

    override fun pause() {
        cancelGrok()
        grok.pause()
        system.pause()
    }

    override fun seek(article: ReadArticle, positionMs: Long, speed: Float) {
        if (useGrok()) {
            system.pause()
            grok.seek(article, positionMs, speed)
        } else {
            grok.pause()
            system.seek(article, positionMs, speed)
        }
    }

    override fun skip(article: ReadArticle, deltaMs: Long, speed: Float) {
        if (useGrok()) {
            system.pause()
            grok.skip(article, deltaMs, speed)
        } else {
            grok.pause()
            system.skip(article, deltaMs, speed)
        }
    }

    override fun setSpeed(article: ReadArticle?, speed: Float) {
        if (useGrok()) grok.setSpeed(article, speed) else system.setSpeed(article, speed)
    }
}
