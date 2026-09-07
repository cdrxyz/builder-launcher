package xyz.cdr.builderlauncher.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMatchTest {
    private val book = listOf(
        PhoneContact("Jason Lee", "555-0101"),
        PhoneContact("Jay", "555-0102"),
        PhoneContact("Lauren", "555-0199"),
        PhoneContact("Sam Patel", "4165550000"),
    )

    @Test
    fun prefixBeatsContains() {
        val hits = ContactMatch.filter(book, "ja")
        assertEquals(listOf("Jason Lee", "Jay"), hits.map { it.name })
    }

    @Test
    fun wordStart() {
        val hits = ContactMatch.filter(book, "lee")
        assertEquals(listOf("Jason Lee"), hits.map { it.name })
    }

    @Test
    fun digits() {
        val hits = ContactMatch.filter(book, "0199")
        assertEquals(listOf("Lauren"), hits.map { it.name })
    }

    @Test
    fun empty() {
        assertEquals(emptyList<PhoneContact>(), ContactMatch.filter(book, "  "))
    }
}
