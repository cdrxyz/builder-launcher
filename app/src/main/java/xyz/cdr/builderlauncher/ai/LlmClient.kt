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
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.SettingsRepository
import java.util.concurrent.TimeUnit

class LlmClient(
    private val settings: SettingsRepository,
    private val oauth: OAuthService,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(question: String): String = withContext(Dispatchers.IO) {
        val s = settings.settings.value
        val platform = AiPlatforms.of(s.provider)
        val base = settings.effectiveBaseUrl()
        if (base.isBlank()) {
            return@withContext missingCreds(s.provider)
        }
        if (!EndpointPolicy.allowed(base)) {
            return@withContext "HTTP is only allowed to private LAN hosts. Use HTTPS otherwise."
        }
        if (!CredentialResolver.readyForAsk(s) && s.provider != LlmProvider.HERMES) {
            return@withContext missingCreds(s.provider)
        }
        val bearer = runCatching { oauth.bearer() }.getOrElse {
            return@withContext "Sign-in expired. Open settings and sign in again."
        }
        val current = settings.settings.value
        if (current.provider != LlmProvider.HERMES && bearer.isNullOrBlank()) {
            return@withContext missingCreds(current.provider)
        }
        val oauthLive = CredentialResolver.tokens(current)
            ?.valid(System.currentTimeMillis()) == true
        when (platform.chatKind) {
            ChatKind.OPENAI_CHAT -> openaiChat(base, settings.effectiveModel(), question, bearer)
            ChatKind.ANTHROPIC_MESSAGES -> anthropicMessages(
                base,
                settings.effectiveModel(),
                question,
                bearer,
                oauthLive,
            )
        }
    }

    private fun openaiChat(base: String, model: String, question: String, bearer: String?): String {
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        val body = """
            {
              "model": ${esc(model)},
              "messages": [
                {"role":"system","content":${esc(SYSTEM)}},
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
        if (!bearer.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $bearer")
        }
        return http.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@use llmError(resp.code, raw)
            extractOpenAi(raw) ?: raw.take(400)
        }
    }

    private fun anthropicMessages(
        base: String,
        model: String,
        question: String,
        bearer: String?,
        oauth: Boolean,
    ): String {
        val root = base.trimEnd('/')
        val url = if (root.endsWith("/v1")) "$root/messages" else "$root/v1/messages"
        val body = """
            {
              "model": ${esc(model)},
              "max_tokens": 400,
              "system": ${esc(SYSTEM)},
              "messages": [{"role":"user","content":${esc(question)}}]
            }
        """.trimIndent()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
        if (!bearer.isNullOrBlank()) {
            if (oauth) {
                reqBuilder.header("Authorization", "Bearer $bearer")
                reqBuilder.header("anthropic-beta", "oauth-2024-10-22")
            } else {
                reqBuilder.header("x-api-key", bearer)
            }
        }
        return http.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@use llmError(resp.code, raw)
            extractAnthropic(raw) ?: raw.take(400)
        }
    }

    private fun extractOpenAi(raw: String): String? {
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

    private fun extractAnthropic(raw: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root["content"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
    }

    private fun llmError(code: Int, raw: String): String = "LLM error $code: ${raw.take(280)}"

    private fun missingCreds(provider: LlmProvider): String = when (provider) {
        LlmProvider.HERMES -> "Set a Hermes URL in settings."
        else -> "Sign in or paste an API key in settings."
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
        private const val SYSTEM =
            "You are a concise assistant on a builder's phone. Answer in a few short sentences so they can get back to work. No markdown."
    }
}
