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
import okhttp3.Response
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.data.ChatMessage
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

    suspend fun ask(question: String): String = ask(listOf(ChatMessage(role = "user", content = question)))

    suspend fun ask(
        messages: List<ChatMessage>,
        onDelta: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
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
        val bearer = runCatching { oauth.bearer() }.getOrNull()
        val current = settings.settings.value
        if (current.provider != LlmProvider.HERMES && bearer.isNullOrBlank()) {
            return@withContext missingCreds(current.provider)
        }
        val oauthLive = CredentialResolver.tokens(current)
            ?.valid(System.currentTimeMillis()) == true
        val turns = messages.filter { it.content.isNotBlank() }
        when (platform.chatKind) {
            ChatKind.OPENAI_CHAT -> openaiChat(base, settings.effectiveModel(), turns, bearer, onDelta)
            ChatKind.ANTHROPIC_MESSAGES -> anthropicMessages(
                base,
                settings.effectiveModel(),
                turns,
                bearer,
                oauthLive,
                onDelta,
            )
        }
    }

    private suspend fun openaiChat(
        base: String,
        model: String,
        messages: List<ChatMessage>,
        bearer: String?,
        onDelta: ((String) -> Unit)?,
    ): String {
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        val turns = messagesJson(messages)
        fun body(stream: Boolean) = """
            {
              "model": ${esc(model)},
              "messages": [
                {"role":"system","content":${esc(SYSTEM)}},
                $turns
              ],
              "max_tokens": 2048,
              "temperature": 0.4,
              "stream": $stream
            }
        """.trimIndent()
        fun request(stream: Boolean): Request {
            val reqBuilder = Request.Builder()
                .url("$root/chat/completions")
                .post(body(stream).toRequestBody(JSON))
                .header("Content-Type", "application/json")
            if (!bearer.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $bearer")
            }
            return reqBuilder.build()
        }
        return http.newCall(request(true)).execute().use { resp ->
            if (shouldRetryWithoutStream(resp)) null else readStream(resp, onDelta, openai = true)
        } ?: http.newCall(request(false)).execute().use { resp ->
            readStream(resp, onDelta, openai = true)
        }
    }

    private suspend fun anthropicMessages(
        base: String,
        model: String,
        messages: List<ChatMessage>,
        bearer: String?,
        oauth: Boolean,
        onDelta: ((String) -> Unit)?,
    ): String {
        val root = base.trimEnd('/')
        val url = if (root.endsWith("/v1")) "$root/messages" else "$root/v1/messages"
        val turns = messagesJson(messages)
        fun body(stream: Boolean) = """
            {
              "model": ${esc(model)},
              "max_tokens": 2048,
              "stream": $stream,
              "system": ${esc(SYSTEM)},
              "messages": [$turns]
            }
        """.trimIndent()
        fun request(stream: Boolean): Request {
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body(stream).toRequestBody(JSON))
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
            return reqBuilder.build()
        }
        return http.newCall(request(true)).execute().use { resp ->
            if (shouldRetryWithoutStream(resp)) null else readStream(resp, onDelta, openai = false)
        } ?: http.newCall(request(false)).execute().use { resp ->
            readStream(resp, onDelta, openai = false)
        }
    }

    private fun shouldRetryWithoutStream(resp: Response): Boolean {
        if (resp.isSuccessful) return false
        return resp.code in 400..499 && resp.code != 401 && resp.code != 403 && resp.code != 429
    }

    private suspend fun readStream(
        resp: Response,
        onDelta: ((String) -> Unit)?,
        openai: Boolean,
    ): String {
        val body = resp.body ?: return if (resp.isSuccessful) "" else llmError(resp.code, "")
        if (!resp.isSuccessful) return llmError(resp.code, body.string())
        val source = body.source()
        val first = source.readUtf8Line() ?: return ""
        if (!ChatStream.looksLikeSse(first)) {
            val rest = source.readUtf8()
            val raw = if (rest.isEmpty()) first else first + "\n" + rest
            val full = if (openai) extractOpenAi(raw) else extractAnthropic(raw)
            val text = full ?: raw.take(400)
            emit(text, onDelta)
            return text
        }
        val acc = StringBuilder()
        val frame = StringBuilder()
        suspend fun consume(line: String) {
            if (line.isEmpty()) {
                val data = ChatStream.sseData(frame.toString())
                frame.clear()
                val piece = if (openai) ChatStream.openaiDelta(data) else ChatStream.anthropicDelta(data)
                if (!piece.isNullOrEmpty()) {
                    acc.append(piece)
                    emit(acc.toString(), onDelta)
                }
            } else {
                frame.append(line).append('\n')
            }
        }
        consume(first)
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            consume(line)
        }
        if (frame.isNotEmpty()) consume("")
        return acc.toString()
    }

    private suspend fun emit(text: String, onDelta: ((String) -> Unit)?) {
        if (onDelta == null || text.isEmpty()) return
        withContext(Dispatchers.Main.immediate) { onDelta(text) }
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

    private fun messagesJson(messages: List<ChatMessage>): String =
        messages.joinToString(",") { msg ->
            val role = if (msg.fromUser) "user" else "assistant"
            "{\"role\":${esc(role)},\"content\":${esc(msg.content)}}"
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
            "You are a concise assistant on a builder's phone. Prefer short answers they can act on. Use markdown when it helps: headings, lists, tables, and fenced code. Skip preamble."
    }
}
