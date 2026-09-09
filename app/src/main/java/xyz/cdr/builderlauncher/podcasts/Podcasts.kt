package xyz.cdr.builderlauncher.podcasts

import kotlinx.serialization.Serializable

object Podcasts {
    const val MORE = "… all podcasts >"
    const val BACK = "<"
    const val COMMAND = "podcasts"
    const val DEFAULT_CACHE_BYTES = 5L * 1024 * 1024 * 1024
    const val CONTINUE = 3
    const val NEW = 5
    const val MAX_SHOWS = 200
    const val FINISH_REMAINING_MS = 30_000L
    const val SKIP_MS = 15_000L
    const val BAR_SIDE_DP = 36
    const val SECTION_RECENT = "recent"
    const val SECTION_NEXT = "next 5 episodes"
    const val SECTION_SHOWS = "podcasts"
    const val ART_DP = 36
    const val TITLE_LINES = 3
    const val SHOW_LINES = 1
    const val DEFAULT_SPEED = 1.0f
    const val MEDIA_ACTION_STOP = 1L
    const val MEDIA_ACTION_PAUSE = 1L shl 1
    const val MEDIA_ACTION_PLAY = 1L shl 2
    const val MEDIA_ACTION_REWIND = 1L shl 3
    const val MEDIA_ACTION_FAST_FORWARD = 1L shl 6
    const val MEDIA_ACTION_SEEK = 1L shl 8
    const val MEDIA_ACTION_PLAY_PAUSE = 1L shl 9
    val SPEED_STEPS = listOf(0.8f, 1.0f, 1.1f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.5f, 3.0f)
    val CACHE_PRESETS = listOf(
        1L * 1024 * 1024 * 1024,
        5L * 1024 * 1024 * 1024,
        10L * 1024 * 1024 * 1024,
        20L * 1024 * 1024 * 1024,
    )

    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.length < 2) return false
        return COMMAND.startsWith(q) || "podcast".startsWith(q)
    }

    fun looksLikeFeedUrl(raw: String): Boolean {
        val t = raw.trim()
        if (t.contains(' ') || t.contains('\n')) return false
        return t.startsWith("https://", ignoreCase = true) || t.startsWith("http://", ignoreCase = true)
    }

    fun finished(progress: EpisodeProgress?): Boolean {
        if (progress == null) return false
        if (progress.finished) return true
        val duration = progress.durationMs
        if (duration <= 0L) return false
        val remaining = duration - progress.positionMs
        return remaining <= FINISH_REMAINING_MS || progress.positionMs * 100 >= duration * 95
    }

    fun homeRows(
        shows: List<PodcastShow>,
        episodes: List<PodcastEpisode>,
        progress: Map<String, EpisodeProgress>,
        currentEpisodeId: String? = null,
    ): List<PodcastHomeRow> {
        val showById = shows.associateBy { it.feedUrl }
        val currentId = currentEpisodeId?.takeIf { it.isNotBlank() }
        val continueRows = progress.values
            .filter { !finished(it) && !skipped(it) && it.lastPlayedAt > 0L && it.episodeId != currentId }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { p ->
                val episode = episodes.find { it.id == p.episodeId } ?: return@mapNotNull null
                val show = showById[episode.showId] ?: return@mapNotNull null
                PodcastHomeRow.Continue(episode, show, p)
            }
            .take(CONTINUE)
        val skipIds = continueRows.map { it.episode.id }.toSet() + setOfNotNull(currentId)
        val fresh = episodes
            .sortedByDescending { it.pubDate }
            .mapNotNull { episode ->
                if (episode.id in skipIds) return@mapNotNull null
                if (finished(progress[episode.id]) || skipped(progress[episode.id])) return@mapNotNull null
                val show = showById[episode.showId] ?: return@mapNotNull null
                PodcastHomeRow.Fresh(episode, show)
            }
            .take(NEW)
        val subs = shows.sortedBy { it.title.lowercase() }
            .map { PodcastHomeRow.Subscription(it) }
        val rows = mutableListOf<PodcastHomeRow>()
        if (continueRows.isNotEmpty()) {
            rows += PodcastHomeRow.Header(SECTION_RECENT)
            rows += continueRows
        }
        if (fresh.isNotEmpty()) {
            rows += PodcastHomeRow.Header(SECTION_NEXT)
            rows += fresh
        }
        if (subs.isNotEmpty()) {
            rows += PodcastHomeRow.Header(SECTION_SHOWS)
            rows += subs
        }
        return rows
    }

    fun filesToDelete(
        files: List<PodcastCacheFile>,
        capBytes: Long,
        keepIds: Set<String> = emptySet(),
    ): List<String> {
        if (capBytes <= 0L) return files.filter { it.episodeId !in keepIds }.map { it.episodeId }
        val total = files.sumOf { it.bytes }
        if (total <= capBytes) return emptyList()
        var used = total
        val drop = mutableListOf<String>()
        for (file in files.sortedBy { it.lastAccessAt }) {
            if (used <= capBytes) break
            if (file.episodeId in keepIds) continue
            drop += file.episodeId
            used -= file.bytes
        }
        return drop
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatPosition(positionMs: Long, durationMs: Long): String =
        "${formatDuration(positionMs)} of ${formatDuration(durationMs)}"

    fun parseDuration(raw: String): Long {
        val t = raw.trim()
        if (t.isEmpty()) return 0L
        if (t.contains(':')) {
            val parts = t.split(':')
            if (parts.any { it.toLongOrNull() == null }) return 0L
            val nums = parts.map { it.toLong() }
            val sec = when (nums.size) {
                3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
                2 -> nums[0] * 60 + nums[1]
                1 -> nums[0]
                else -> return 0L
            }
            return sec * 1000L
        }
        val sec = t.toLongOrNull() ?: return 0L
        return sec * 1000L
    }

    fun cacheLabel(bytes: Long): String {
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L
        val n = bytes.coerceAtLeast(0L)
        return when {
            n >= gb && n % gb == 0L -> "${n / gb} GB"
            n >= gb -> "%.1f GB".format(n / gb.toDouble())
            n >= mb && n % mb == 0L -> "${n / mb} MB"
            n >= mb -> "%.1f MB".format(n / mb.toDouble())
            else -> "0 MB"
        }
    }

    fun cacheFileName(episodeId: String): String {
        val hex = episodeId.hashCode().toUInt().toString(16)
        return "$hex.bin"
    }

    fun skip(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
        val cap = durationMs.coerceAtLeast(0L)
        return (positionMs + deltaMs).coerceIn(0L, cap)
    }

    fun progressAt(x: Float, width: Float, durationMs: Long): Long {
        if (durationMs <= 0L || width <= 0f) return 0L
        val t = (x / width).coerceIn(0f, 1f)
        return (t * durationMs).toLong()
    }

    fun fraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun speedAt(x: Float, width: Float): Float {
        val i = indexAt(x, width, SPEED_STEPS.size)
        return SPEED_STEPS[i]
    }

    fun snapSpeed(speed: Float): Float =
        SPEED_STEPS.minBy { kotlin.math.abs(it - speed) }

    fun formatSpeed(speed: Float): String {
        val s = snapSpeed(speed)
        val whole = s.toInt()
        return if (s == whole.toFloat()) "${whole}×" else "%.1f×".format(java.util.Locale.US, s)
    }

    const val POSITION_PUBLISH_MS = 1_000L

    fun shouldPublishPlayback(previous: PlaybackState, next: PlaybackState): Boolean {
        if (previous.episodeId != next.episodeId) return true
        if (previous.playing != next.playing) return true
        if (previous.speed != next.speed) return true
        if (previous.durationMs != next.durationMs) return true
        return previous.positionMs / POSITION_PUBLISH_MS != next.positionMs / POSITION_PUBLISH_MS
    }

    fun speedProgress(speed: Float): Float =
        fraction(
            SPEED_STEPS.indexOf(snapSpeed(speed)).coerceAtLeast(0).toLong(),
            SPEED_STEPS.lastIndex.toLong(),
        )

    fun formatEpisodeDate(
        pubDate: Long,
        locale: java.util.Locale = java.util.Locale.US,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    ): String {
        if (pubDate <= 0L) return ""
        val fmt = java.text.SimpleDateFormat("d MMM yyyy", locale)
        fmt.timeZone = timeZone
        return fmt.format(java.util.Date(pubDate))
    }

    fun skipped(progress: EpisodeProgress?): Boolean = progress?.skipped == true

    fun nowPlayingVisible(playing: Boolean, episodeId: String?): Boolean =
        playing && !episodeId.isNullOrBlank()

    fun nowPlayingBarVisible(episodeId: String?, finished: Boolean = false): Boolean =
        !episodeId.isNullOrBlank() && !finished

    fun playbackEnded(playing: Boolean, positionMs: Long, durationMs: Long): Boolean =
        !playing && finished(EpisodeProgress("", positionMs, durationMs))

    fun episodeLeftMeta(progress: EpisodeProgress?, durationMs: Long): String {
        if (skipped(progress)) {
            return if (durationMs > 0) formatDuration(durationMs) else ""
        }
        if (progress != null && !finished(progress) && progress.positionMs > 0L) {
            val dur = progress.durationMs.takeIf { it > 0L } ?: durationMs
            return if (dur > 0L) formatPosition(progress.positionMs, dur) else ""
        }
        val dur = progress?.durationMs?.takeIf { it > 0L } ?: durationMs
        return if (dur > 0L) formatDuration(dur) else ""
    }

    fun downloadPercent(received: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((received * 100) / total).toInt().coerceIn(0, 100)
    }

    fun downloadLabel(
        downloaded: Boolean,
        busy: Boolean,
        percent: Int,
        knownTotal: Boolean,
    ): String = when {
        downloaded && !busy -> "downloaded"
        busy && knownTotal -> "downloading $percent%"
        busy -> "downloading…"
        else -> "download"
    }

    fun playedDownloadsToDelete(
        downloads: Set<String>,
        progress: Map<String, EpisodeProgress>,
    ): List<String> = downloads.filter { finished(progress[it]) }

    fun mediaSessionActive(
        episodeId: String?,
        stopped: Boolean,
        playing: Boolean = true,
    ): Boolean {
        if (stopped || episodeId.isNullOrBlank()) return false
        return true
    }

    fun mediaActions(playing: Boolean): Long {
        val common = MEDIA_ACTION_PLAY_PAUSE or MEDIA_ACTION_STOP or MEDIA_ACTION_SEEK or
            MEDIA_ACTION_REWIND or MEDIA_ACTION_FAST_FORWARD
        return common or if (playing) MEDIA_ACTION_PAUSE else MEDIA_ACTION_PLAY
    }

    fun searchRowShowsArt(artworkUrl: String): Boolean = artworkUrl.trim().isNotEmpty()

    fun titleMaxLines(home: Boolean): Int = if (home) TITLE_LINES else Int.MAX_VALUE

    fun showMaxLines(nextEpisodes: Boolean): Int = if (nextEpisodes) SHOW_LINES else Int.MAX_VALUE

    fun pickNotes(encoded: String, summary: String, description: String): String {
        val raw = listOf(encoded, summary, description).firstOrNull { it.isNotBlank() }.orEmpty()
        return plainNotes(raw)
    }

    fun plainNotes(raw: String): String {
        if (raw.isBlank()) return ""
        var t = raw.replace("\r\n", "\n")
        t = BR.replace(t, "\n")
        t = BLOCK_END.replace(t, "\n\n")
        t = TAG.replace(t, "")
        t = t.replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        t = ENTITY.replace(t) { m ->
            val num = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            if (num in 1..0x10FFFF) String(Character.toChars(num)) else m.value
        }
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex(" *\\n *"), "\n")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        return t.trim()
    }

    fun parseTimestamp(raw: String): Long? {
        val t = raw.trim().trim('[', ']', '(', ')')
        if (!t.contains(':')) return null
        val parts = t.split(':')
        if (parts.size !in 2..3) return null
        if (parts.any { it.toLongOrNull() == null }) return null
        val ms = parseDuration(t)
        return ms.takeIf { it >= 0L }
    }

    fun timestamps(text: String): List<TimestampHit> {
        if (text.isBlank()) return emptyList()
        return CLOCK.findAll(text).mapNotNull { m ->
            val raw = m.value
            val ms = parseTimestamp(raw) ?: return@mapNotNull null
            TimestampHit(m.range.first, m.range.last + 1, ms, raw)
        }.toList()
    }

    fun timestampAt(text: String, index: Int): Long? =
        timestamps(text).firstOrNull { index in it.start until it.end }?.positionMs

    fun sortEpisodes(episodes: List<PodcastEpisode>, order: EpisodeOrder): List<PodcastEpisode> =
        when (order) {
            EpisodeOrder.NEWEST -> episodes.sortedByDescending { it.pubDate }
            EpisodeOrder.OLDEST -> episodes.sortedBy { it.pubDate }
        }

    fun episodeOrderLabel(order: EpisodeOrder): String = when (order) {
        EpisodeOrder.NEWEST -> "newest first"
        EpisodeOrder.OLDEST -> "oldest first"
    }

    fun parseEpisodeOrder(raw: String?): EpisodeOrder = when (raw?.trim()?.lowercase()) {
        "oldest", "oldest first", "ascending", "asc" -> EpisodeOrder.OLDEST
        else -> EpisodeOrder.NEWEST
    }

    fun mergeShow(existing: PodcastShow?, incoming: PodcastShow): PodcastShow {
        if (existing == null) return incoming
        return incoming.copy(
            subscribedAt = existing.subscribedAt.takeIf { it > 0L } ?: incoming.subscribedAt,
            artworkUrl = incoming.artworkUrl.ifBlank { existing.artworkUrl },
            episodeOrder = existing.episodeOrder,
        )
    }

    private fun indexAt(x: Float, width: Float, count: Int): Int {
        if (count <= 1 || width <= 0f) return 0
        val t = (x / width).coerceIn(0f, 1f)
        return kotlin.math.round(t * (count - 1)).toInt().coerceIn(0, count - 1)
    }

    private val BR = Regex("(?i)<br\\s*/?>")
    private val BLOCK_END = Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote)>")
    private val TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&#(\\d+);")
    private val CLOCK = Regex("(?<!\\d)(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?!\\d)")
}

