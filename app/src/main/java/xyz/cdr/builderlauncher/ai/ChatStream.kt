package xyz.cdr.builderlauncher.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ChatStream {
    private val json = Json { ignoreUnknownKeys = true }

    fun sseEvent(frame: String): String =
        frame.lineSequence()
            .firstOrNull { it.startsWith("event:") }
            ?.removePrefix("event:")
            ?.trim()
            .orEmpty()
            .ifBlank { "message" }

    fun sseData(frame: String): String =
        frame.lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

    fun webUiDelta(data: String): String? {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            json.parseToJsonElement(trimmed).jsonObject["text"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    fun webUiError(data: String): String {
        val trimmed = data.trim()
        val msg = runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            root["error"]?.jsonPrimitive?.content
                ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Web UI stream error."
    }

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
