package xyz.cdr.builderlauncher.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.net.HttpClients

class LlmClient(
    private val settings: SettingsRepository,
    private val oauth: OAuthService,
    private val http: OkHttpClient = HttpClients.derived(
        connectSec = 20,
        readSec = 90,
        cookieJar = MemoryCookieJar(),
    ),
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
        val primaryFail = last
        for (provider in AiFallback.order(primary).drop(1)) {
            if (provider !in settings.connectedProviders()) continue
            val view = settings.viewAs(provider)
            val next = askOne(view, turns, onDelta = null)
            if (!AiFallback.failed(next.text)) {
                emit(next.text, onDelta)
                return@withContext next.copy(
                    fallbackFrom = provider,
                    primaryProvider = primary,
                    primaryError = primaryFail.text,
                )
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
            if (!HermesWebUi.authFailed(text)) return LlmAnswer(text)
            val api = HermesUrls.apiBase(snapshot)
            if (api.isBlank() || !EndpointPolicy.allowed(api)) return LlmAnswer(text)
            val bearer = snapshot.apiKey.trim().ifBlank { null }
            val viaApi = try {
                openaiChat(
                    api,
                    settings.effectiveModel(snapshot),
                    turns,
                    bearer,
                    onDelta,
                    provider = snapshot.provider,
                )
            } catch (_: Throwable) {
                text
            }
            return LlmAnswer(if (HermesWebUi.authFailed(viaApi)) text else viaApi)
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
                    snapshot.provider,
                )
                ChatKind.ANTHROPIC_MESSAGES -> anthropicMessages(
                    base,
                    settings.effectiveModel(snapshot),
                    turns,
                    bearer,
                    oauthLive,
                    onDelta,
                    snapshot.provider,
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
        provider: LlmProvider = LlmProvider.GENERIC,
    ): String {
        val root = AiPlatforms.chatRoot(base)
        return toolChat(
            url = "$root/chat/completions",
            model = model,
            messages = AiTools.textMessages(messages, system = true),
            bearer = bearer,
            onDelta = onDelta,
            extraHeaders = extraHeaders,
            provider = provider,
            anthropic = false,
            oauth = false,
        )
    }

    private suspend fun anthropicMessages(
        base: String,
        model: String,
        messages: List<ChatMessage>,
        bearer: String?,
        oauth: Boolean,
        onDelta: ((String) -> Unit)?,
        provider: LlmProvider,
    ): String {
        val root = base.trimEnd('/')
        val url = if (root.endsWith("/v1")) "$root/messages" else "$root/v1/messages"
        return toolChat(
            url = url,
            model = model,
            messages = AiTools.textMessages(messages, system = false),
            bearer = bearer,
            onDelta = onDelta,
            extraHeaders = emptyMap(),
            provider = provider,
            anthropic = true,
            oauth = oauth,
        )
    }

    private suspend fun toolChat(
        url: String,
        model: String,
        messages: List<kotlinx.serialization.json.JsonObject>,
        bearer: String?,
        onDelta: ((String) -> Unit)?,
        extraHeaders: Map<String, String>,
        provider: LlmProvider,
        anthropic: Boolean,
        oauth: Boolean,
    ): String {
        var wire = messages
        var privacy = true
        var tools = true
        var strips = 0
        var round = 0
        while (round <= AiTools.MAX_ROUNDS) {
            val body = if (anthropic) {
                AiTools.anthropicRequest(model, wire, tools, stream = true)
            } else {
                AiTools.openaiRequest(model, wire, provider, privacy, tools, stream = true)
            }
            val posted = postChat(url, body, bearer, extraHeaders, anthropic, oauth, onDelta, streamFirst = true)
            if (posted is ChatRound.Fail) {
                val drop = if (posted.code == 400) AiTools.droppedFields(posted.raw) else AiTools.Drop(false, false)
                val canStrip = (drop.privacy && privacy) || (drop.tools && tools)
                if (canStrip && strips < 2) {
                    if (drop.privacy) privacy = false
                    if (drop.tools) tools = false
                    strips += 1
                    continue
                }
                return llmError(posted.code, posted.raw)
            }
            val turn = (posted as ChatRound.Ok).turn
            when (val step = AiTools.toolStep(turn.calls, round, tools)) {
                "answer" -> return turn.text.ifBlank { EMPTY_REPLY }
                else -> {
                    AiTools.status(turn.calls)?.let { emit(it, onDelta) }
                    val results = turn.calls.take(4).map { call -> call.id to AiTools.run(call.name, call.arguments) { toolGet(it) } }
                    wire = if (anthropic) AiTools.appendAnthropicTools(wire, turn, results) else AiTools.appendOpenAiTools(wire, turn, results)
                    if (step == "finish") tools = false else round += 1
                }
            }
        }
        return EMPTY_REPLY
    }

    private suspend fun postChat(
        url: String,
        streamedBody: String,
        bearer: String?,
        extraHeaders: Map<String, String>,
        anthropic: Boolean,
        oauth: Boolean,
        onDelta: ((String) -> Unit)?,
        streamFirst: Boolean,
    ): ChatRound {
        fun body(stream: Boolean): String {
            val root = json.parseToJsonElement(streamedBody).jsonObject.toMutableMap()
            root["stream"] = kotlinx.serialization.json.JsonPrimitive(stream)
            return kotlinx.serialization.json.JsonObject(root).toString()
        }
        val first = executeChat(url, if (streamFirst) streamedBody else body(false), bearer, extraHeaders, anthropic, oauth, onDelta)
        if (first is ChatRound.Fail && first.code in 400..499 && first.code != 401 && first.code != 403 && first.code != 429) {
            val drop = AiTools.droppedFields(first.raw)
            if (drop.privacy || drop.tools) return first
            return executeChat(url, body(false), bearer, extraHeaders, anthropic, oauth, onDelta)
        }
        return first
    }

    private suspend fun executeChat(
        url: String,
        body: String,
        bearer: String?,
        extraHeaders: Map<String, String>,
        anthropic: Boolean,
        oauth: Boolean,
        onDelta: ((String) -> Unit)?,
    ): ChatRound {
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json")
        if (anthropic) {
            reqBuilder.header("anthropic-version", "2023-06-01")
            if (!bearer.isNullOrBlank()) {
                if (oauth) {
                    reqBuilder.header("Authorization", "Bearer $bearer")
                    reqBuilder.header("anthropic-beta", "oauth-2024-10-22")
                } else {
                    reqBuilder.header("x-api-key", bearer)
                }
            }
        } else if (!bearer.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $bearer")
        }
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        return http.newCall(reqBuilder.build()).execute().use { resp ->
            val responseBody = resp.body ?: return@use ChatRound.Fail(resp.code, "")
            if (!resp.isSuccessful) return@use ChatRound.Fail(resp.code, responseBody.string())
            val source = responseBody.source()
            val accum = AiTools.TurnAccum(openai = !anthropic)
            var first: String? = null
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isNotEmpty()) {
                    first = line
                    break
                }
            }
            val start = first ?: return@use ChatRound.Ok(AiTools.Turn("", emptyList()))
            if (!ChatStream.looksLikeSse(start)) {
                val rest = source.readUtf8()
                val raw = if (rest.isEmpty()) start else start + "\n" + rest
                val turn = AiTools.parseBody(raw, openai = !anthropic) ?: AiTools.Turn(raw.take(400), emptyList())
                if (turn.calls.isEmpty()) emit(turn.text, onDelta)
                return@use ChatRound.Ok(turn)
            }
            val frame = StringBuilder()
            suspend fun consume(line: String) {
                if (line.isEmpty()) {
                    val data = ChatStream.sseData(frame.toString())
                    frame.clear()
                    val before = accum.turn().text.length
                    accum.acceptData(data)
                    val turn = accum.turn()
                    if (turn.calls.isEmpty() && turn.text.length > before) emit(turn.text, onDelta)
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
            ChatRound.Ok(accum.turn())
        }
    }

    private fun toolGet(url: String): String? = toolGet(url, hops = 0)

    private fun toolGet(url: String, hops: Int): String? {
        if (hops > 2 || AiTools.publicPageUrl(url) == null) return null
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", AiTools.SEARCH_UA)
            .header("Accept", "text/html,text/plain,application/json")
            .get()
            .build()
        return runCatching {
            http.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                    if (resp.code in 300..399) resp.header("Location") to null
                    else if (!resp.isSuccessful) null to null
                    else null to resp.body?.string()?.take(80_000)
                }
        }.getOrNull()?.let { (redirect, body) ->
            if (redirect != null) toolGet(java.net.URI(url).resolve(redirect).toString(), hops + 1) else body
        }
    }

    private sealed class ChatRound {
        data class Ok(val turn: AiTools.Turn) : ChatRound()
        data class Fail(val code: Int, val raw: String) : ChatRound()
    }

    private suspend fun emit(text: String, onDelta: ((String) -> Unit)?) {
        if (onDelta == null || text.isEmpty()) return
        withContext(Dispatchers.Main.immediate) { onDelta(text) }
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

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val EMPTY_REPLY = "Empty reply from the model."
    }
}