@Serializable
enum class EpisodeOrder {
    NEWEST,
    OLDEST,
}

@Serializable
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val artworkUrl: String = "",
    val subscribedAt: Long = 0L,
    val episodeOrder: EpisodeOrder = EpisodeOrder.NEWEST,
)

@Serializable
data class PodcastEpisode(
    val id: String,
    val showId: String,
    val title: String,
    val pubDate: Long = 0L,
    val durationMs: Long = 0L,
    val enclosureUrl: String = "",
    val description: String = "",
)

@Serializable
data class EpisodeProgress(
    val episodeId: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val finished: Boolean = false,
    val skipped: Boolean = false,
)

data class PodcastCacheFile(
    val episodeId: String,
    val bytes: Long,
    val lastAccessAt: Long,
)

data class DownloadProgress(
    val episodeId: String? = null,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

data class TimestampHit(
    val start: Int,
    val end: Int,
    val positionMs: Long,
    val raw: String,
)

data class PodcastHit(
    val title: String,
    val author: String = "",
    val feedUrl: String,
    val artworkUrl: String = "",
)

data class PodcastFeed(
    val show: PodcastShow,
    val episodes: List<PodcastEpisode>,
)

sealed class PodcastHomeRow {
    data class Header(val title: String) : PodcastHomeRow()

    data class Continue(
        val episode: PodcastEpisode,
        val show: PodcastShow,
        val progress: EpisodeProgress,
    ) : PodcastHomeRow()

    data class Fresh(
        val episode: PodcastEpisode,
        val show: PodcastShow,
    ) : PodcastHomeRow()

    data class Subscription(
        val show: PodcastShow,
    ) : PodcastHomeRow()
}
