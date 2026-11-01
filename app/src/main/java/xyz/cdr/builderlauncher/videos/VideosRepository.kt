package xyz.cdr.builderlauncher.videos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cdr.builderlauncher.net.HttpClients
import java.io.File

class VideosRepository(
    context: Context,
    private val http: OkHttpClient = HttpClients.derived(connectSec = 15, readSec = 20),
) {
    private val file = File(context.applicationContext.filesDir, "videos.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _videos = MutableStateFlow<List<SavedVideo>>(emptyList())
    val videos: StateFlow<List<SavedVideo>> = _videos.asStateFlow()
    private var deletedIds: List<String> = emptyList()

    init {
        val stored = load()
        _videos.value = Videos.sorted(stored.videos)
        deletedIds = stored.deletedIds
    }

    fun exportBackup(): VideoBackup = VideoBackup(_videos.value, deletedIds)

    fun importBackup(backup: VideoBackup) {
        _videos.value = Videos.sorted(backup.videos)
        deletedIds = backup.deletedIds
        persist()
    }

    fun remember(video: SavedVideo, now: Long = System.currentTimeMillis()) {
        _videos.value = Videos.add(_videos.value, video, now)
        deletedIds = Videos.deletedIdsAfterAdd(deletedIds, video.id)
        persist()
    }

    fun remove(id: String) {
        _videos.value = _videos.value.filterNot { it.id == id }
        deletedIds = (deletedIds + id).distinct()
        persist()
    }

    suspend fun resolve(raw: String): PickedVideo? = withContext(Dispatchers.IO) {
        val id = Videos.youtubeId(raw)
        if (id.isBlank()) return@withContext null
        val request = Request.Builder()
            .url(Videos.playerUrl())
            .header("User-Agent", Videos.PLAYER_UA)
            .post(Videos.playerBody(id).toRequestBody("application/json".toMediaType()))
            .build()
        val body = runCatching { http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
            ?: return@withContext null
        Videos.pick(body)
    }

    private fun load(): VideoBackup {
        if (!file.exists()) return VideoBackup()
        return runCatching { json.decodeFromString<VideoBackup>(file.readText()) }.getOrDefault(VideoBackup())
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(exportBackup()))
    }
}
