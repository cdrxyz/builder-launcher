package xyz.cdr.builderlauncher.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.LlmProvider
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ProviderHandoff {
    fun prompt(draft: String, lastUserMessage: String?): String {
        val fromBar = Chats.questionFromInput(draft)
        if (fromBar.isNotBlank()) return fromBar
        return lastUserMessage?.trim().orEmpty()
    }

    fun label(provider: LlmProvider): String = when (provider) {
        LlmProvider.XAI -> "Grok"
        LlmProvider.OPENAI -> "ChatGPT"
        LlmProvider.ANTHROPIC -> "Claude"
        LlmProvider.HERMES -> "Hermes"
    }

    fun contentDescription(provider: LlmProvider): String = "open ${label(provider)}"

    fun appPackage(provider: LlmProvider): String? = when (provider) {
        LlmProvider.XAI -> "ai.x.grok"
        LlmProvider.OPENAI -> "com.openai.chatgpt"
        LlmProvider.ANTHROPIC -> "com.anthropic.claude"
        LlmProvider.HERMES -> null
    }

    fun webUrl(provider: LlmProvider, prompt: String, hermesBaseUrl: String?): String? {
        val q = prompt.take(MAX_QUERY)
        return when (provider) {
            LlmProvider.XAI -> site("https://grok.com/", q)
            LlmProvider.OPENAI -> site("https://chatgpt.com/", q)
            LlmProvider.ANTHROPIC -> site("https://claude.ai/new", q)
            LlmProvider.HERMES -> {
                val base = hermesBaseUrl?.trim()?.trimEnd('/').orEmpty()
                if (base.isEmpty()) null else "$base/"
            }
        }
    }

    fun open(
        context: Context,
        provider: LlmProvider,
        prompt: String,
        hermesBaseUrl: String?,
    ): Boolean {
        val pkg = appPackage(provider)
        if (pkg != null && prompt.isNotBlank()) {
            val share = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, prompt)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (share.resolveActivity(context.packageManager) != null) {
                context.startActivity(share)
                return true
            }
        }
        val url = webUrl(provider, prompt, hermesBaseUrl) ?: return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            false
        }
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
