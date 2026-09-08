package xyz.cdr.builderlauncher.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ChatStream {
    private val json = Json { ignoreUnknownKeys = true }

    fun sseData(frame: String): String =
        frame.lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

    fun looksLikeSse(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("data:") || trimmed.startsWith("event:")
    }

    fun openaiDelta(data: String): String? {
        val trimmed = data.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return null
        return runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    fun anthropicDelta(data: String): String? {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            if (root["type"]?.jsonPrimitive?.content != "content_block_delta") {
                return@runCatching null
            }
            root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
        }.getOrNull()
    }
}
