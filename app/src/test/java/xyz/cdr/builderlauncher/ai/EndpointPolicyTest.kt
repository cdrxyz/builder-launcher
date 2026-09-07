package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun httpsAnywhere() {
        assertTrue(EndpointPolicy.allowed("https://api.x.ai/v1"))
    }

    @Test
    fun httpLan() {
        assertTrue(EndpointPolicy.allowed("http://192.168.1.10:8642"))
        assertTrue(EndpointPolicy.allowed("http://10.0.0.5:8642"))
        assertTrue(EndpointPolicy.allowed("http://localhost:8642"))
    }

    @Test
    fun httpPublicBlocked() {
        assertFalse(EndpointPolicy.allowed("http://example.com/v1"))
        assertFalse(EndpointPolicy.allowed("http://8.8.8.8"))
    }
}
