package xyz.cdr.builderlauncher.videos

import kotlinx.serialization.Serializable
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class SavedVideo(
    val id: String,
    val url: String,
    val title: String,
    val author: String = "",
    val durationMs: Long = 0L,
    val addedAt: Long = 0L,
)

@Serializable
data class VideoBackup(
    val videos: List<SavedVideo> = emptyList(),
    val deletedIds: List<String> = emptyList(),
)

data class PickedVideo(
    val id: String,
    val title: String,
    val author: String,
    val durationMs: Long,
    val audioUrl: String,
    val videoUrl: String,
    val muxed: Boolean,
)

object Videos {
    const val TITLE = "videos"
    const val SAVED_FEED = "saved-videos"

    fun savedShow(count: Int): PodcastShow? {
        if (count <= 0) return null
        return PodcastShow(feedUrl = SAVED_FEED, title = "Saved Videos", author = "$count saved")
    }
    const val EMPTY = "Paste a YouTube URL."
    const val FAILED = "Could not play that video."
    const val MAX_VIDEOS = 100
    const val PLAYER_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    const val PLAYER_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val json = Json { ignoreUnknownKeys = true }

    fun youtubeId(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        if (idPattern.matches(text)) return text
        val withScheme = if (text.contains("://")) text else "https://$text"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return ""
        val host = uri.host?.removePrefix("www.")?.lowercase().orEmpty()
        if (host == "youtu.be") {
            val id = uri.path.orEmpty().trim('/').substringBefore('/')
            return if (idPattern.matches(id)) id else ""
        }
        if (host != "youtube.com" && host != "m.youtube.com" && host != "music.youtube.com") return ""
        val query = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v")
            ?.removePrefix("=")
            .orEmpty()
        if (idPattern.matches(query)) return query
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val marker = parts.indexOfFirst { it == "shorts" || it == "embed" || it == "live" || it == "v" }
        val id = if (marker >= 0) parts.getOrNull(marker + 1).orEmpty() else ""
        return if (idPattern.matches(id)) id else ""
    }

    fun savedId(youtubeId: String): String = if (youtubeId.isBlank()) "" else "v$youtubeId"

    fun canonicalUrl(youtubeId: String): String =
        if (idPattern.matches(youtubeId)) "https://www.youtube.com/watch?v=$youtubeId" else ""

    fun playerUrl(): String = "https://www.youtube.com/youtubei/v1/player?key=$PLAYER_KEY&prettyPrint=false"

    fun playerBody(youtubeId: String): String =
        """{"context":{"client":{"clientName":"ANDROID","clientVersion":"20.10.38","hl":"en","gl":"US","androidSdkVersion":34}},"videoId":"$youtubeId","contentCheckOk":true,"racyCheckOk":true}"""

    fun pick(body: String): PickedVideo? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val status = root.obj("playabilityStatus")?.str("status").orEmpty()
        if (status != "OK") return null
        val details = root.obj("videoDetails")
        val streaming = root.obj("streamingData") ?: return null
        val formats = (streaming.arr("formats") + streaming.arr("adaptiveFormats")).mapNotNull { it as? JsonObject }
        val audio = best(formats) { mime(it) == "audio/mp4" } ?: best(formats) { mime(it).startsWith("audio/") }
        val muxed = best(formats) { mime(it).startsWith("video/") && it.str("mimeType").orEmpty().contains(Regex("mp4a|opus")) }
        val picture = muxed ?: best(formats) { mime(it) == "video/mp4" } ?: best(formats) { mime(it).startsWith("video/") }
        val chosenAudio = audio ?: picture ?: return null
        val chosenPicture = picture ?: return null
        val seconds = details?.str("lengthSeconds")?.toLongOrNull() ?: 0L
        val youtubeId = details?.str("videoId").orEmpty()
        return PickedVideo(
            id = savedId(youtubeId),
            title = details?.str("title").orEmpty().ifBlank { "Video" },
            author = details?.str("author").orEmpty(),
            durationMs = seconds * 1000L,
            audioUrl = chosenAudio.str("url").orEmpty(),
            videoUrl = chosenPicture.str("url").orEmpty(),
            muxed = muxed != null,
        )
    }

    fun sorted(videos: List<SavedVideo>): List<SavedVideo> = videos.sortedByDescending { it.addedAt }

    fun add(videos: List<SavedVideo>, video: SavedVideo, now: Long): List<SavedVideo> {
        val next = video.copy(addedAt = now)
        return sorted(listOf(next) + videos.filterNot { it.id == next.id }).take(MAX_VIDEOS)
    }

    fun deletedIdsAfterAdd(deletedIds: List<String>, id: String): List<String> = deletedIds.filterNot { it == id }

    fun mergeBackup(a: VideoBackup, b: VideoBackup, restoredIds: Set<String> = emptySet()): VideoBackup {
        val deleted = (a.deletedIds + b.deletedIds).distinct().filter { it !in restoredIds }
        val drop = deleted.toSet()
        val videos = LinkedHashMap<String, SavedVideo>()
        (a.videos + b.videos).forEach { item ->
            if (item.id in drop) return@forEach
            val prev = videos[item.id]
            videos[item.id] = if (prev == null || item.addedAt >= prev.addedAt) item else prev
        }
        return VideoBackup(videos = sorted(videos.values.toList()), deletedIds = deleted)
    }

    private fun best(formats: List<JsonObject>, test: (JsonObject) -> Boolean): JsonObject? =
        formats.filter { it.str("url") != null && it.str("signatureCipher") == null && it.str("cipher") == null && test(it) }
            .maxByOrNull { it.long("bitrate") }

    private fun mime(format: JsonObject): String = format.str("mimeType").orEmpty().substringBefore(';').trim().lowercase()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String) = (this[key] as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull
        ?: this[key]?.jsonPrimitive?.intOrNull?.toLong()
        ?: 0L
}
