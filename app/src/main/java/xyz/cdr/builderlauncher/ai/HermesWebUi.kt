package xyz.cdr.builderlauncher.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xyz.cdr.builderlauncher.data.ChatMessage

class MemoryCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            this.cookies.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain }
            }
            this.cookies.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        cookies.filter { it.matches(url) }
    }
}

object HermesWebUi {
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun prompt(turns: List<ChatMessage>): String {
        val rows = turns.filter { it.content.isNotBlank() && !it.isNotice }
        if (rows.isEmpty()) return ""
        if (rows.size == 1) return rows.first().content
        return rows.joinToString("\n\n") { row ->
            val who = if (row.fromUser) "User" else "Assistant"
            "$who: ${row.content}"
        }
    }

    fun sessionId(raw: String): String? = firstString(raw, "session_id")

    fun streamId(raw: String): String? = firstString(raw, "stream_id")

    fun passwordRequired(raw: String): Boolean =
        booleanField(raw, "password_required") == true

    fun authenticated(raw: String): Boolean = booleanField(raw, "authenticated") == true

    fun alreadyAuthenticated(code: Int, body: String): Boolean =
        code in 200..299 && authenticated(body)

    fun needsLogin(code: Int, body: String): Boolean {
        if (alreadyAuthenticated(code, body)) return false
        if (code == 401 || code == 403) return true
        return passwordRequired(body)
    }

    fun authFailed(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("LLM error 401") ||
            t.startsWith("LLM error 403") ||
            t.startsWith("Web UI password was rejected") ||
            t.startsWith("Web UI needs a password")
    }

    fun loginBody(password: String): String = """{"password":${esc(password)}}"""

    fun startBody(sessionId: String, message: String): String =
        """{"session_id":${esc(sessionId)},"message":${esc(message)}}"""

    fun newSessionBody(model: String): String {
        val trimmed = model.trim()
        if (trimmed.isEmpty() || trimmed == "default") return "{}"
        return """{"model":${esc(trimmed)}}"""
    }

    suspend fun ask(
        http: OkHttpClient,
        base: String,
        password: String,
        model: String,
        turns: List<ChatMessage>,
        onDelta: ((String) -> Unit)?,
    ): String {
        val root = base.trim().trimEnd('/')
        val message = prompt(turns)
        if (message.isBlank()) return "Ask a question."
        val authed = authenticate(http, root, password)
        if (authed != null) return authed
        val sessionRaw = post(http, "$root/api/session/new", newSessionBody(model), password)
            ?: return "Could not reach the Web UI."
        if (sessionRaw.code !in 200..299) {
            return llmError(sessionRaw.code, sessionRaw.body)
        }
        val sessionId = sessionId(sessionRaw.body) ?: return "Web UI did not return a session."
        val startRaw = post(http, "$root/api/chat/start", startBody(sessionId, message), password)
            ?: return "Could not reach the Web UI."
        if (startRaw.code !in 200..299) {
            return llmError(startRaw.code, startRaw.body)
        }
        val streamId = streamId(startRaw.body) ?: return "Web UI did not return a stream."
        val req = request("$root/api/chat/stream?stream_id=$streamId", password)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use llmError(resp.code, resp.body?.string().orEmpty())
                }
                readStream(resp, onDelta)
            }
        } catch (_: Throwable) {
            "Could not reach the Web UI."
        }
    }

    fun probe(http: OkHttpClient, base: String, password: String): Probe {
        val root = base.trim().trimEnd('/')
        val status = get(http, "$root/api/auth/status", password)
            ?: get(http, "$root/health", password)
            ?: return Probe(false, "Web UI failed: could not reach the host.")
        if (status.code <= 0) return Probe(false, "Web UI failed: could not reach the host.")
        if (status.code !in 200..299 && status.code != 401 && status.code != 403) {
            return Probe(false, "Web UI failed: HTTP ${status.code}.")
        }
        if (alreadyAuthenticated(status.code, status.body) || !needsLogin(status.code, status.body)) {
            return Probe(true, "Web UI reachable.")
        }
        if (password.isBlank()) {
            return Probe(false, "Web UI needs a password in settings.")
        }
        val login = post(http, "$root/api/auth/login", loginBody(password), password)
            ?: return Probe(false, "Web UI failed: could not reach the host.")
        if (login.code == 401 || login.code == 403) {
            return Probe(false, "Web UI password was rejected.")
        }
        if (login.code !in 200..299) {
            return Probe(false, "Web UI failed: HTTP ${login.code}.")
        }
        return Probe(true, "Web UI signed in.")
    }

    private suspend fun readStream(resp: Response, onDelta: ((String) -> Unit)?): String {
        val body = resp.body ?: return "Empty reply from the model."
        val source = body.source()
        val acc = StringBuilder()
        val frame = StringBuilder()
        var error: String? = null
        suspend fun consume() {
            val raw = frame.toString()
            frame.clear()
            if (raw.isBlank()) return
            val event = ChatStream.sseEvent(raw)
            val data = ChatStream.sseData(raw)
            when (event) {
                "token", "message" -> {
                    val piece = ChatStream.webUiDelta(data) ?: return
                    if (piece.isNotEmpty()) {
                        acc.append(piece)
                        if (onDelta != null) {
                            withContext(Dispatchers.Main.immediate) { onDelta(acc.toString()) }
                        }
                    }
                }
                "error" -> error = ChatStream.webUiError(data)
                else -> Unit
            }
        }
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) consume() else frame.append(line).append('\n')
        }
        if (frame.isNotEmpty()) consume()
        error?.let { return it }
        return acc.toString().ifBlank { "Empty reply from the model." }
    }

    private fun authenticate(http: OkHttpClient, root: String, password: String): String? {
        val status = get(http, "$root/api/auth/status", password)
        if (status == null) return "Could not reach the Web UI."
        if (alreadyAuthenticated(status.code, status.body)) return null
        if (!needsLogin(status.code, status.body)) return null
        if (password.isBlank()) return "Web UI needs a password in settings."
        val login = post(http, "$root/api/auth/login", loginBody(password), password)
            ?: return "Could not reach the Web UI."
        if (login.code == 401 || login.code == 403) return "Web UI password was rejected."
        if (login.code !in 200..299) return llmError(login.code, login.body)
        return null
    }

    private data class HttpText(val code: Int, val body: String)

    private fun request(url: String, password: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (password.isNotBlank()) {
            builder.header("Authorization", "Bearer $password")
        }
        return builder
    }

    private fun get(http: OkHttpClient, url: String, password: String = ""): HttpText? = try {
        http.newCall(request(url, password).get().build()).execute().use {
            HttpText(it.code, it.body?.string().orEmpty())
        }
    } catch (_: Throwable) {
        null
    }

    private fun post(http: OkHttpClient, url: String, body: String, password: String = ""): HttpText? = try {
        http.newCall(
            request(url, password)
                .post(body.toRequestBody(JSON))
                .header("Content-Type", "application/json")
                .build(),
        ).execute().use {
            HttpText(it.code, it.body?.string().orEmpty())
        }
    } catch (_: Throwable) {
        null
    }

    private fun firstString(raw: String, key: String): String? {
        val obj = obj(raw) ?: return null
        obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            obj["session"]?.jsonObject?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun booleanField(raw: String, key: String): Boolean? {
        val obj = obj(raw) ?: return null
        val prim = obj[key]?.jsonPrimitive ?: return null
        return prim.booleanOrNull ?: (prim.content == "true")
    }

    private fun obj(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    private fun llmError(code: Int, raw: String): String = "LLM error $code: ${raw.take(280)}"

    private fun esc(value: String): String = buildString {
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
}
