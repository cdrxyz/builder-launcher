package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.LlmProvider

class AiFallbackTest {
    @Test
    fun noticeNamesTheProvider() {
        assertEquals("Fell back to Grok.", AiFallback.notice(LlmProvider.XAI))
        assertEquals("Fell back to ChatGPT.", AiFallback.notice(LlmProvider.OPENAI))
        assertEquals("Fell back to Claude.", AiFallback.notice(LlmProvider.ANTHROPIC))
        assertEquals("Fell back to Gemini.", AiFallback.notice(LlmProvider.GEMINI))
        assertEquals("Fell back to OpenRouter.", AiFallback.notice(LlmProvider.OPENROUTER))
    }

    @Test
    fun failedDetectsHermesAndTransportErrors() {
        assertTrue(AiFallback.failed("Set a Hermes URL in settings."))
        assertTrue(AiFallback.failed("Set a Web UI URL in settings."))
        assertTrue(AiFallback.failed("Set a base URL in settings."))
        assertTrue(AiFallback.failed("Could not reach the model."))
        assertTrue(AiFallback.failed("LLM error 503: overloaded"))
        assertTrue(AiFallback.failed("""LLM error 401: {"error":"Authentication required"}"""))
        assertTrue(AiFallback.failed("Web UI needs a password in settings."))
        assertTrue(AiFallback.failed(""))
        assertFalse(AiFallback.failed("Here is a short answer."))
        assertFalse(AiFallback.failed("LLM error 400: bad request"))
    }

    @Test
    fun orderTriesHermesThenCloudAccounts() {
        val fromHermes = AiFallback.order(LlmProvider.HERMES)
        assertEquals(LlmProvider.HERMES, fromHermes.first())
        assertEquals(LlmProvider.XAI, fromHermes[1])
        assertTrue(fromHermes.contains(LlmProvider.GEMINI))
        assertTrue(fromHermes.contains(LlmProvider.OPENROUTER))
        val fromXai = AiFallback.order(LlmProvider.XAI)
        assertEquals(LlmProvider.XAI, fromXai.first())
        assertFalse(fromXai.contains(LlmProvider.XAI) && fromXai.indexOf(LlmProvider.XAI) != 0)
        assertEquals(1, fromXai.count { it == LlmProvider.XAI })
    }

    @Test
    fun reportPutsPromptBeforeTheError() {
        val report = AiFallback.report(
            to = LlmProvider.XAI,
            from = LlmProvider.HERMES,
            error = """LLM error 401: {"error":"Authentication required"}""",
        )
        assertTrue(report.startsWith(AiFallback.DEBUG_PROMPT))
        assertTrue(report.contains("Fell back to Grok."))
        assertTrue(report.contains("Primary provider: Hermes"))
        assertTrue(report.contains("LLM error 401"))
        val promptAt = report.indexOf(AiFallback.DEBUG_PROMPT)
        val errorAt = report.indexOf("LLM error 401")
        assertTrue(promptAt >= 0 && errorAt > promptAt)
    }

    @Test
    fun headlineShowsOnlyTheFellBackLine() {
        val report = AiFallback.report(
            to = LlmProvider.OPENAI,
            from = LlmProvider.HERMES,
            error = "Could not reach the Web UI.",
        )
        assertEquals("Fell back to ChatGPT.", AiFallback.headline(report))
        assertEquals("Fell back to Grok.", AiFallback.headline("Fell back to Grok."))
    }

    @Test
    fun answerNoticeIncludesPrimaryError() {
        val answer = LlmAnswer(
            text = "42",
            fallbackFrom = LlmProvider.XAI,
            primaryProvider = LlmProvider.HERMES,
            primaryError = "Could not reach the Web UI.",
        )
        val notice = answer.notice!!
        assertEquals("Fell back to Grok.", AiFallback.headline(notice))
        assertTrue(notice.startsWith(AiFallback.DEBUG_PROMPT))
        assertTrue(notice.contains("Could not reach the Web UI."))
    }
}
