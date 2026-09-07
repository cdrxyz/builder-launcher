package xyz.cdr.builderlauncher.ai.oauth

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.OAuthSpec
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.SettingsRepository

class OAuthService(
    private val settings: SettingsRepository,
    private val poster: FormPoster = OkHttpFormPoster(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun spec(provider: LlmProvider = settings.settings.value.provider): OAuthSpec? =
        AiPlatforms.of(provider).oauth

    suspend fun beginDevice(provider: LlmProvider): DevicePending = withContext(Dispatchers.IO) {
        val oauth = spec(provider) as? OAuthSpec.Device
            ?: throw IllegalStateException("This provider does not use device-code sign-in")
        DeviceCodeFlow.request(poster, oauth)
    }

    fun beginPkce(provider: LlmProvider): PkceSession {
        val oauth = spec(provider) as? OAuthSpec.PkcePaste
            ?: throw IllegalStateException("This provider does not use paste sign-in")
        return PkcePasteFlow.begin(oauth)
    }

    suspend fun completePkce(provider: LlmProvider, session: PkceSession, pasted: String) =
        withContext(Dispatchers.IO) {
            val oauth = spec(provider) as? OAuthSpec.PkcePaste
                ?: throw IllegalStateException("This provider does not use paste sign-in")
            val tokens = PkcePasteFlow.complete(poster, oauth, session, pasted, now())
            settings.saveOAuth(tokens)
        }

    suspend fun pollUntilAuthorized(provider: LlmProvider, pending: DevicePending): OAuthTokens =
        withContext(Dispatchers.IO) {
            val oauth = spec(provider) as? OAuthSpec.Device
                ?: throw IllegalStateException("This provider does not use device-code sign-in")
            var intervalMs = pending.intervalSeconds.coerceAtLeast(1) * 1000L
            val deadline = now() + pending.expiresInSeconds * 1000L
            while (now() < deadline) {
                delay(intervalMs)
                when (val result = DeviceCodeFlow.pollOnce(poster, oauth, pending.deviceCode, now())) {
                    PollResult.Pending -> Unit
                    is PollResult.SlowDown -> intervalMs += result.extraSeconds * 1000L
                    is PollResult.Success -> {
                        settings.saveOAuth(result.tokens)
                        return@withContext result.tokens
                    }
                    is PollResult.Failed -> throw IllegalStateException(result.message)
                }
            }
            throw IllegalStateException("Sign-in timed out. Start again.")
        }

    fun refreshIfNeeded() {
        val s = settings.settings.value
        val oauth = spec(s.provider) ?: return
        if (!CredentialResolver.needsRefresh(s, now())) return
        val refresh = s.oauthRefresh
        if (refresh.isBlank()) return
        val tokens = DeviceCodeFlow.refresh(poster, oauth, refresh, now())
        settings.saveOAuth(tokens)
    }

    fun bearer(): String? {
        runCatching { refreshIfNeeded() }
        return CredentialResolver.bearer(settings.settings.value, now())
    }

    fun signOut() {
        settings.clearOAuth()
    }

    fun browserUrl(pending: DevicePending, provider: LlmProvider): String? {
        val hosts = spec(provider)?.allowedHosts ?: return null
        return VerificationPolicy.openUrl(pending.verificationUriComplete, pending.verificationUri, hosts)
    }
}
