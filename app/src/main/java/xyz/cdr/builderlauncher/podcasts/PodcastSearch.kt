package xyz.cdr.builderlauncher.podcasts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PodcastSearch {
    const val HOST = "https://itunes.apple.com/search"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<PodcastHit> {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()
            results.mapNotNull { el ->
                val obj = el.jsonObject
                val feed = obj["feedUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (feed.isEmpty()) return@mapNotNull null
                val title = obj["collectionName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: obj["trackName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                PodcastHit(
                    title = title,
                    author = obj["artistName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    feedUrl = feed,
                    artworkUrl = obj["artworkUrl600"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?: obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }
}
