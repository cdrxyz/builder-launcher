package xyz.cdr.builderlauncher.ai

import xyz.cdr.builderlauncher.data.LlmProvider

data class AiPlatform(
    val provider: LlmProvider,
    val label: String,
    val apiBase: String?,
    val defaultModel: String,
    val chatKind: ChatKind,
    val oauth: OAuthSpec? = null,
    val defaultLocalBase: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val keyOptional: Boolean = false,
) {
    val needsBaseUrl: Boolean get() = apiBase == null
}

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

    fun chatRoot(base: String): String {
        val trimmed = base.trim().trimEnd('/')
        if (trimmed.endsWith("/v1") || trimmed.endsWith("/openai")) return trimmed
        return "$trimmed/v1"
    }

    val all: List<AiPlatform> = listOf(
        AiPlatform(
            provider = LlmProvider.HERMES,
            label = "Hermes",
            apiBase = null,
            defaultModel = "default",
            chatKind = ChatKind.OPENAI_CHAT,
            defaultLocalBase = null,
            keyOptional = true,
        ),
        AiPlatform(
            provider = LlmProvider.XAI,
            label = "xAI",
            apiBase = "https://api.x.ai/v1",
            defaultModel = "grok-4.6",
            chatKind = ChatKind.OPENAI_CHAT,
            oauth = OAuthSpec.Device(
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
        ),
        AiPlatform(
            provider = LlmProvider.ANTHROPIC,
            label = "Anthropic",
            apiBase = "https://api.anthropic.com",
            defaultModel = "claude-sonnet-4-5",
            chatKind = ChatKind.ANTHROPIC_MESSAGES,
        ),
        AiPlatform(
            provider = LlmProvider.GEMINI,
            label = "Gemini",
            apiBase = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-2.5-flash",
            chatKind = ChatKind.OPENAI_CHAT,
        ),
        AiPlatform(
            provider = LlmProvider.OPENROUTER,
            label = "OpenRouter",
            apiBase = "https://openrouter.ai/api/v1",
            defaultModel = "openrouter/auto",
            chatKind = ChatKind.OPENAI_CHAT,
            extraHeaders = mapOf(
                "HTTP-Referer" to "https://cdr.xyz",
                "X-Title" to "Builder Launcher",
            ),
        ),
        AiPlatform(
            provider = LlmProvider.GROQ,
            label = "Groq",
            apiBase = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            chatKind = ChatKind.OPENAI_CHAT,
        ),
        AiPlatform(
            provider = LlmProvider.DEEPSEEK,
            label = "DeepSeek",
            apiBase = "https://api.deepseek.com",
            defaultModel = "deepseek-chat",
            chatKind = ChatKind.OPENAI_CHAT,
        ),
        AiPlatform(
            provider = LlmProvider.MISTRAL,
            label = "Mistral",
            apiBase = "https://api.mistral.ai/v1",
            defaultModel = "mistral-small-latest",
            chatKind = ChatKind.OPENAI_CHAT,
        ),
        AiPlatform(
            provider = LlmProvider.LMSTUDIO,
            label = "LM Studio",
            apiBase = null,
            defaultModel = "local-model",
            chatKind = ChatKind.OPENAI_CHAT,
            defaultLocalBase = "http://127.0.0.1:1234/v1",
            keyOptional = true,
        ),
        AiPlatform(
            provider = LlmProvider.OLLAMA,
            label = "Ollama",
            apiBase = null,
            defaultModel = "llama3.2",
            chatKind = ChatKind.OPENAI_CHAT,
            defaultLocalBase = "http://127.0.0.1:11434/v1",
            keyOptional = true,
        ),
        AiPlatform(
            provider = LlmProvider.GENERIC,
            label = "OpenAI API",
            apiBase = null,
            defaultModel = "gpt-4o",
            chatKind = ChatKind.OPENAI_CHAT,
            defaultLocalBase = "https://api.openai.com/v1",
            keyOptional = false,
        ),
    )
}
