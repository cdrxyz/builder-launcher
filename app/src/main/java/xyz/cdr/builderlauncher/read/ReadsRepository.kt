package xyz.cdr.builderlauncher.read

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.net.HttpClients
import xyz.cdr.builderlauncher.podcasts.Podcasts
import java.io.File

@Serializable
private data class ReadStore(
    val articles: List<ReadArticle> = emptyList(),
    val progress: List<ReadProgress> = emptyList(),
    val deletedIds: List<String> = emptyList(),
    val speed: Float = 1f,
)

class ReadsRepository(
    context: Context,
    private val http: OkHttpClient = HttpClients.derived(connectSec = 15, readSec = 20),
) {
    private val file = File(context.applicationContext.filesDir, "reads.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _articles = MutableStateFlow<List<ReadArticle>>(emptyList())
    val articles: StateFlow<List<ReadArticle>> = _articles.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, ReadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ReadProgress>> = _progress.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private var deletedIds: List<String> = emptyList()

    init {
        val stored = load()
        _articles.value = Reads.sorted(stored.articles)
        _progress.value = stored.progress.associateBy { it.articleId }
        deletedIds = stored.deletedIds
        _speed.value = Podcasts.snapSpeed(stored.speed)
    }

    fun exportBackup(): ReadBackup = ReadBackup(
        articles = _articles.value,
        progress = _progress.value.values.toList(),
        deletedIds = deletedIds,
    )

    fun importBackup(backup: ReadBackup) {
        _articles.value = Reads.sorted(backup.articles)
        _progress.value = backup.progress.associateBy { it.articleId }
        deletedIds = backup.deletedIds
        persist()
    }

    fun remember(article: ReadArticle, now: Long = System.currentTimeMillis()) {
        val (next, dropped) = Reads.add(_articles.value, article, now)
        _articles.value = next
        if (dropped.isNotEmpty()) {
            _progress.update { map -> map.filterKeys { it !in dropped.toSet() } }
            deletedIds = (deletedIds + dropped).distinct()
        }
        persist()
    }

    fun remove(id: String) {
        _articles.value = Reads.remove(_articles.value, id)
        _progress.update { it - id }
        deletedIds = (deletedIds + id).distinct()
        persist()
    }

    fun saveProgress(id: String, positionMs: Long, now: Long = System.currentTimeMillis()) {
        if (id.isBlank()) return
        _progress.update { it + (id to ReadProgress(id, positionMs.coerceAtLeast(0L), now)) }
        persist()
    }

    fun setSpeed(speed: Float) {
        _speed.value = Podcasts.snapSpeed(speed)
        persist()
    }

    suspend fun fetch(raw: String): ReadArticle? = withContext(Dispatchers.IO) {
        val canonical = Reads.normalizeUrl(raw) ?: return@withContext null
        val id = Reads.articleId(canonical) ?: return@withContext null
        val start = Reads.fetchUrl(raw) ?: return@withContext null
        val html = download(start) ?: return@withContext null
        val extracted = ArticleExtract.extract(html, canonical) ?: return@withContext null
        ReadArticle(
            id = id,
            url = canonical,
            title = extracted.title,
            publication = extracted.publication,
            text = extracted.text,
        )
    }

    private fun download(start: String): String? {
        var current = start
        val seen = mutableSetOf<String>()
        repeat(8) {
            if (!seen.add(current)) return null
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val call = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
            call.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val loc = response.header("Location") ?: return null
                    current = Reads.nextFetchUrl(current, loc) ?: return null
                    return@use
                }
                if (!response.isSuccessful) return null
                val type = response.header("Content-Type").orEmpty()
                if (type.contains("pdf", ignoreCase = true) || type.startsWith("image/")) return null
                val bytes = response.body?.bytes() ?: return null
                val capped = if (bytes.size > MAX_BYTES) bytes.copyOf(MAX_BYTES) else bytes
                return String(capped, Charsets.UTF_8)
            }
        }
        return null
    }

    private fun load(): ReadStore {
        if (!file.exists()) return ReadStore()
        return runCatching { json.decodeFromString<ReadStore>(file.readText()) }.getOrDefault(ReadStore())
    }

    private fun persist() {
        val store = ReadStore(
            articles = _articles.value,
            progress = _progress.value.values.toList(),
            deletedIds = deletedIds,
            speed = _speed.value,
        )
        runCatching { file.writeText(json.encodeToString(store)) }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 BuilderLauncher"
        const val MAX_BYTES = 1_500_000
    }
}
