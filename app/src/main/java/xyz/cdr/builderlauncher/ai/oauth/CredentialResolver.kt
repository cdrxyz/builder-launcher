package xyz.cdr.builderlauncher.ai.oauth

import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.data.BuilderSettings

object CredentialResolver {
    fun tokens(settings: BuilderSettings): OAuthTokens? {
        if (settings.oauthAccess.isBlank() && settings.oauthRefresh.isBlank()) return null
        return OAuthTokens(
            accessToken = settings.oauthAccess,
            refreshToken = settings.oauthRefresh,
            expiresAtEpochMs = settings.oauthExpiresAtEpochMs,
            account = settings.oauthAccount,
        )
    }

    fun needsRefresh(settings: BuilderSettings, nowMs: Long, skewMs: Long = 120_000L): Boolean {
        val t = tokens(settings) ?: return false
        if (t.refreshToken.isBlank()) return false
        return !t.valid(nowMs, skewMs)
    }

    fun bearer(settings: BuilderSettings, nowMs: Long = System.currentTimeMillis()): String? {
        val t = tokens(settings)
        if (t != null && t.accessToken.isNotBlank() && t.valid(nowMs)) return t.accessToken
        val key = settings.apiKey.trim()
        return key.ifBlank { null }
    }

    fun readyForAsk(settings: BuilderSettings, nowMs: Long = System.currentTimeMillis()): Boolean {
        val platform = AiPlatforms.of(settings.provider)
        if (platform.needsBaseUrl && settings.hermesBaseUrl.trim().isEmpty()) return false
        if (platform.keyOptional) return true
        if (bearer(settings, nowMs) != null) return true
        val t = tokens(settings)
        return t != null && t.refreshToken.isNotBlank()
    }
}
