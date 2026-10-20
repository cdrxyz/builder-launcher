package xyz.cdr.builderlauncher.read

import kotlinx.serialization.Serializable
import xyz.cdr.builderlauncher.podcasts.Podcasts
import java.net.URI

@Serializable
data class ReadArticle(
    val id: String,
    val url: String,
    val title: String,
    val publication: String,
    val text: String,
    val addedAt: Long = 0L,
)

@Serializable
data class ReadProgress(
    val articleId: String,
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ReadBackup(
    val articles: List<ReadArticle> = emptyList(),
    val progress: List<ReadProgress> = emptyList(),
    val deletedIds: List<String> = emptyList(),
)

data class ReadChunk(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

object Reads {
    const val BACK = "<"
    const val TITLE = "articles"
    const val OPEN = "open in browser"
    const val REMOVE = "remove"
    const val EMPTY = "Paste a URL."
    const val READING = "Reading…"
    const val FAILED = "Could not read that page."
    const val GROK_VOICE = "leo"
    const val SPEECH_START_BYTES = 16_000
    const val NOT_A_URL = "Paste a URL."
    const val MAX_ARTICLES = 100
    const val MAX_TEXT = 40_000
    const val WPM = 160
    const val SKIP_MS = 15_000L
    private const val FNV_OFFSET = 0xcbf29ce484222325UL
    private const val FNV_PRIME = 0x100000001b3UL
    private val PRIVATE = Regex(
        """^(localhost|127\.0\.0\.1|0\.0\.0\.0|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+|169\.254\.\d+\.\d+|.*\.local|metadata\.google\.internal)$""",
        RegexOption.IGNORE_CASE,
    )

    fun fnv1a64(text: String): String {
        var hash = FNV_OFFSET
        for (b in text.encodeToByteArray()) {
            hash = hash xor b.toUByte().toULong()
            hash *= FNV_PRIME
        }
        return hash.toString(16).padStart(16, '0')
    }

    fun speechHash(text: String): String = fnv1a64("60000\n$text")

    fun publicUri(raw: String): URI? {
        var text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) {
            if (!text.contains('.') || text.startsWith("/") || text.startsWith(".")) return null
            text = "https://$text"
        }
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        val host = uri.host?.lowercase() ?: return null
        if (':' in host || PRIVATE.matches(host)) return null
        return uri
    }

    fun fetchUrl(raw: String): String? = assemble(raw, stripSlash = false)

    fun nextFetchUrl(current: String, location: String): String? {
        val base = runCatching { URI(current) }.getOrNull() ?: return null
        val resolved = runCatching { base.resolve(location) }.getOrNull()?.toString() ?: return null
        return fetchUrl(resolved)
    }

    fun normalizeUrl(raw: String): String? = assemble(raw, stripSlash = true)

    private fun assemble(raw: String, stripSlash: Boolean): String? {
        val uri = publicUri(raw) ?: return null
        val scheme = uri.scheme.lowercase()
        val host = uri.host.lowercase()
        var path = uri.rawPath ?: ""
        if (stripSlash && path.length > 1 && path.endsWith("/")) path = path.dropLast(1)
        val port = uri.port
        val portPart = when {
            port <= 0 -> ""
            scheme == "https" && port == 443 -> ""
            scheme == "http" && port == 80 -> ""
            else -> ":$port"
        }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$host$portPart$path$query"
    }

    fun articleId(raw: String): String? {
        val key = normalizeUrl(raw) ?: return null
        return "r" + fnv1a64(key)
    }

    fun publicationFromUrl(raw: String): String {
        val host = publicUri(raw)?.host?.lowercase()?.removePrefix("www.") ?: return ""
        return host
    }

    fun indexFromInput(raw: String, count: Int): Int? {
        val q = raw.trim()
        if (q.isEmpty() || q.any { !it.isDigit() }) return null
        val n = q.toIntOrNull() ?: return null
        if (n < 1 || n > count) return null
        return n - 1
    }

    fun wordCount(text: String): Int =
        text.split(Regex("""\s+""")).count { word -> word.any { it.isLetterOrDigit() } }

    fun durationMs(text: String): Long {
        val words = wordCount(text)
        if (words <= 0) return 0L
        return words * 60_000L / WPM
    }

    fun positionLabel(positionMs: Long, durationMs: Long): String {
        if (durationMs <= 0L) return ""
        return Podcasts.formatPosition(positionMs.coerceIn(0L, durationMs), durationMs)
    }

    fun usesGrokVoice(providerIsXai: Boolean, bearer: String?): Boolean =
        providerIsXai && !bearer.isNullOrBlank()

    fun storedAudioReady(indexes: List<Int>, index: Int = 0): Boolean = index in indexes

    fun storedAudioQuery(articleId: String, voice: String = GROK_VOICE, hash: String, index: Int? = null): String {
        val base = "/api/articles/audio?id=$articleId&voice=$voice&hash=$hash"
        return if (index == null) base else "$base&index=$index"
    }

    fun wordMsFromAudio(audioPos: Long, audioDur: Long, textDur: Long): Long {
        if (audioDur <= 0L || textDur <= 0L) return audioPos.coerceAtLeast(0L)
        return (textDur * audioPos.coerceAtLeast(0L) / audioDur).coerceIn(0L, textDur)
    }

    fun audioMsFromWord(wordPos: Long, audioDur: Long, textDur: Long): Long {
        if (audioDur <= 0L || textDur <= 0L) return wordPos.coerceAtLeast(0L)
        return (audioDur * wordPos.coerceIn(0L, textDur) / textDur).coerceIn(0L, audioDur)
    }

    fun speechSocketUrl(voice: String = GROK_VOICE): String =
        "wss://api.x.ai/v1/tts?language=en&voice=$voice&codec=mp3&sample_rate=24000&bit_rate=64000&speed=1.0&text_normalization=true&optimize_streaming_latency=1"

    fun speechTextFrames(text: String): List<String> {
        val frames = mutableListOf<String>()
        var start = 0
        val source = text
        if (source.isEmpty()) frames += """{"type":"text.delta","delta":""}"""
        while (start < source.length) {
            val end = minOf(start + 4_000, source.length)
            val delta = jsonEscape(source.substring(start, end))
            frames += """{"type":"text.delta","delta":"$delta"}"""
            start = end
        }
        frames += """{"type":"text.done"}"""
        return frames
    }

    fun speechEvent(raw: String): Pair<String, String> {
        val type = jsonField(raw, "type")
        val payload = when (type) {
            "audio.delta" -> jsonField(raw, "delta")
            "error" -> jsonField(raw, "message")
            else -> ""
        }
        return type to payload
    }

    fun shouldStartSpeech(bytes: Int): Boolean = bytes >= SPEECH_START_BYTES

    private fun jsonField(raw: String, name: String): String {
        val key = "\"$name\""
        val at = raw.indexOf(key)
        if (at < 0) return ""
        val colon = raw.indexOf(':', at + key.length)
        if (colon < 0) return ""
        var i = colon + 1
        while (i < raw.length && raw[i].isWhitespace()) i += 1
        if (i >= raw.length || raw[i] != '"') return ""
        val out = StringBuilder()
        i += 1
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                out.append(raw[i + 1])
                i += 2
                continue
            }
            if (ch == '"') break
            out.append(ch)
            i += 1
        }
        return out.toString()
    }

    private fun jsonEscape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    fun audioProgressLabel(status: String, done: Int = 0, total: Int = 0, error: String = ""): String {
        val capped = if (total > 0) done.coerceIn(0, total) else done.coerceAtLeast(0)
        return when (status) {
            "failed" -> {
                val reason = error.replace(Regex("\\s+"), " ").trim().let { if (it.length > 80) it.take(77) + "…" else it }
                if (reason.isEmpty()) "audio failed" else "audio failed · $reason"
            }
            "ready" -> "audio ready"
            "preparing" -> if (total <= 1) "preparing audio" else "preparing audio $capped of $total"
            "partial" -> if (total > 0 && capped in 1 until total) "audio $capped of $total" else ""
            else -> ""
        }
    }

    fun sorted(articles: List<ReadArticle>): List<ReadArticle> =
        articles.sortedByDescending { it.addedAt }

    fun add(
        articles: List<ReadArticle>,
        article: ReadArticle,
        now: Long,
    ): Pair<List<ReadArticle>, List<String>> {
        val prev = articles.find { it.id == article.id }
        val merged = if (prev == null) {
            article.copy(addedAt = now)
        } else {
            article.copy(
                addedAt = now,
                title = article.title.ifBlank { prev.title },
                publication = article.publication.ifBlank { prev.publication },
                text = article.text.ifBlank { prev.text },
            )
        }
        val next = sorted(listOf(merged) + articles.filterNot { it.id == merged.id })
        if (next.size <= MAX_ARTICLES) return next to emptyList()
        val kept = next.take(MAX_ARTICLES)
        return kept to next.drop(MAX_ARTICLES).map { it.id }
    }

    fun remove(articles: List<ReadArticle>, id: String): List<ReadArticle> =
        articles.filterNot { it.id == id }

    fun chunks(text: String): List<ReadChunk> {
        val spans = sentenceSpans(text)
        if (spans.isEmpty()) return emptyList()
        val words = spans.map { wordCount(text.substring(it.first, it.second)).coerceAtLeast(1) }
        val total = words.sum().coerceAtLeast(1)
        val dur = durationMs(text).coerceAtLeast(1L)
        var cursor = 0L
        return spans.mapIndexed { index, span ->
            val slice = if (index == spans.lastIndex) dur - cursor else dur * words[index] / total
            val chunk = ReadChunk(span.first, span.second, cursor, cursor + slice)
            cursor += slice
            chunk
        }
    }

    fun chunkIndexAt(chunks: List<ReadChunk>, positionMs: Long): Int {
        if (chunks.isEmpty()) return 0
        val pos = positionMs.coerceAtLeast(0L)
        val hit = chunks.indexOfFirst { pos < it.endMs }
        return if (hit < 0) chunks.lastIndex else hit
    }

    fun skip(positionMs: Long, durationMs: Long, deltaMs: Long): Long =
        Podcasts.skip(positionMs, durationMs, deltaMs)

    fun fraction(positionMs: Long, durationMs: Long): Float =
        Podcasts.fraction(positionMs, durationMs)

    fun progressAt(fraction: Float, durationMs: Long): Long =
        Podcasts.progressAt(fraction, 1f, durationMs)

    fun deletedIdsAfterAdd(deletedIds: List<String>, articleId: String, dropped: List<String> = emptyList()): List<String> =
        (deletedIds + dropped).distinct().filter { it != articleId }

    fun restoredIds(backup: ReadBackup): Set<String> {
        val deleted = backup.deletedIds.toSet()
        return backup.articles.map { it.id }.filter { it.isNotBlank() && it !in deleted }.toSet()
    }

    fun mergeBackup(a: ReadBackup, b: ReadBackup, restoredIds: Set<String> = emptySet()): ReadBackup {
        val deleted = (a.deletedIds + b.deletedIds).distinct().filter { it !in restoredIds }
        val drop = deleted.toSet()
        val articles = LinkedHashMap<String, ReadArticle>()
        (a.articles + b.articles).forEach { item ->
            if (item.id in drop) return@forEach
            articles[item.id] = mergeArticle(articles[item.id], item)
        }
        val progress = LinkedHashMap<String, ReadProgress>()
        (a.progress + b.progress).forEach { row ->
            if (row.articleId in drop || row.articleId !in articles) return@forEach
            progress[row.articleId] = mergeProgress(progress[row.articleId], row)
        }
        return ReadBackup(
            articles = sorted(articles.values.toList()),
            progress = progress.values.toList(),
            deletedIds = deleted,
        )
    }

    private fun mergeArticle(prev: ReadArticle?, item: ReadArticle): ReadArticle {
        if (prev == null) return item
        val text = if (item.text.length >= prev.text.length) item.text else prev.text
        val newer = if (item.addedAt >= prev.addedAt) item else prev
        val older = if (newer === item) prev else item
        return newer.copy(
            title = newer.title.ifBlank { older.title },
            publication = newer.publication.ifBlank { older.publication },
            text = text.ifBlank { older.text },
        )
    }

    private fun mergeProgress(prev: ReadProgress?, row: ReadProgress): ReadProgress {
        if (prev == null) return row
        return when {
            row.updatedAt > prev.updatedAt -> row
            row.updatedAt < prev.updatedAt -> prev
            row.positionMs >= prev.positionMs -> row
            else -> prev
        }
    }

    private fun sentenceSpans(text: String): List<Pair<Int, Int>> {
        if (text.isBlank()) return emptyList()
        val spans = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val boundary = c == '\n' ||
                ((c == '.' || c == '!' || c == '?') && (i + 1 >= text.length || text[i + 1].isWhitespace()))
            if (boundary) {
                val end = if (c == '\n') i else i + 1
                if (text.substring(start, end).any { it.isLetterOrDigit() }) spans += start to end
                var next = end
                while (next < text.length && text[next].isWhitespace()) next++
                start = next
                i = next
                continue
            }
            i++
        }
        if (start < text.length && text.substring(start).any { it.isLetterOrDigit() }) {
            spans += start to text.length
        }
        return spans.flatMap { splitLong(text, it.first, it.second) }
    }

    private fun splitLong(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        if (end - start <= 240) return listOf(start to end)
        val window = text.substring(start, end)
        val cut = window.lastIndexOf(' ', 240).takeIf { it > 40 } ?: 240
        val mid = start + cut
        return splitLong(text, start, mid) + splitLong(text, mid, end)
    }
}
