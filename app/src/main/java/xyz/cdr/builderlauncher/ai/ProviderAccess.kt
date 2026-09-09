package xyz.cdr.builderlauncher.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.SettingsRepository
import java.util.concurrent.TimeUnit

sealed class AccessCheck {
    data object Testing : AccessCheck()
    data class Done(val ok: Boolean, val line: String) : AccessCheck()
}

data class Probe(val ok: Boolean, val detail: String)

object AccessReport {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromModels(code: Int, body: String, wanted: String): Probe {
        if (code == 401) return Probe(false, "Access failed: unauthorized. Check the API key.")
        if (code == 403) return Probe(false, "Access failed: forbidden.")
        if (code !in 200..299) return Probe(false, "Access failed: LLM error $code.")
        val ids = modelIds(body)
        val model = wanted.trim()
        val line = when {
            model.isNotEmpty() && ids.any { it.equals(model, ignoreCase = true) } ->
                "Access good — $model"
            model.isNotEmpty() && ids.isNotEmpty() ->
                "Access good — ${ids.size} models ($model not listed)"
            ids.isNotEmpty() -> "Access good — ${ids.first()}"
            else -> "Access good."
        }
        return Probe(true, line)
    }

    fun fromWebUi(code: Int): Probe {
        if (code in 200..299) return Probe(true, "Web UI reachable.")
        if (code == 401 || code == 403) return Probe(true, "Web UI reachable (sign-in required).")
        if (code <= 0) return Probe(false, "Web UI failed: could not reach the host.")
        return Probe(false, "Web UI failed: HTTP $code.")
    }

    fun combine(api: Probe?, web: Probe?): AccessCheck.Done {
        val parts = listOfNotNull(api?.detail, web?.detail)
        if (parts.isEmpty()) return AccessCheck.Done(false, "Set a base URL in settings.")
        val ok = (api?.ok ?: true) && (web?.ok ?: true)
        return AccessCheck.Done(ok, parts.joinToString(". "))
    }

    fun modelIds(raw: String): List<String> {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root["data"]?.jsonArray.orEmpty().mapNotNull { el ->
                el.jsonObject["id"]?.jsonPrimitive?.content
            }
        }.getOrDefault(emptyList())
    }
}

class ProviderAccess(
    private val settings: SettingsRepository,
    private val oauth: OAuthService,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun check(snapshot: BuilderSettings = settings.settings.value): AccessCheck.Done =
        withContext(Dispatchers.IO) {
            val platform = AiPlatforms.of(snapshot.provider)
            val apiBase = settings.effectiveBaseUrl(snapshot)
            val webUi = if (snapshot.provider == LlmProvider.HERMES) HermesUrls.webUi(snapshot) else ""
            if (apiBase.isBlank() && webUi.isBlank()) {
                return@withContext AccessCheck.Done(false, missingCreds(snapshot.provider))
            }
            val api = if (apiBase.isNotBlank()) probeApi(snapshot, platform, apiBase) else null
            val web = if (webUi.isNotBlank()) probeWebUi(webUi) else null
            AccessReport.combine(api, web)
        }

    private fun probeApi(snapshot: BuilderSettings, platform: AiPlatform, base: String): Probe {
        if (!EndpointPolicy.allowed(base)) {
            return Probe(false, "HTTP is only allowed to private LAN hosts. Use HTTPS otherwise.")
        }
        if (!CredentialResolver.readyForAsk(snapshot) && !platform.keyOptional) {
            return Probe(false, missingCreds(snapshot.provider))
        }
        val bearer = if (snapshot.provider == settings.settings.value.provider) {
            runCatching { oauth.bearer() }.getOrNull()
        } else {
            CredentialResolver.bearer(snapshot)
        }
        if (!platform.keyOptional && bearer.isNullOrBlank()) {
            return Probe(false, missingCreds(snapshot.provider))
        }
        val oauthLive = CredentialResolver.tokens(snapshot)
            ?.valid(System.currentTimeMillis()) == true
        val url = modelsUrl(platform, base)
        val reqBuilder = Request.Builder().url(url).get()
        if (!bearer.isNullOrBlank()) {
            if (platform.chatKind == ChatKind.ANTHROPIC_MESSAGES) {
                if (oauthLive) {
                    reqBuilder.header("Authorization", "Bearer $bearer")
                    reqBuilder.header("anthropic-beta", "oauth-2024-10-22")
                } else {
                    reqBuilder.header("x-api-key", bearer)
                }
                reqBuilder.header("anthropic-version", "2023-06-01")
            } else {
                reqBuilder.header("Authorization", "Bearer $bearer")
            }
        }
        platform.extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            http.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                AccessReport.fromModels(resp.code, body, settings.effectiveModel(snapshot))
            }
        } catch (_: Throwable) {
            Probe(false, "Could not reach the model.")
        }
    }

    private fun probeWebUi(base: String): Probe {
        if (!EndpointPolicy.allowed(base)) {
            return Probe(false, "Web UI blocked: HTTP is only allowed to private LAN hosts.")
        }
        val root = base.trim().trimEnd('/')
        var last = Probe(false, "Web UI failed: could not reach the host.")
        for (path in listOf("/api/status", "/health")) {
            last = try {
                http.newCall(Request.Builder().url("$root$path").get().build()).execute().use { resp ->
                    AccessReport.fromWebUi(resp.code)
                }
            } catch (_: Throwable) {
                Probe(false, "Web UI failed: could not reach the host.")
            }
            if (last.ok) return last
        }
        return last
    }

    private fun modelsUrl(platform: AiPlatform, base: String): String {
        return when (platform.chatKind) {
            ChatKind.OPENAI_CHAT -> "${AiPlatforms.chatRoot(base)}/models"
            ChatKind.ANTHROPIC_MESSAGES -> {
                val root = base.trimEnd('/')
                if (root.endsWith("/v1")) "$root/models" else "$root/v1/models"
            }
        }
    }

    private fun missingCreds(provider: LlmProvider): String {
        val platform = AiPlatforms.of(provider)
        return when {
            platform.needsBaseUrl && platform.keyOptional -> "Set a base URL in settings."
            platform.needsBaseUrl -> "Set a base URL and API key in settings."
            else -> "Sign in or paste an API key in settings."
        }
    }
}
