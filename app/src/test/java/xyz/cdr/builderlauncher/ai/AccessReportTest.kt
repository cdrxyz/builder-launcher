package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessReportTest {
    @Test
    fun models200UsesWantedId() {
        val probe = AccessReport.fromModels(
            200,
            """{"data":[{"id":"hermes-agent"},{"id":"gpt-4o"}]}""",
            "hermes-agent",
        )
        assertTrue(probe.ok)
        assertEquals("Access good — hermes-agent", probe.detail)
    }

    @Test
    fun models200NotesMissingWanted() {
        val probe = AccessReport.fromModels(
            200,
            """{"data":[{"id":"gpt-4o"},{"id":"o3"}]}""",
            "grok-4.6",
        )
        assertTrue(probe.ok)
        assertEquals("Access good — 2 models (grok-4.6 not listed)", probe.detail)
    }

    @Test
    fun unauthorizedIsAFailedCheck() {
        val probe = AccessReport.fromModels(401, """{"error":"invalid"}""", "gpt-4o")
        assertFalse(probe.ok)
        assertEquals("Access failed: unauthorized. Check the API key.", probe.detail)
    }

    @Test
    fun webUiOkAndAuthRequiredStillCountAsReachable() {
        assertEquals("Web UI reachable.", AccessReport.fromWebUi(200).detail)
        val gated = AccessReport.fromWebUi(401)
        assertTrue(gated.ok)
        assertEquals("Web UI reachable (sign-in required).", gated.detail)
    }

    @Test
    fun combineJoinsApiAndWebUi() {
        val done = AccessReport.combine(
            Probe(true, "Access good — hermes-agent"),
            Probe(true, "Web UI reachable."),
        )
        assertTrue(done.ok)
        assertEquals("Access good — hermes-agent. Web UI reachable.", done.line)
    }

    @Test
    fun combineFailsIfEitherLegFails() {
        val done = AccessReport.combine(
            Probe(true, "Access good — hermes-agent"),
            Probe(false, "Web UI failed: could not reach the host."),
        )
        assertFalse(done.ok)
        assertEquals("Access good — hermes-agent. Web UI failed: could not reach the host.", done.line)
    }

    @Test
    fun modelIdsReadsOpenAiList() {
        assertEquals(
            listOf("hermes-agent", "gpt-4o"),
            AccessReport.modelIds("""{"object":"list","data":[{"id":"hermes-agent"},{"id":"gpt-4o"}]}"""),
        )
    }
}
