package xyz.cdr.builderlauncher.data

import kotlinx.serialization.Serializable
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver

@Serializable
data class ProviderAccount(
    val apiKey: String = "",
    val oauthAccess: String = "",
    val oauthRefresh: String = "",
    val oauthExpiresAtEpochMs: Long = 0L,
    val oauthAccount: String = "",
    val model: String = "",
    val baseUrl: String = "",
) {
    fun apply(base: BuilderSettings, provider: LlmProvider): BuilderSettings = base.copy(
        provider = provider,
        apiKey = apiKey,
        oauthAccess = oauthAccess,
        oauthRefresh = oauthRefresh,
        oauthExpiresAtEpochMs = oauthExpiresAtEpochMs,
        oauthAccount = oauthAccount,
        model = model,
        hermesBaseUrl = baseUrl,
    )

    companion object {
        fun of(settings: BuilderSettings) = ProviderAccount(
            apiKey = settings.apiKey,
            oauthAccess = settings.oauthAccess,
            oauthRefresh = settings.oauthRefresh,
            oauthExpiresAtEpochMs = settings.oauthExpiresAtEpochMs,
            oauthAccount = settings.oauthAccount,
            model = settings.model,
            baseUrl = settings.hermesBaseUrl,
        )
    }
}

object ProviderAccounts {
    fun remember(
        accounts: Map<String, ProviderAccount>,
        settings: BuilderSettings,
    ): Map<String, ProviderAccount> = accounts + (settings.provider.name to ProviderAccount.of(settings))

    fun view(
        accounts: Map<String, ProviderAccount>,
        current: BuilderSettings,
        provider: LlmProvider,
    ): BuilderSettings {
        if (current.provider == provider) return current
        val stored = accounts[provider.name]
        return if (stored != null) {
            stored.apply(current, provider)
        } else {
            current.copy(provider = provider).clearedOAuth().copy(
                apiKey = "",
                model = "",
                hermesBaseUrl = AiPlatforms.of(provider).defaultLocalBase.orEmpty(),
            )
        }
    }

    fun connected(
        accounts: Map<String, ProviderAccount>,
        current: BuilderSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): List<LlmProvider> {
        return LlmProvider.entries.filter { provider ->
            if (AiPlatforms.of(provider).needsBaseUrl &&
                provider != current.provider &&
                accounts[provider.name] == null
            ) {
                return@filter false
            }
            CredentialResolver.readyForAsk(view(accounts, current, provider), nowMs)
        }
    }
}
