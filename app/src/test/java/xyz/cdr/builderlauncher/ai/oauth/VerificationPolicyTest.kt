package xyz.cdr.builderlauncher.ai.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationPolicyTest {
    private val hosts = setOf("auth.x.ai", "accounts.x.ai")

    @Test
    fun httpsAllowedHost() {
        assertTrue(VerificationPolicy.allowed("https://auth.x.ai/oauth2/device/verify", hosts))
        assertTrue(VerificationPolicy.allowed("https://accounts.x.ai/sign-in", hosts))
    }

    @Test
    fun rejectsHttpAndForeignHosts() {
        assertFalse(VerificationPolicy.allowed("http://auth.x.ai/x", hosts))
        assertFalse(VerificationPolicy.allowed("https://evil.example/x", hosts))
        assertFalse(VerificationPolicy.allowed("javascript:alert(1)", hosts))
        assertFalse(VerificationPolicy.allowed("https://notx.ai/", hosts))
        assertFalse(VerificationPolicy.allowed("https://auth.x.ai.evil.com/", hosts))
    }

    @Test
    fun prefersCompleteUriWhenAllowed() {
        val url = VerificationPolicy.openUrl(
            "https://auth.x.ai/device?user_code=ABCD",
            "https://auth.x.ai/device",
            hosts,
        )
        assertEquals("https://auth.x.ai/device?user_code=ABCD", url)
    }

    @Test
    fun dropsDisallowedCompleteUri() {
        val url = VerificationPolicy.openUrl(
            "https://phishing.test/device",
            "https://auth.x.ai/device",
            hosts,
        )
        assertEquals("https://auth.x.ai/device", url)
    }
}
