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
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.SettingsRepository
import java.util.concurrent.TimeUnit

class LlmClient(
    private val settings: SettingsRepository,
    private val oauth: OAuthService,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(question: String): LlmAnswer = ask(listOf(ChatMessage(role = "user", content = question)))

    suspend fun ask(
        messages: List<ChatMessage>,
        onDelta: ((String) -> Unit)? = null,
    ): LlmAnswer = withContext(Dispatchers.IO) {
        val turns = messages.filter { it.content.isNotBlank() && !it.isNotice }
        val primary = settings.settings.value.provider
        var last = askOne(settings.settings.value, turns, onDelta)
        if (!AiFallback.failed(last.text) || primary != LlmProvider.HERMES) return@withContext last
        for (provider in AiFallback.order(primary).drop(1)) {
            if (provider !in settings.connectedProviders()) continue
            val view = settings.viewAs(provider)
            val next = askOne(view, turns, onDelta = null)
            if (!AiFallback.failed(next.text)) {
                emit(next.text, onDelta)
                return@withContext next.copy(fallbackFrom = provider)
            }
            last = next
        }
        last
    }

    private suspend fun askOne(
        snapshot: BuilderSettings,
        turns: List<ChatMessage>,
        onDelta: ((String) -> Unit)?,
    ): LlmAnswer {
        val platform = AiPlatforms.of(snapshot.provider)
        if (snapshot.provider == LlmProvider.HERMES) {
            val web = HermesUrls.webUi(snapshot)
            if (web.isBlank()) return LlmAnswer(missingCreds(snapshot.provider))
            if (!EndpointPolicy.allowed(web)) {
                return LlmAnswer("HTTP is only allowed to private LAN hosts. Use HTTPS otherwise.")
            }
            val text = try {
                HermesWebUi.ask(
                    http,
                    web,
                    snapshot.apiKey,
                    settings.effectiveModel(snapshot),
                    turns,
                    onDelta,
                )
            } catch (_: Throwable) {
                "Could not reach the Web UI."
            }
            return LlmAnswer(text)
        }
        val base = settings.effectiveBaseUrl(snapshot)
        if (base.isBlank()) {
            return LlmAnswer(missingCreds(snapshot.provider))
        }
        if (!EndpointPolicy.allowed(base)) {
            return LlmAnswer("HTTP is only allowed to private LAN hosts. Use HTTPS otherwise.")
        }
        if (!CredentialResolver.readyForAsk(snapshot)) {
            return LlmAnswer(missingCreds(snapshot.provider))
        }
        val bearer = if (snapshot.provider == settings.settings.value.provider) {
            runCatching { oauth.bearer() }.getOrNull()
        } else {
            CredentialResolver.bearer(snapshot)
        }
        if (!platform.keyOptional && bearer.isNullOrBlank()) {
            return LlmAnswer(missingCreds(snapshot.provider))
        }
        val oauthLive = CredentialResolver.tokens(snapshot)
            ?.valid(System.currentTimeMillis()) == true
        val text = try {
            when (platform.chatKind) {
                ChatKind.OPENAI_CHAT -> openaiChat(
                    base,
                    settings.effectiveModel(snapshot),
                    turns,
                    bearer,
                    onDelta,
                    platform.extraHeaders,
                )
                ChatKind.ANTHROPIC_MESSAGES -> anthropicMessages(
                    base,
                    settings.effectiveModel(snapshot),
                    turns,
                    bearer,
                    oauthLive,
                    onDelta,
                )
            }
        } catch (_: Throwable) {
            "Could not reach the model."
        }
        return LlmAnswer(text)
    }

    private suspend fun openaiChat(
        base: String,
        model: String,
        messages: List<ChatMessage>,
        bearer: String?,
        onDelta: ((String) -> Unit)?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val root = AiPlatforms.chatRoot(base)
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
            extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
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
        val body = resp.body ?: return if (resp.isSuccessful) EMPTY_REPLY else llmError(resp.code, "")
        if (!resp.isSuccessful) return llmError(resp.code, body.string())
        val source = body.source()
        var first: String? = null
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isNotEmpty()) {
                first = line
                break
            }
        }
        val start = first ?: return EMPTY_REPLY
        if (!ChatStream.looksLikeSse(start)) {
            val rest = source.readUtf8()
            val raw = if (rest.isEmpty()) start else start + "\n" + rest
            val full = if (openai) extractOpenAi(raw) else extractAnthropic(raw)
            val text = full ?: raw.take(400)
            emit(text, onDelta)
            return text.ifBlank { EMPTY_REPLY }
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
        consume(start)
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            consume(line)
        }
        if (frame.isNotEmpty()) consume("")
        return acc.toString().ifBlank { EMPTY_REPLY }
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

    private fun missingCreds(provider: LlmProvider): String {
        val platform = AiPlatforms.of(provider)
        return when {
            provider == LlmProvider.HERMES -> "Set a Web UI URL in settings."
            platform.needsBaseUrl && platform.keyOptional -> "Set a base URL in settings."
            platform.needsBaseUrl -> "Set a base URL and API key in settings."
            else -> "Sign in or paste an API key in settings."
        }
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
        private const val EMPTY_REPLY = "Empty reply from the model."
        private const val SYSTEM =
            "You are a concise assistant on a builder's phone. Prefer short answers they can act on. Use markdown when it helps: headings, lists, tables, and fenced code. Skip preamble."
    }
}
