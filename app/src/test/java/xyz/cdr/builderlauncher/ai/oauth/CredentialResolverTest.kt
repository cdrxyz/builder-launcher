package xyz.cdr.builderlauncher.ai.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.LlmProvider

class CredentialResolverTest {
    @Test
    fun hermesReadyWithUrlOnly() {
        val s = BuilderSettings(provider = LlmProvider.HERMES, hermesBaseUrl = "http://192.168.1.10:8642")
        assertTrue(CredentialResolver.readyForAsk(s))
        assertNull(CredentialResolver.bearer(s, nowMs = 0L))
    }

    @Test
    fun hermesReadyWithWebUiOnly() {
        val s = BuilderSettings(provider = LlmProvider.HERMES, hermesWebUrl = "http://192.168.1.10:9119")
        assertTrue(CredentialResolver.readyForAsk(s))
    }

    @Test
    fun xaiNeedsLoginOrKey() {
        val empty = BuilderSettings(provider = LlmProvider.XAI)
        assertFalse(CredentialResolver.readyForAsk(empty, nowMs = 0L))
        val keyed = empty.copy(apiKey = "sk-test")
        assertTrue(CredentialResolver.readyForAsk(keyed, nowMs = 0L))
        assertEquals("sk-test", CredentialResolver.bearer(keyed, nowMs = 0L))
    }

    @Test
    fun prefersValidOauthOverKey() {
        val now = 1_000_000L
        val s = BuilderSettings(
            provider = LlmProvider.XAI,
            apiKey = "sk-test",
            oauthAccess = "oauth-token",
            oauthRefresh = "refresh",
            oauthExpiresAtEpochMs = now + 600_000L,
        )
        assertEquals("oauth-token", CredentialResolver.bearer(s, now))
        assertFalse(CredentialResolver.needsRefresh(s, now))
    }

    @Test
    fun expiredOauthFallsBackToKeyUntilRefresh() {
        val now = 1_000_000L
        val s = BuilderSettings(
            provider = LlmProvider.XAI,
            apiKey = "sk-test",
            oauthAccess = "stale",
            oauthRefresh = "refresh",
            oauthExpiresAtEpochMs = now - 1,
        )
        assertTrue(CredentialResolver.needsRefresh(s, now))
        assertEquals("sk-test", CredentialResolver.bearer(s, now))
    }

    @Test
    fun readyWhenRefreshTokenPresent() {
        val s = BuilderSettings(
            provider = LlmProvider.XAI,
            oauthRefresh = "refresh",
            oauthExpiresAtEpochMs = 0L,
        )
        assertTrue(CredentialResolver.readyForAsk(s, nowMs = 50L))
    }
}
