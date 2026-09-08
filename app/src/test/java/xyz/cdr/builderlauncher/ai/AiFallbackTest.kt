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
    }

    @Test
    fun failedDetectsHermesAndTransportErrors() {
        assertTrue(AiFallback.failed("Set a Hermes URL in settings."))
        assertTrue(AiFallback.failed("Could not reach the model."))
        assertTrue(AiFallback.failed("LLM error 503: overloaded"))
        assertTrue(AiFallback.failed(""))
        assertFalse(AiFallback.failed("Here is a short answer."))
        assertFalse(AiFallback.failed("LLM error 400: bad request"))
    }

    @Test
    fun orderTriesHermesThenCloudAccounts() {
        assertEquals(
            listOf(LlmProvider.HERMES, LlmProvider.XAI, LlmProvider.OPENAI, LlmProvider.ANTHROPIC),
            AiFallback.order(LlmProvider.HERMES),
        )
        assertEquals(
            listOf(LlmProvider.XAI, LlmProvider.OPENAI, LlmProvider.ANTHROPIC, LlmProvider.HERMES),
            AiFallback.order(LlmProvider.XAI),
        )
    }
}
