package xyz.cdr.builderlauncher.ai

import xyz.cdr.builderlauncher.data.LlmProvider

data class LlmAnswer(
    val text: String,
    val fallbackFrom: LlmProvider? = null,
) {
    val notice: String?
        get() = fallbackFrom?.let { AiFallback.notice(it) }
}

object AiFallback {
    fun notice(provider: LlmProvider): String =
        "Fell back to ${ProviderHandoff.label(provider)}."

    fun failed(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return t.startsWith("Set a Hermes URL") ||
            t.startsWith("Sign in or paste an API key") ||
            t.startsWith("Could not reach") ||
            t.startsWith("HTTP is only allowed") ||
            t.startsWith("LLM error 5") ||
            t.startsWith("Empty reply")
    }

    fun order(primary: LlmProvider): List<LlmProvider> {
        val rest = listOf(LlmProvider.XAI, LlmProvider.OPENAI, LlmProvider.ANTHROPIC, LlmProvider.HERMES)
            .filter { it != primary }
        return listOf(primary) + rest
    }
}
