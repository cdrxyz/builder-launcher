package xyz.cdr.builderlauncher.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.LlmProvider
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ProviderHandoff {
    const val HERMEX_PACKAGE = "com.uzairansar.hermex"

    val HERMEX_PACKAGES = listOf(
        "com.uzairansar.hermex",
        "com.hermex.android",
    )

    val HERMEX_DEEP_LINKS = listOf(
        "hermes-agent://new-chat",
        "hermex://new-chat",
    )

    fun prompt(draft: String, lastUserMessage: String?): String {
        val fromBar = Chats.questionFromInput(draft)
        if (fromBar.isNotBlank()) return fromBar
        return lastUserMessage?.trim().orEmpty()
    }

    fun label(provider: LlmProvider): String = when (provider) {
        LlmProvider.XAI -> "Grok"
        LlmProvider.OPENAI -> "ChatGPT"
        LlmProvider.ANTHROPIC -> "Claude"
        LlmProvider.GEMINI -> "Gemini"
        LlmProvider.OPENROUTER -> "OpenRouter"
        LlmProvider.GROQ -> "Groq"
        LlmProvider.DEEPSEEK -> "DeepSeek"
        LlmProvider.MISTRAL -> "Mistral"
        LlmProvider.LMSTUDIO -> "LM Studio"
        LlmProvider.OLLAMA -> "Ollama"
        LlmProvider.GENERIC -> "OpenAI API"
        LlmProvider.HERMES -> "Hermes"
    }

    fun contentDescription(provider: LlmProvider): String = "open ${label(provider)}"

    fun appPackage(provider: LlmProvider, openHermex: Boolean = false): String? = when (provider) {
        LlmProvider.XAI -> "ai.x.grok"
        LlmProvider.OPENAI -> "com.openai.chatgpt"
        LlmProvider.ANTHROPIC -> "com.anthropic.claude"
        LlmProvider.GEMINI -> "com.google.android.apps.bard"
        LlmProvider.HERMES -> if (openHermex) HERMEX_PACKAGE else null
        else -> null
    }

    fun webUrl(provider: LlmProvider, prompt: String, hermesBaseUrl: String?): String? {
        val q = prompt.take(MAX_QUERY)
        return when (provider) {
            LlmProvider.XAI -> site("https://grok.com/", q)
            LlmProvider.OPENAI -> site("https://chatgpt.com/", q)
            LlmProvider.ANTHROPIC -> site("https://claude.ai/new", q)
            LlmProvider.GEMINI -> site("https://gemini.google.com/app", q)
            LlmProvider.OPENROUTER -> site("https://openrouter.ai/chat", q)
            LlmProvider.HERMES, LlmProvider.LMSTUDIO, LlmProvider.OLLAMA, LlmProvider.GENERIC -> {
                val base = hermesBaseUrl?.trim()?.trimEnd('/').orEmpty()
                if (base.isEmpty()) null else "$base/"
            }
            LlmProvider.GROQ, LlmProvider.DEEPSEEK, LlmProvider.MISTRAL -> null
        }
    }

    fun open(
        context: Context,
        provider: LlmProvider,
        prompt: String,
        hermesBaseUrl: String?,
        openHermex: Boolean = false,
    ): Boolean {
        if (provider == LlmProvider.HERMES && openHermex) {
            if (prompt.isNotBlank()) {
                for (pkg in HERMEX_PACKAGES) {
                    if (shareTo(context, pkg, prompt)) return true
                }
            }
            for (link in HERMEX_DEEP_LINKS) {
                if (view(context, link)) return true
            }
        } else {
            val pkg = appPackage(provider, openHermex)
            if (pkg != null && prompt.isNotBlank() && shareTo(context, pkg, prompt)) {
                return true
            }
        }
        val url = webUrl(provider, prompt, hermesBaseUrl) ?: return false
        return view(context, url)
    }

    private fun shareTo(context: Context, pkg: String, prompt: String): Boolean {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, prompt)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (share.resolveActivity(context.packageManager) == null) return false
        context.startActivity(share)
        return true
    }

    private fun view(context: Context, url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    private fun site(base: String, prompt: String): String {
        if (prompt.isBlank()) return base
        val joiner = if (base.contains('?')) "&" else "?"
        return "$base${joiner}q=${encode(prompt)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private const val MAX_QUERY = 4000
}
