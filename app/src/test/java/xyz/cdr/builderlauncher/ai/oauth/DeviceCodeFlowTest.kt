package xyz.cdr.builderlauncher.ai.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.OAuthSpec
import xyz.cdr.builderlauncher.data.LlmProvider

class DeviceCodeFlowTest {
    private val spec = AiPlatforms.of(LlmProvider.XAI).oauth as OAuthSpec.Device

    @Test
    fun requestParsesDeviceCode() {
        val poster = ScriptedPoster(
            FormResponse(
                200,
                """{"device_code":"dev-1","user_code":"WDJB-MJHT","verification_uri":"https://auth.x.ai/device","verification_uri_complete":"https://auth.x.ai/device?user_code=WDJB-MJHT","expires_in":300,"interval":5}""",
            ),
        )
        val pending = DeviceCodeFlow.request(poster, spec)
        assertEquals("dev-1", pending.deviceCode)
        assertEquals("WDJB-MJHT", pending.userCode)
        assertEquals(5, pending.intervalSeconds)
        assertEquals(spec.clientId, poster.lastFields["client_id"])
        assertTrue(poster.lastFields["scope"]!!.contains("api:access"))
        assertEquals("builder-launcher", poster.lastFields["referrer"])
    }

    @Test
    fun pollPendingThenSuccess() {
        val poster = ScriptedPoster(
            FormResponse(400, """{"error":"authorization_pending"}"""),
            FormResponse(200, """{"access_token":"atk","refresh_token":"rtk","expires_in":3600}"""),
        )
        val pending = DeviceCodeFlow.pollOnce(poster, spec, "dev-1", nowMs = 1_000L)
        assertEquals(PollResult.Pending, pending)
        val success = DeviceCodeFlow.pollOnce(poster, spec, "dev-1", nowMs = 1_000L) as PollResult.Success
        assertEquals("atk", success.tokens.accessToken)
        assertEquals("rtk", success.tokens.refreshToken)
        assertEquals(1_000L + 3600_000L, success.tokens.expiresAtEpochMs)
    }

    @Test
    fun pollSlowDownAndDenied() {
        val poster = ScriptedPoster(
            FormResponse(400, """{"error":"slow_down"}"""),
            FormResponse(400, """{"error":"access_denied"}"""),
        )
        assertTrue(DeviceCodeFlow.pollOnce(poster, spec, "dev-1", 0L) is PollResult.SlowDown)
        val failed = DeviceCodeFlow.pollOnce(poster, spec, "dev-1", 0L) as PollResult.Failed
        assertTrue(failed.message.contains("access_denied"))
    }

    @Test
    fun refreshKeepsOldRefreshIfOmitted() {
        val poster = ScriptedPoster(
            FormResponse(200, """{"access_token":"new","expires_in":120}"""),
        )
        val tokens = DeviceCodeFlow.refresh(poster, spec, "old-refresh", nowMs = 50L)
        assertEquals("new", tokens.accessToken)
        assertEquals("old-refresh", tokens.refreshToken)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsDisallowedVerificationUri() {
        val poster = ScriptedPoster(
            FormResponse(
                200,
                """{"device_code":"dev-1","user_code":"X","verification_uri":"https://evil.example/device","expires_in":300,"interval":5}""",
            ),
        )
        DeviceCodeFlow.request(poster, spec)
    }
}

class PkcePasteFlowTest {
    private val spec = OAuthSpec.PkcePaste(
        clientId = "test-client",
        authorizeUrl = "https://auth.example.com/oauth/authorize",
        tokenUrl = "https://auth.example.com/oauth/token",
        redirectUri = "http://localhost:1455/auth/callback",
        scope = "openid",
        allowedHosts = setOf("auth.example.com"),
    )

    @Test
    fun extractBareCode() {
        assertEquals("abc", PkcePasteFlow.extractCode(" abc ").code)
    }

    @Test
    fun extractAnthropicHash() {
        val extracted = PkcePasteFlow.extractCode("abc#state-1")
        assertEquals("abc", extracted.code)
        assertEquals("state-1", extracted.state)
    }

    @Test
    fun extractCallbackUrl() {
        val extracted = PkcePasteFlow.extractCode(
            "http://localhost:1455/auth/callback?code=tok&state=st",
        )
        assertEquals("tok", extracted.code)
        assertEquals("st", extracted.state)
    }

    @Test
    fun beginBuildsAuthorizeUrl() {
        val spec = this.spec
        val session = PkcePasteFlow.begin(spec)
        assertTrue(session.authorizeUrl.startsWith("https://auth.example.com/oauth/authorize?"))
        assertTrue(session.authorizeUrl.contains("code_challenge_method=S256"))
        assertTrue(session.verifier.isNotBlank())
    }

    @Test
    fun completePostsCodeVerifier() {
        val spec = this.spec
        val session = PkcePasteFlow.begin(spec)
        val poster = ScriptedPoster(
            FormResponse(200, """{"access_token":"a","refresh_token":"r","expires_in":60}"""),
        )
        val tokens = PkcePasteFlow.complete(poster, spec, session, "the-code", nowMs = 10L)
        assertEquals("a", tokens.accessToken)
        assertEquals("the-code", poster.lastFields["code"])
        assertEquals(session.verifier, poster.lastFields["code_verifier"])
        assertEquals("authorization_code", poster.lastFields["grant_type"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun completeRejectsMismatchedState() {
        val session = PkcePasteFlow.begin(spec)
        val poster = ScriptedPoster(
            FormResponse(200, """{"access_token":"a"}"""),
        )
        PkcePasteFlow.complete(
            poster,
            spec,
            session,
            "http://localhost:1455/auth/callback?code=tok&state=other",
            nowMs = 10L,
        )
    }
}

private class ScriptedPoster(vararg responses: FormResponse) : FormPoster {
    private val queue = ArrayDeque(responses.toList())
    var lastFields: Map<String, String> = emptyMap()
        private set

    override fun post(url: String, fields: Map<String, String>, headers: Map<String, String>): FormResponse {
        lastFields = fields
        return queue.removeFirst()
    }
}
