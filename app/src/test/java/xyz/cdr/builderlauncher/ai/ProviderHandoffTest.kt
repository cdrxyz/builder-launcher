package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.BuilderSettings

class ProviderHandoffTest {
    @Test
    fun promptPrefersDraftOverLastQuestion() {
        assertEquals("new draft", ProviderHandoff.prompt("? new draft", "old question"))
        assertEquals("new draft", ProviderHandoff.prompt("new draft", "old question"))
        assertEquals("old question", ProviderHandoff.prompt("?", "old question"))
        assertEquals("old question", ProviderHandoff.prompt("  ", " old question "))
        assertEquals("", ProviderHandoff.prompt("?", null))
    }

    @Test
    fun labelsMatchTheConsumerApps() {
        assertEquals("Grok", ProviderHandoff.label(LlmProvider.XAI))
        assertEquals("ChatGPT", ProviderHandoff.label(LlmProvider.OPENAI))
        assertEquals("Claude", ProviderHandoff.label(LlmProvider.ANTHROPIC))
        assertEquals("Hermes", ProviderHandoff.label(LlmProvider.HERMES))
        assertEquals("open Grok", ProviderHandoff.contentDescription(LlmProvider.XAI))
    }

    @Test
    fun grokUrlPrefillsQ() {
        assertEquals(
            "https://grok.com/?q=compare%20kotlin%20and%20rust",
            ProviderHandoff.webUrl(LlmProvider.XAI, "compare kotlin and rust", null),
        )
        assertEquals("ai.x.grok", ProviderHandoff.appPackage(LlmProvider.XAI))
    }

    @Test
    fun chatgptAndClaudePrefillNewChats() {
        assertEquals(
            "https://chatgpt.com/?q=hello",
            ProviderHandoff.webUrl(LlmProvider.OPENAI, "hello", null),
        )
        assertEquals(
            "https://claude.ai/new?q=hello",
            ProviderHandoff.webUrl(LlmProvider.ANTHROPIC, "hello", null),
        )
        assertEquals("com.openai.chatgpt", ProviderHandoff.appPackage(LlmProvider.OPENAI))
        assertEquals("com.anthropic.claude", ProviderHandoff.appPackage(LlmProvider.ANTHROPIC))
    }

    @Test
    fun emptyPromptOpensTheAppWithoutQuery() {
        assertEquals("https://grok.com/", ProviderHandoff.webUrl(LlmProvider.XAI, "", null))
    }

    @Test
    fun hermesOpensWebUiWhenConfigured() {
        val settings = BuilderSettings(
            provider = LlmProvider.HERMES,
            hermesBaseUrl = "http://192.168.1.10:8642",
            hermesWebUrl = "http://192.168.1.10:9119",
        )
        assertEquals(
            "http://192.168.1.10:9119/",
            ProviderHandoff.webUrl(
                LlmProvider.HERMES,
                "secret",
                HermesUrls.openInBrowser(settings)?.trimEnd('/'),
            ),
        )
        assertNull(ProviderHandoff.appPackage(LlmProvider.HERMES))
    }

    @Test
    fun hermesOpensConfiguredBaseAndDoesNotPrefillApi() {
        assertEquals(
            "http://192.168.1.10:8642/",
            ProviderHandoff.webUrl(LlmProvider.HERMES, "secret", "http://192.168.1.10:8642"),
        )
        assertNull(ProviderHandoff.webUrl(LlmProvider.HERMES, "secret", "  "))
        assertNull(ProviderHandoff.appPackage(LlmProvider.HERMES))
        assertNull(ProviderHandoff.appPackage(LlmProvider.HERMES, openHermex = false))
        assertEquals(ProviderHandoff.HERMEX_PACKAGE, ProviderHandoff.appPackage(LlmProvider.HERMES, openHermex = true))
        assertEquals(
            listOf("hermex://new-chat", "hermes-agent://new-chat"),
            ProviderHandoff.HERMEX_DEEP_LINKS,
        )
    }

    @Test
    fun longPromptsAreCappedForTheQueryString() {
        val long = "a".repeat(5000)
        val url = ProviderHandoff.webUrl(LlmProvider.XAI, long, null)!!
        val q = url.substringAfter("q=")
        assertEquals(4000, q.length)
        assertTrue(q.all { it == 'a' })
    }
}
