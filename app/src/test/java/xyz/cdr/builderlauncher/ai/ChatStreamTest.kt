package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamTest {
    @Test
    fun sseDataJoinsDataLines() {
        val frame = """
            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"text":"Hi"}}
        """.trimIndent()
        assertEquals(
            "{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"Hi\"}}",
            ChatStream.sseData(frame),
        )
    }

    @Test
    fun openaiDeltaReadsContent() {
        val data =
            """{"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", ChatStream.openaiDelta(data))
        assertNull(ChatStream.openaiDelta("[DONE]"))
        assertNull(ChatStream.openaiDelta(""))
    }

    @Test
    fun anthropicDeltaReadsText() {
        val data =
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals("Hi", ChatStream.anthropicDelta(data))
        assertNull(ChatStream.anthropicDelta("""{"type":"message_stop"}"""))
    }

    @Test
    fun looksLikeSse() {
        assertTrue(ChatStream.looksLikeSse("data: {}"))
        assertTrue(ChatStream.looksLikeSse("event: ping"))
        assertTrue(ChatStream.looksLikeSse(""))
        assertFalse(ChatStream.looksLikeSse("{\"choices\":[]}"))
    }
}
