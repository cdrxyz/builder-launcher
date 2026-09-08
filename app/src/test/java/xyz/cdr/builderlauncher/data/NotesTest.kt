package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class NotesTest {
    private fun note(text: String, id: String = text, createdAt: Long = 0, updatedAt: Long = 0) =
        LocalItem(id = id, kind = "note", text = text, createdAt = createdAt, updatedAt = updatedAt)

    @Test
    fun sortedByDateEditedNewestFirst() {
        val items = listOf(
            note("old", id = "1", createdAt = 10),
            LocalItem("t", "todo", "ignore", createdAt = 99),
            note("mid", id = "2", createdAt = 20, updatedAt = 40),
            note("new", id = "3", createdAt = 5, updatedAt = 80),
        )
        assertEquals(listOf("new", "mid", "old"), Notes.of(items).map { it.text })
    }

    @Test
    fun titleIsFirstNonBlankLineWithoutHeadingMarks() {
        assertEquals("Ship notes", Notes.title("# Ship notes\n\nbody"))
        assertEquals("Ship notes", Notes.title("Ship notes\nmore"))
        assertEquals("untitled", Notes.title("  \n\n"))
    }

    @Test
    fun queryMatchesNotesPrefixesOnly() {
        assertTrue(Notes.matchesQuery("n"))
        assertTrue(Notes.matchesQuery("note"))
        assertTrue(Notes.matchesQuery("notes"))
        assertTrue(Notes.matchesQuery("NOTES"))
        assertFalse(Notes.matchesQuery(""))
        assertFalse(Notes.matchesQuery("hub"))
        assertFalse(Notes.matchesQuery("notesapp"))
    }

    @Test
    fun draftStripsNotePrefix() {
        assertEquals("ship it", Notes.draftFromInput("+ship it"))
        assertEquals("ship it", Notes.draftFromInput("+ ship it"))
        assertEquals("", Notes.draftFromInput("+"))
        assertEquals("", Notes.draftFromInput("+ "))
    }

    @Test
    fun editedLabelIsDayMonthTime() {
        assertEquals(
            "7 Sep 15:42",
            Notes.editedLabel(1_788_795_720_000L, Locale.US, java.util.TimeZone.getTimeZone("UTC")),
        )
    }
}
