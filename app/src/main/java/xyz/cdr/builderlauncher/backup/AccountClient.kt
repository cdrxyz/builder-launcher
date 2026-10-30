package xyz.cdr.builderlauncher.backup

import kotlinx.serialization.SerialName
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
    private val speech: OkHttpClient = HttpClients.derived(connectSec = 20, readSec = 360, writeSec = 60),
    private val origin: String = ORIGIN,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    fun joinWaitlist(email: String) {
        val body = json.encodeToString(WaitlistBody.serializer(), WaitlistBody(email.trim()))
        post("/api/waitlist", body, token = "")
    }

    fun signup(email: String, password: String): AccountSession = auth("/api/auth/signup", email, password)

    fun login(email: String, password: String): AccountSession = auth("/api/auth/login", email, password)

    fun logout(token: String, refreshToken: String = "") {
        val body = json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken))
        runCatching { post("/api/auth/logout", body, token) }
    }

    fun refresh(refreshToken: String): AccountSession {
        val body = json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken))
        val raw = post("/api/auth/refresh", body, token = "")
        return json.decodeFromString(AccountSession.serializer(), raw)
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
        val body = json.encodeToString(VaultPut.serializer(), VaultPut(document.slimForAccount()))
        val raw = put("/api/vault", body, token)
        return json.decodeFromString(VaultEnvelope.serializer(), raw)
    }

    fun prepareArticleAudio(token: String, articleId: String, text: String, voice: String, bearer: String): ArticleAudioStatus {
        val body = json.encodeToString(
            ArticleAudioBody.serializer(),
            ArticleAudioBody(articleId, text, voice, bearer),
        )
        val raw = post("/api/articles/audio", body, token, speech)
        return json.decodeFromString(ArticleAudioStatus.serializer(), raw)
    }

    fun fetchXaiSpeech(bearer: String, text: String, voice: String): ByteArray {
        val body = Json { encodeDefaults = true }.encodeToString(
            XaiSpeechRequest.serializer(),
            XaiSpeechRequest(text, voice),
        )
        val req = Request.Builder()
            .url("https://api.x.ai/v1/tts")
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "audio/mpeg")
            .post(body.toRequestBody(JSON))
            .build()
        speech.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful || bytes.size < 64) throw IllegalStateException("Speech HTTP ${resp.code}")
            return bytes
        }
    }

    fun storeArticleAudio(token: String, articleId: String, voice: String, hash: String, index: Int, audio: ByteArray) {
        val url = "$origin/api/articles/audio?id=$articleId&voice=$voice&hash=$hash&index=$index"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(audio.toRequestBody("audio/mpeg".toMediaType()))
            .build()
        speech.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Could not save audio.")
        }
    }

    fun storedArticleIndexes(token: String, articleId: String, voice: String, hash: String): List<Int> {
        if (token.isBlank()) return emptyList()
        val raw = get(xyz.cdr.builderlauncher.read.Reads.storedAudioQuery(articleId, voice, hash), token)
        return json.decodeFromString(StoredAudioStatus.serializer(), raw).ready
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

    private fun post(path: String, body: String, token: String, client: OkHttpClient = http): String {
        val req = Request.Builder().url("$origin$path").post(body.toRequestBody(JSON))
        if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
        return execute(req.build(), client)
    }

    private fun put(path: String, body: String, token: String): String {
        val req = Request.Builder().url("$origin$path").put(body.toRequestBody(JSON))
        if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
        return execute(req.build())
    }

    private fun execute(req: Request, client: OkHttpClient = http): String {
        client.newCall(req).execute().use { resp ->
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
data class WaitlistBody(val email: String)

@Serializable
data class AccountSession(
    val ok: Boolean = true,
    val email: String = "",
    val token: String = "",
    val refreshToken: String = "",
)

@Serializable
data class RefreshBody(val refreshToken: String = "")

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

@Serializable
data class StoredAudioStatus(val ready: List<Int> = emptyList())

@Serializable
data class ArticleAudioBody(val id: String, val text: String, val voice: String, val bearer: String)

@Serializable
data class ArticleAudioStatus(val ready: Boolean = false, val total: Int = 0, val index: Int = 0)

@Serializable
data class XaiSpeechFormat(
    val codec: String = "mp3",
    val sample_rate: Int = 24000,
    val bit_rate: Int = 64000,
)

@Serializable
data class XaiSpeechRequest(
    val text: String,
    @SerialName("voice_id") val voiceId: String,
    val language: String = "en",
    val speed: Double = 1.0,
    val text_normalization: Boolean = true,
    val output_format: XaiSpeechFormat = XaiSpeechFormat(),
)
