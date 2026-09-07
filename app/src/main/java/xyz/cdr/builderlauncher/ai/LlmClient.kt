package xyz.cdr.builderlauncher.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cdr.builderlauncher.data.SettingsRepository
import java.util.concurrent.TimeUnit

class LlmClient(
    private val settings: SettingsRepository,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(question: String): String = withContext(Dispatchers.IO) {
        val base = settings.effectiveBaseUrl()
        if (base.isBlank()) {
            return@withContext "Set a Hermes URL or xAI key in settings."
        }
        if (!EndpointPolicy.allowed(base)) {
            return@withContext "HTTP is only allowed to private LAN hosts. Use HTTPS otherwise."
        }
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        val body = """
            {
              "model": ${esc(settings.effectiveModel())},
              "messages": [
                {"role":"system","content":"You are a concise assistant on a builder's phone. Answer in a few short sentences so they can get back to work. No markdown."},
                {"role":"user","content":${esc(question)}}
              ],
              "max_tokens": 400,
              "temperature": 0.4
            }
        """.trimIndent()
        val reqBuilder = Request.Builder()
            .url("$root/chat/completions")
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json")
        val key = settings.settings.value.apiKey.trim()
        if (key.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $key")
        }
        http.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext "LLM error ${resp.code}: ${raw.take(280)}"
            }
            extract(raw) ?: raw.take(400)
        }
    }

    private fun extract(raw: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    private fun esc(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
