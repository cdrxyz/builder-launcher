package xyz.cdr.builderlauncher.ai

import xyz.cdr.builderlauncher.data.LlmProvider

data class AiPlatform(
    val provider: LlmProvider,
    val label: String,
    val apiBase: String?,
    val defaultModel: String,
    val chatKind: ChatKind,
    val oauth: OAuthSpec?,
)

enum class ChatKind { OPENAI_CHAT, ANTHROPIC_MESSAGES }

sealed class OAuthSpec {
    abstract val clientId: String
    abstract val tokenUrl: String
    abstract val allowedHosts: Set<String>

    data class Device(
        override val clientId: String,
        val deviceUrl: String,
        override val tokenUrl: String,
        val scope: String,
        val extraDeviceFields: Map<String, String> = emptyMap(),
        override val allowedHosts: Set<String>,
    ) : OAuthSpec()

    data class PkcePaste(
        override val clientId: String,
        val authorizeUrl: String,
        override val tokenUrl: String,
        val redirectUri: String,
        val scope: String,
        val extraAuthorize: Map<String, String> = emptyMap(),
        val extraToken: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        override val allowedHosts: Set<String>,
    ) : OAuthSpec()
}

object AiPlatforms {
    fun of(provider: LlmProvider): AiPlatform = all.first { it.provider == provider }

    val all: List<AiPlatform> = listOf(
        AiPlatform(
            provider = LlmProvider.HERMES,
            label = "Hermes",
            apiBase = null,
            defaultModel = "default",
            chatKind = ChatKind.OPENAI_CHAT,
            oauth = null,
        ),
        AiPlatform(
            provider = LlmProvider.XAI,
            label = "xAI",
            apiBase = "https://api.x.ai/v1",
            defaultModel = "grok-4.6",
            chatKind = ChatKind.OPENAI_CHAT,
            oauth = OAuthSpec.Device(
                // Public Grok CLI client id (not a secret). xAI rejects unknown
                // clients and has no self-service app registration.
                clientId = "b1a00492-073a-47ea-816f-4c329264a828",
                deviceUrl = "https://auth.x.ai/oauth2/device/code",
                tokenUrl = "https://auth.x.ai/oauth2/token",
                scope = "openid profile email offline_access grok-cli:access api:access",
                extraDeviceFields = mapOf("referrer" to "builder-launcher"),
                allowedHosts = setOf("auth.x.ai", "accounts.x.ai"),
            ),
        ),
        AiPlatform(
            provider = LlmProvider.OPENAI,
            label = "OpenAI",
            apiBase = "https://api.openai.com/v1",
            defaultModel = "gpt-4o",
            chatKind = ChatKind.OPENAI_CHAT,
            oauth = null,
        ),
        AiPlatform(
            provider = LlmProvider.ANTHROPIC,
            label = "Anthropic",
            apiBase = "https://api.anthropic.com",
            defaultModel = "claude-sonnet-4-5",
            chatKind = ChatKind.ANTHROPIC_MESSAGES,
            oauth = null,
        ),
    )
}
