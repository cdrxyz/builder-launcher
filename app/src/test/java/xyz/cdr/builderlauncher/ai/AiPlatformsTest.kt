package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.LlmProvider

class AiPlatformsTest {
    @Test
    fun catalogCoversTheRequestedProviders() {
        val labels = AiPlatforms.all.map { it.label }
        assertTrue(labels.containsAll(listOf("Hermes", "xAI", "OpenAI", "Anthropic", "Gemini", "OpenRouter", "LM Studio", "OpenAI API")))
        assertEquals(LlmProvider.entries.toSet(), AiPlatforms.all.map { it.provider }.toSet())
    }

    @Test
    fun chatRootKeepsGeminiAndV1Suffixes() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai",
            AiPlatforms.chatRoot("https://generativelanguage.googleapis.com/v1beta/openai/"),
        )
        assertEquals("http://127.0.0.1:1234/v1", AiPlatforms.chatRoot("http://127.0.0.1:1234/v1"))
        assertEquals("http://192.168.1.10:8642/v1", AiPlatforms.chatRoot("http://192.168.1.10:8642"))
    }

    @Test
    fun localServersDoNotRequireAKey() {
        assertTrue(AiPlatforms.of(LlmProvider.HERMES).keyOptional)
        assertTrue(AiPlatforms.of(LlmProvider.LMSTUDIO).keyOptional)
        assertTrue(AiPlatforms.of(LlmProvider.OLLAMA).keyOptional)
        assertFalse(AiPlatforms.of(LlmProvider.GENERIC).keyOptional)
        assertTrue(AiPlatforms.of(LlmProvider.GENERIC).needsBaseUrl)
        assertFalse(AiPlatforms.of(LlmProvider.GEMINI).needsBaseUrl)
    }
}
