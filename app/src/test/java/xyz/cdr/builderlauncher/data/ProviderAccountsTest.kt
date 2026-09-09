package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAccountsTest {
    @Test
    fun rememberKeepsEachProviderWhenSwitching() {
        val hermes = BuilderSettings(
            provider = LlmProvider.HERMES,
            hermesBaseUrl = "http://192.168.1.10:8642",
            hermesOpenInHermex = true,
        )
        val grok = hermes.copy(
            provider = LlmProvider.XAI,
            apiKey = "sk-x",
            oauthAccess = "tok",
            oauthRefresh = "ref",
            oauthExpiresAtEpochMs = 9,
            oauthAccount = "you",
        )
        val stored = ProviderAccounts.remember(
            ProviderAccounts.remember(emptyMap(), hermes),
            grok,
        )
        val back = ProviderAccounts.view(stored, grok, LlmProvider.HERMES)
        assertEquals(LlmProvider.HERMES, back.provider)
        assertEquals("http://192.168.1.10:8642", back.hermesBaseUrl)
        assertTrue(back.hermesOpenInHermex)
        val xai = ProviderAccounts.view(stored, back, LlmProvider.XAI)
        assertEquals("sk-x", xai.apiKey)
        assertEquals("tok", xai.oauthAccess)
        assertEquals("you", xai.oauthAccount)
    }

    @Test
    fun connectedListsHermesAndSignedInCloud() {
        val current = BuilderSettings(provider = LlmProvider.HERMES, hermesBaseUrl = "http://10.0.0.2:8642")
        val accounts = mapOf(
            LlmProvider.XAI.name to ProviderAccount(apiKey = "sk-x"),
            LlmProvider.OPENAI.name to ProviderAccount(),
        )
        val connected = ProviderAccounts.connected(accounts, current, nowMs = 0L)
        assertEquals(listOf(LlmProvider.HERMES, LlmProvider.XAI), connected)
        assertTrue(LlmProvider.OPENAI !in connected)
    }
}
