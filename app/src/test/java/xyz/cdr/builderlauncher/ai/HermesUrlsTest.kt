package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.LlmProvider

class HermesUrlsTest {
    @Test
    fun webUiPortsAreDetected() {
        assertTrue(HermesUrls.looksLikeWebUi("http://192.168.1.10:9119"))
        assertTrue(HermesUrls.looksLikeWebUi("http://192.168.1.10:8787/"))
        assertTrue(HermesUrls.looksLikeWebUi("http://127.0.0.1:5173"))
        assertFalse(HermesUrls.looksLikeWebUi("http://192.168.1.10:8642"))
        assertFalse(HermesUrls.looksLikeWebUi("https://api.x.ai/v1"))
    }

    @Test
    fun apiBaseKeepsExplicitApiUrl() {
        val s = BuilderSettings(
            provider = LlmProvider.HERMES,
            hermesBaseUrl = "http://192.168.1.10:8642",
            hermesWebUrl = "http://192.168.1.10:9119",
        )
        assertEquals("http://192.168.1.10:8642", HermesUrls.apiBase(s))
        assertEquals("http://192.168.1.10:9119", HermesUrls.webUi(s))
        assertEquals("http://192.168.1.10:9119/", HermesUrls.openInBrowser(s))
    }

    @Test
    fun webUiOnlyDerivesApiPort() {
        val s = BuilderSettings(
            provider = LlmProvider.HERMES,
            hermesWebUrl = "http://10.0.0.8:9119",
        )
        assertEquals("http://10.0.0.8:8642", HermesUrls.apiBase(s))
        assertEquals("http://10.0.0.8:9119", HermesUrls.webUi(s))
    }

    @Test
    fun baseUrlThatIsWebUiIsOpenedAsWebUi() {
        val s = BuilderSettings(
            provider = LlmProvider.HERMES,
            hermesBaseUrl = "http://192.168.1.10:9119",
        )
        assertEquals("http://192.168.1.10:8642", HermesUrls.apiBase(s))
        assertEquals("http://192.168.1.10:9119", HermesUrls.webUi(s))
        assertEquals("http://192.168.1.10:9119/", HermesUrls.openInBrowser(s))
    }

    @Test
    fun placeholderFollowsApiHost() {
        assertEquals(
            "http://10.0.0.5:9119",
            HermesUrls.webUiPlaceholder("http://10.0.0.5:8642"),
        )
        assertEquals(HermesUrls.DEFAULT_WEBUI, HermesUrls.webUiPlaceholder(""))
    }

    @Test
    fun emptySettingsHaveNoOpenUrl() {
        assertNull(HermesUrls.openInBrowser(BuilderSettings(provider = LlmProvider.HERMES)))
    }
}
