package xyz.cdr.builderlauncher.read

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.cdr.builderlauncher.podcasts.Podcasts
import java.util.Locale

data class ReadPlayback(
    val articleId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val speed: Float = 1f,
)

class ReadSpeaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<() -> Unit>()
    private val _state = MutableStateFlow(ReadPlayback())
    val state: StateFlow<ReadPlayback> = _state.asStateFlow()
    var onCheckpoint: ((String, Long) -> Unit)? = null
    private var chunks: List<ReadChunk> = emptyList()
    private var body: String = ""
    private var lastSavedMs = -1L

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val index = indexOf(utteranceId) ?: return
            val chunk = chunks.getOrNull(index) ?: return
            main.post { publish(chunk.startMs, playing = true) }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val index = indexOf(utteranceId) ?: return
            val chunk = chunks.getOrNull(index) ?: return
            val slice = body.substring(chunk.start, chunk.end)
            val cut = start.coerceIn(0, slice.length)
            val before = Reads.wordCount(slice.substring(0, cut))
            val words = Reads.wordCount(slice).coerceAtLeast(1)
            val span = (chunk.endMs - chunk.startMs).coerceAtLeast(1L)
            val pos = chunk.startMs + span * before / words
            main.post { publish(pos, playing = true) }
        }

        override fun onDone(utteranceId: String?) {
            val index = indexOf(utteranceId) ?: return
            val chunk = chunks.getOrNull(index) ?: return
            main.post {
                if (index == chunks.lastIndex) {
                    publish(chunk.endMs, playing = false, force = true)
                }
            }
        }

        override fun onError(utteranceId: String?) {
            main.post { publish(_state.value.positionMs, playing = false, force = true) }
        }
    }

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            engine?.setOnUtteranceProgressListener(listener)
            engine?.language = Locale.getDefault()
            val queued = pending.toList()
            pending.clear()
            if (ready) queued.forEach { main.post(it) }
        }
    }

    fun toggle(article: ReadArticle, startMs: Long, speed: Float) {
        val current = _state.value
        if (current.articleId == article.id && current.playing) {
            pause()
            return
        }
        play(article, if (current.articleId == article.id) current.positionMs else startMs, speed)
    }

    fun play(article: ReadArticle, startMs: Long, speed: Float) {
        runWhenReady {
            val rate = Podcasts.snapSpeed(speed)
            chunks = Reads.chunks(article.text)
            body = article.text
            val duration = Reads.durationMs(article.text)
            val start = startMs.coerceIn(0L, duration)
            _state.value = ReadPlayback(article.id, start, duration, playing = true, speed = rate)
            lastSavedMs = -1L
            val tts = engine ?: return@runWhenReady
            tts.setSpeechRate(rate)
            tts.stop()
            if (chunks.isEmpty()) {
                publish(0L, playing = false, force = true)
                return@runWhenReady
            }
            val from = Reads.chunkIndexAt(chunks, start)
            chunks.drop(from).forEachIndexed { offset, chunk ->
                val queue = if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(body.substring(chunk.start, chunk.end), queue, Bundle(), "r${from + offset}")
            }
        }
    }

    fun pause() {
        engine?.stop()
        val current = _state.value
        if (current.articleId != null) publish(current.positionMs, playing = false, force = true)
    }

    fun seek(article: ReadArticle, positionMs: Long, speed: Float) {
        val playing = _state.value.playing && _state.value.articleId == article.id
        val duration = Reads.durationMs(article.text)
        val next = positionMs.coerceIn(0L, duration)
        if (playing) play(article, next, speed) else {
            _state.value = ReadPlayback(article.id, next, duration, playing = false, speed = Podcasts.snapSpeed(speed))
            checkpoint(article.id, next)
        }
    }

    fun skip(article: ReadArticle, deltaMs: Long, speed: Float) {
        val base = if (_state.value.articleId == article.id) _state.value.positionMs else 0L
        seek(article, Reads.skip(base, Reads.durationMs(article.text), deltaMs), speed)
    }

    fun setSpeed(article: ReadArticle?, speed: Float) {
        val rate = Podcasts.snapSpeed(speed)
        _state.value = _state.value.copy(speed = rate)
        val current = article ?: return
        if (_state.value.playing && _state.value.articleId == current.id) {
            play(current, _state.value.positionMs, rate)
        }
    }

    fun shutdown() {
        pause()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun runWhenReady(block: () -> Unit) {
        if (ready) block() else pending += block
    }

    private fun publish(positionMs: Long, playing: Boolean, force: Boolean = false) {
        val id = _state.value.articleId ?: return
        _state.value = _state.value.copy(positionMs = positionMs, playing = playing)
        if (force || lastSavedMs < 0L || kotlin.math.abs(positionMs - lastSavedMs) >= 5_000L) {
            lastSavedMs = positionMs
            checkpoint(id, positionMs)
        }
    }

    private fun checkpoint(id: String, positionMs: Long) {
        onCheckpoint?.invoke(id, positionMs)
    }

    private fun indexOf(utteranceId: String?): Int? =
        utteranceId?.removePrefix("r")?.toIntOrNull()
}
