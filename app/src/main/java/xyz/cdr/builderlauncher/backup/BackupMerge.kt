package xyz.cdr.builderlauncher.backup

import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.clock.WorldClock
import xyz.cdr.builderlauncher.data.ChatThread
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.podcasts.EpisodeProgress
import xyz.cdr.builderlauncher.podcasts.PodcastEpisode
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import xyz.cdr.builderlauncher.stocks.WatchItem

object BackupMerge {
    fun join(local: BackupDocument, remote: BackupDocument?): BackupDocument {
        if (remote == null) return local
        return merge(local, remote)
    }

    fun merge(a: BackupDocument, b: BackupDocument): BackupDocument {
        val preferA = a.exportedAt >= b.exportedAt
        val newer = if (preferA) a else b
        val deleted = (a.deletedIds + b.deletedIds).distinct()
        val drop = deleted.toSet()
        return BackupDocument(
            version = maxOf(a.version, b.version),
            exportedAt = maxOf(a.exportedAt, b.exportedAt),
            items = mergeItems(a.items, b.items).filterNot { it.id in drop },
            deletedIds = deleted,
            chats = mergeChats(a.chats, b.chats),
            pins = (a.pins + b.pins).distinct(),
            watchlist = mergeWatch(a.watchlist, b.watchlist, preferA),
            podcasts = mergePods(a.podcasts, b.podcasts),
            alarms = mergeAlarms(a.alarms, b.alarms),
            zones = mergeZones(a.zones, b.zones),
            settings = newer.settings,
        )
    }

    private fun stamp(item: LocalItem): Long =
        maxOf(item.createdAt, item.updatedAt, item.completedAt ?: 0L)

    private fun mergeItems(a: List<LocalItem>, b: List<LocalItem>): List<LocalItem> {
        val map = LinkedHashMap<String, LocalItem>()
        (a + b).forEach { item ->
            val prev = map[item.id]
            map[item.id] = if (prev == null || stamp(item) >= stamp(prev)) item else prev
        }
        return map.values.toList()
    }

    private fun mergeChats(a: List<ChatThread>, b: List<ChatThread>): List<ChatThread> {
        val map = LinkedHashMap<String, ChatThread>()
        (a + b).forEach { item ->
            val prev = map[item.id]
            map[item.id] = if (prev == null || item.updatedAt >= prev.updatedAt) item else prev
        }
        return map.values.toList()
    }

    private fun mergeWatch(a: List<WatchItem>, b: List<WatchItem>, preferA: Boolean): List<WatchItem> {
        val first = if (preferA) a else b
        val second = if (preferA) b else a
        val seen = HashSet<String>()
        val out = ArrayList<WatchItem>()
        (first + second).forEach { item ->
            val symbol = item.symbol.uppercase()
            if (seen.add(symbol)) out += item
        }
        return out
    }

    private fun mergePods(a: PodcastBackup, b: PodcastBackup): PodcastBackup {
        val shows = LinkedHashMap<String, PodcastShow>()
        (a.shows + b.shows).forEach { show -> shows[show.feedUrl] = show }
        val episodes = LinkedHashMap<String, PodcastEpisode>()
        (a.episodes + b.episodes).forEach { ep ->
            val prev = episodes[ep.id]
            episodes[ep.id] = if (prev == null || ep.pubDate >= prev.pubDate) ep else prev
        }
        val progress = LinkedHashMap<String, EpisodeProgress>()
        (a.progress + b.progress).forEach { row ->
            val prev = progress[row.episodeId]
            progress[row.episodeId] = if (prev == null || row.lastPlayedAt >= prev.lastPlayedAt) row else prev
        }
        return PodcastBackup(
            shows = shows.values.toList(),
            episodes = episodes.values.toList(),
            progress = progress.values.toList(),
            cacheBytes = maxOf(a.cacheBytes, b.cacheBytes),
        )
    }

    private fun mergeAlarms(a: List<ClockAlarm>, b: List<ClockAlarm>): List<ClockAlarm> {
        val map = LinkedHashMap<String, ClockAlarm>()
        (a + b).forEach { item -> map[item.id] = item }
        return map.values.toList()
    }

    private fun mergeZones(a: List<WorldClock>, b: List<WorldClock>): List<WorldClock> {
        val map = LinkedHashMap<String, WorldClock>()
        (a + b).forEach { item -> map[item.id] = item }
        return map.values.toList()
    }
}
