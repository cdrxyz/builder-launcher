package xyz.cdr.builderlauncher.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cdr.builderlauncher.net.HttpClients

class AccountClient(
    private val http: OkHttpClient = HttpClients.derived(connectSec = 20, readSec = 60, writeSec = 60),
    private val origin: String = ORIGIN,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    fun signup(email: String, password: String): AccountSession = auth("/api/auth/signup", email, password)

    fun login(email: String, password: String): AccountSession = auth("/api/auth/login", email, password)

    fun logout(token: String) {
        runCatching { post("/api/auth/logout", "{}", token) }
    }

    fun me(token: String): AccountSession {
        val raw = get("/api/auth/me", token)
        return json.decodeFromString(AccountSession.serializer(), raw)
    }

    fun getVault(token: String): VaultEnvelope {
        val raw = get("/api/vault", token)
        return json.decodeFromString(VaultEnvelope.serializer(), raw)
    }

    fun putVault(token: String, document: BackupDocument): VaultEnvelope {
        val body = json.encodeToString(VaultPut.serializer(), VaultPut(document))
        val raw = put("/api/vault", body, token)
        return json.decodeFromString(VaultEnvelope.serializer(), raw)
    }

    private fun auth(path: String, email: String, password: String): AccountSession {
        val body = json.encodeToString(AuthBody.serializer(), AuthBody(email.trim(), password))
        val raw = post(path, body, token = "")
        return json.decodeFromString(AccountSession.serializer(), raw)
    }

    private fun get(path: String, token: String): String {
        val req = Request.Builder().url("$origin$path").get()
        if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
        return execute(req.build())
    }

    private fun post(path: String, body: String, token: String): String {
        val req = Request.Builder().url("$origin$path").post(body.toRequestBody(JSON))
        if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
        return execute(req.build())
    }

    private fun put(path: String, body: String, token: String): String {
        val req = Request.Builder().url("$origin$path").put(body.toRequestBody(JSON))
        if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
        return execute(req.build())
    }

    private fun execute(req: Request): String {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching { json.decodeFromString(ErrorBody.serializer(), text).error }.getOrNull()
                throw IllegalStateException(err ?: "HTTP ${resp.code}")
            }
            return text
        }
    }

    companion object {
        const val ORIGIN = "https://builder.cdr.xyz"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
data class AuthBody(val email: String, val password: String)

@Serializable
data class AccountSession(val ok: Boolean = true, val email: String = "", val token: String = "")

@Serializable
data class VaultPut(val document: BackupDocument)

@Serializable
data class VaultEnvelope(
    val ok: Boolean = true,
    val revision: Int = 0,
    val document: BackupDocument? = null,
    val updatedAt: String? = null,
)

@Serializable
private data class ErrorBody(val error: String = "")
