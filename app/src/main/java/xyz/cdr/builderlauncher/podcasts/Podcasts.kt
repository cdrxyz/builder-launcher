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
    ): List<PodcastHomeRow> {
        val showById = shows.associateBy { it.feedUrl }
        val continueRows = progress.values
            .filter { !finished(it) && it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { p ->
                val episode = episodes.find { it.id == p.episodeId } ?: return@mapNotNull null
                val show = showById[episode.showId] ?: return@mapNotNull null
                PodcastHomeRow.Continue(episode, show, p)
            }
            .take(CONTINUE)
        val continueIds = continueRows.map { it.episode.id }.toSet()
        val fresh = episodes
            .sortedByDescending { it.pubDate }
            .mapNotNull { episode ->
                if (episode.id in continueIds) return@mapNotNull null
                if (finished(progress[episode.id])) return@mapNotNull null
                val show = showById[episode.showId] ?: return@mapNotNull null
                PodcastHomeRow.Fresh(episode, show)
            }
            .take(NEW)
        val subs = shows.sortedBy { it.title.lowercase() }
            .map { PodcastHomeRow.Subscription(it) }
        return continueRows + fresh + subs
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
        return when {
            bytes >= gb && bytes % gb == 0L -> "${bytes / gb} GB"
            bytes >= gb -> "%.1f GB".format(bytes / gb.toDouble())
            bytes >= mb && bytes % mb == 0L -> "${bytes / mb} MB"
            else -> "$bytes B"
        }
    }

    fun cacheFileName(episodeId: String): String {
        val hex = episodeId.hashCode().toUInt().toString(16)
        return "$hex.bin"
    }
}

@Serializable
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val artworkUrl: String = "",
    val subscribedAt: Long = 0L,
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
)

data class PodcastCacheFile(
    val episodeId: String,
    val bytes: Long,
    val lastAccessAt: Long,
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
