package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNotesTest {
    @Test
    fun ofKeepsNotesOnly() {
        val items = listOf(
            LocalItem("1", "note", "ship it", 0),
            LocalItem("2", "todo", "buy milk", 0),
            LocalItem("3", "NOTE", "caps", 0),
        )
        assertEquals(listOf("ship it", "caps"), HomeNotes.of(items).map { it.text })
    }

    @Test
    fun draftHelpers() {
        assertEquals("+", HomeNotes.enterDraft())
        assertEquals("+", HomeNotes.keepDraft(""))
        assertEquals("+idea", HomeNotes.keepDraft("+idea"))
        assertEquals("", HomeNotes.leaveDraft("+"))
        assertEquals("+keep", HomeNotes.leaveDraft("+keep"))
    }

    @Test
    fun titleIsFirstNonBlankLine() {
        assertEquals("heading", HomeNotes.title("heading\nbody"))
        assertEquals("note", HomeNotes.title("  \n"))
    }
}
