package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ChatsTest {
    @Test
    fun questionStripsAskPrefix() {
        assertEquals("weather tomorrow", Chats.questionFromInput("?weather tomorrow"))
        assertEquals("weather tomorrow", Chats.questionFromInput("? weather tomorrow"))
        assertEquals("weather tomorrow", Chats.questionFromInput("weather tomorrow"))
        assertEquals("", Chats.questionFromInput("?"))
        assertEquals("", Chats.questionFromInput("? "))
    }

    @Test
    fun titleIsFirstUserLine() {
        val messages = listOf(
            ChatMessage("user", "compare kotlin and rust\nmore"),
            ChatMessage("assistant", "table"),
        )
        assertEquals("compare kotlin and rust", Chats.title(messages))
        assertEquals("untitled", Chats.title(emptyList()))
        assertEquals("untitled", Chats.title(listOf(ChatMessage("assistant", "hi"))))
    }

    @Test
    fun ofDropsEmptyAndSortsNewestFirst() {
        val old = ChatThread("1", createdAt = 10, updatedAt = 10, messages = listOf(ChatMessage("user", "old")))
        val empty = ChatThread("2", createdAt = 99, updatedAt = 99)
        val fresh = ChatThread("3", createdAt = 5, updatedAt = 80, messages = listOf(ChatMessage("user", "new")))
        assertEquals(listOf("new", "old"), Chats.of(listOf(old, empty, fresh)).map { Chats.title(it.messages) })
    }

    @Test
    fun editedLabelIsDayMonthTime() {
        assertEquals(
            "7 Sep 15:42",
            Chats.editedLabel(1_788_795_720_000L, Locale.US, TimeZone.getTimeZone("UTC")),
        )
    }

    @Test
    fun titleTruncatesLongQuestions() {
        val long = "a".repeat(80)
        assertEquals(48, Chats.title(listOf(ChatMessage("user", long))).length)
        assertTrue(Chats.title(listOf(ChatMessage("user", long))).startsWith("aaa"))
    }
}
