package xyz.cdr.builderlauncher.podcasts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.backup.PodcastBackup
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class PodcastStore(
    val shows: List<PodcastShow> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val progress: List<EpisodeProgress> = emptyList(),
    val cacheBytes: Long = Podcasts.DEFAULT_CACHE_BYTES,
    val playbackSpeed: Float = Podcasts.DEFAULT_SPEED,
    val skipSilence: Boolean = Podcasts.DEFAULT_SKIP_SILENCE,
    val downloads: List<PodcastDownload> = emptyList(),
)

@Serializable
data class PodcastDownload(
    val episodeId: String,
    val bytes: Long = 0L,
    val lastAccessAt: Long = 0L,
)

class PodcastsRepository(
    context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "podcasts.json")
    private val cacheDir = File(appContext.filesDir, "podcasts")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _shows = MutableStateFlow<List<PodcastShow>>(emptyList())
    val shows: StateFlow<List<PodcastShow>> = _shows.asStateFlow()
    private val _episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val episodes: StateFlow<List<PodcastEpisode>> = _episodes.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())
    val progress: StateFlow<Map<String, EpisodeProgress>> = _progress.asStateFlow()
    private val _downloads = MutableStateFlow<Map<String, PodcastDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, PodcastDownload>> = _downloads.asStateFlow()
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()
    private val _cacheBytes = MutableStateFlow(Podcasts.DEFAULT_CACHE_BYTES)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(Podcasts.DEFAULT_SPEED)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val _skipSilence = MutableStateFlow(Podcasts.DEFAULT_SKIP_SILENCE)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    init {
        val stored = load()
        _shows.value = stored.shows
        _episodes.value = stored.episodes
        _progress.value = stored.progress.associateBy { it.episodeId }
        _downloads.value = stored.downloads.associateBy { it.episodeId }
        _cacheBytes.value = stored.cacheBytes.takeIf { it > 0L } ?: Podcasts.DEFAULT_CACHE_BYTES
        _playbackSpeed.value = Podcasts.snapSpeed(stored.playbackSpeed)
        _skipSilence.value = stored.skipSilence
        cacheDir.mkdirs()
    }

    fun homeRows(): List<PodcastHomeRow> = Podcasts.homeRows(_shows.value, _episodes.value, _progress.value)

    fun exportBackup(): PodcastBackup =
        PodcastBackup(
            shows = _shows.value,
            episodes = _episodes.value,
            progress = _progress.value.values.toList(),
            cacheBytes = _cacheBytes.value,
        )

    fun importBackup(backup: PodcastBackup) {
        _shows.value = backup.shows
        _episodes.value = backup.episodes
        _progress.value = backup.progress.associateBy { it.episodeId }
        if (backup.cacheBytes > 0L) _cacheBytes.value = backup.cacheBytes
        persist()
    }

    fun show(feedUrl: String): PodcastShow? =
        _shows.value.find { it.feedUrl.equals(feedUrl, ignoreCase = true) }

    fun episodesFor(feedUrl: String): List<PodcastEpisode> {
        val order = show(feedUrl)?.episodeOrder ?: EpisodeOrder.NEWEST
        return Podcasts.sortEpisodes(
            _episodes.value.filter { it.showId.equals(feedUrl, ignoreCase = true) },
            order,
        )
    }

    fun episode(id: String): PodcastEpisode? = _episodes.value.find { it.id == id }

    fun downloadedFile(episodeId: String): File? {
        val rec = _downloads.value[episodeId] ?: return null
        val f = File(cacheDir, Podcasts.cacheFileName(episodeId))
        return f.takeIf { it.exists() && it.length() > 0L }?.also {
            touchDownload(episodeId, rec.bytes)
        }
    }

    fun setCacheBytes(bytes: Long) {
        _cacheBytes.value = bytes.coerceAtLeast(1L * 1024 * 1024)
        persist()
        evict()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = Podcasts.snapSpeed(speed)
        persist()
    }

    fun setSkipSilence(on: Boolean) {
        _skipSilence.value = on
        persist()
    }

    fun setEpisodeOrder(feedUrl: String, order: EpisodeOrder) {
        _shows.value = _shows.value.map {
            if (it.feedUrl.equals(feedUrl, ignoreCase = true)) it.copy(episodeOrder = order) else it
        }
        persist()
    }

    suspend fun search(query: String): List<PodcastHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = PodcastSearch.HOST.toHttpUrl().newBuilder()
            .addQueryParameter("term", q)
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "8")
            .build()
        runCatching {
            http.newCall(request(url.toString())).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                PodcastSearch.parse(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun subscribe(feedUrl: String, title: String = "", author: String = "", artworkUrl: String = ""): PodcastShow? {
        val url = feedUrl.trim()
        if (!Podcasts.looksLikeFeedUrl(url)) return null
        val existing = show(url)
        if (existing != null) {
            refreshShow(url)
            return show(url)
        }
        if (_shows.value.size >= Podcasts.MAX_SHOWS) return null
        val placeholder = PodcastShow(
            feedUrl = url,
            title = title.ifBlank { url },
            author = author,
            artworkUrl = artworkUrl,
            subscribedAt = System.currentTimeMillis(),
        )
        _shows.value = _shows.value + placeholder
        persist()
        refreshShow(url)
        return show(url) ?: placeholder
    }

    fun unsubscribe(feedUrl: String) {
        val ids = _episodes.value.filter { it.showId.equals(feedUrl, ignoreCase = true) }.map { it.id }.toSet()
        ids.forEach { deleteDownload(it) }
        _shows.value = _shows.value.filterNot { it.feedUrl.equals(feedUrl, ignoreCase = true) }
        _episodes.value = _episodes.value.filterNot { it.showId.equals(feedUrl, ignoreCase = true) }
        _progress.value = _progress.value.filterKeys { it !in ids }
        persist()
    }

    suspend fun importHits(hits: List<PodcastHit>): Int {
        var added = 0
        for (hit in hits) {
            if (_shows.value.size >= Podcasts.MAX_SHOWS) break
            if (show(hit.feedUrl) != null) continue
            if (subscribe(hit.feedUrl, hit.title, hit.author, hit.artworkUrl) != null) added++
        }
        return added
    }

    suspend fun refreshAll() {
        val urls = _shows.value.map { it.feedUrl }
        if (urls.isEmpty()) return
        urls.chunked(4).forEach { batch ->
            coroutineScope {
                batch.map { url -> async { refreshShow(url) } }.awaitAll()
            }
        }
    }

    suspend fun refreshShow(feedUrl: String) = withContext(Dispatchers.IO) {
        val xml = fetchText(feedUrl) ?: return@withContext
        val feed = PodcastRss.parse(xml, feedUrl) ?: return@withContext
        val mergedShow = Podcasts.mergeShow(show(feedUrl), feed.show)
        _shows.value = _shows.value.map {
            if (it.feedUrl.equals(feedUrl, ignoreCase = true)) mergedShow else it
        }
        val keepOther = _episodes.value.filterNot { it.showId.equals(feedUrl, ignoreCase = true) }
        val byId = _episodes.value.associateBy { it.id }
        val incoming = feed.episodes.map { ep ->
            val old = byId[ep.id]
            if (old == null) ep
            else ep.copy(
                durationMs = if (ep.durationMs > 0) ep.durationMs else old.durationMs,
                description = ep.description.ifBlank { old.description },
            )
        }
        _episodes.value = keepOther + incoming
        persist()
    }

    fun saveProgress(episodeId: String, positionMs: Long, durationMs: Long, finished: Boolean = false) {
        val now = System.currentTimeMillis()
        val prev = _progress.value[episodeId]
        val dur = durationMs.takeIf { it > 0L } ?: prev?.durationMs ?: 0L
        val pos = positionMs.coerceAtLeast(0L)
        val done = finished || Podcasts.finished(
            EpisodeProgress(episodeId, pos, dur, now, finished),
        )
        _progress.value = _progress.value + (episodeId to EpisodeProgress(
            episodeId = episodeId,
            positionMs = if (done) dur else pos,
            durationMs = dur,
            lastPlayedAt = now,
            finished = done,
            skipped = if (done) false else (prev?.skipped == true),
        ))
        persist()
        if (done) {
            Podcasts.playedDownloadsToDelete(_downloads.value.keys, _progress.value)
                .forEach { deleteDownload(it) }
        }
    }

    fun setSkipped(episodeId: String, skipped: Boolean, durationMs: Long = 0L) {
        val prev = _progress.value[episodeId]
        val dur = durationMs.takeIf { it > 0L } ?: prev?.durationMs ?: 0L
        _progress.value = _progress.value + (episodeId to EpisodeProgress(
            episodeId = episodeId,
            positionMs = prev?.positionMs ?: 0L,
            durationMs = dur,
            lastPlayedAt = prev?.lastPlayedAt ?: 0L,
            finished = prev?.finished == true,
            skipped = skipped,
        ))
        persist()
    }

    suspend fun download(episode: PodcastEpisode): File? = withContext(Dispatchers.IO) {
        val url = episode.enclosureUrl
        if (url.isBlank()) return@withContext null
        cacheDir.mkdirs()
        val dest = File(cacheDir, Podcasts.cacheFileName(episode.id))
        if (dest.exists() && dest.length() > 0L) {
            touchDownload(episode.id, dest.length())
            return@withContext dest
        }
        val tmp = File(cacheDir, dest.name + ".part")
        _downloadProgress.value = DownloadProgress(episode.id, 0L, 0L)
        val ok = runCatching {
            http.newCall(request(url)).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                val total = body.contentLength()
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            _downloadProgress.value = DownloadProgress(episode.id, received, total)
                        }
                    }
                }
                true
            }
        }.getOrDefault(false)
        if (!ok) {
            tmp.delete()
            _downloadProgress.value = DownloadProgress()
            return@withContext null
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        touchDownload(episode.id, dest.length())
        evict(keepIds = setOf(episode.id))
        _downloadProgress.value = DownloadProgress()
        dest.takeIf { it.exists() }
    }

    fun deleteDownload(episodeId: String) {
        File(cacheDir, Podcasts.cacheFileName(episodeId)).delete()
        _downloads.value = _downloads.value - episodeId
        persist()
    }

    fun cacheUsedBytes(): Long = _downloads.value.values.sumOf { it.bytes }

    private fun touchDownload(episodeId: String, bytes: Long) {
        _downloads.value = _downloads.value + (episodeId to PodcastDownload(
            episodeId = episodeId,
            bytes = bytes,
            lastAccessAt = System.currentTimeMillis(),
        ))
        persist()
    }

    private fun evict(keepIds: Set<String> = emptySet()) {
        val files = _downloads.value.values.map { rec ->
            val onDisk = File(cacheDir, Podcasts.cacheFileName(rec.episodeId))
            val bytes = if (onDisk.exists()) onDisk.length() else rec.bytes
            PodcastCacheFile(rec.episodeId, bytes, rec.lastAccessAt)
        }
        val drop = Podcasts.filesToDelete(files, _cacheBytes.value, keepIds)
        drop.forEach { deleteDownload(it) }
        cacheDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part")) f.delete()
        }
    }

    private fun fetchText(url: String): String? = runCatching {
        http.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrNull()

    private fun request(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", "BuilderLauncher/1.0")
            .header("Accept", "*/*")
            .get()
            .build()

    private fun persist() {
        val stored = PodcastStore(
            shows = _shows.value,
            episodes = _episodes.value,
            progress = _progress.value.values.toList(),
            cacheBytes = _cacheBytes.value,
            playbackSpeed = _playbackSpeed.value,
            skipSilence = _skipSilence.value,
            downloads = _downloads.value.values.toList(),
        )
        file.writeText(json.encodeToString(stored))
    }

    private fun load(): PodcastStore {
        if (!file.exists()) return PodcastStore()
        return runCatching {
            json.decodeFromString<PodcastStore>(file.readText())
        }.getOrDefault(PodcastStore())
    }
}
