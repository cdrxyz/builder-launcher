package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalListsTest {
    private fun store(): Pair<File, LocalLists> {
        val dir = createTempDirectory("lists").toFile()
        val file = File(dir, "lists.json")
        return file to LocalLists(file)
    }

    @Test
    fun removeRecordsATombstoneThatSurvivesRelaunch() {
        val (file, first) = store()
        val gone = LocalItem(id = "n-gone", kind = "note", text = "gone")
        val keep = LocalItem(id = "t-keep", kind = "todo", text = "keep")
        first.replaceAll(listOf(gone, keep), deleted = emptyList())
        first.remove(gone.id)
        val again = LocalLists(file)
        assertEquals(listOf("keep"), again.items.value.map { it.text })
        assertTrue(again.deletedIds.value.contains(gone.id))
        assertFalse(again.items.value.any { it.id == gone.id })
    }

    @Test
    fun tombstoneHidesACopyLeftInTheFile() {
        val (file, first) = store()
        val gone = LocalItem(id = "n-gone", kind = "note", text = "gone")
        first.replaceAll(listOf(gone), deleted = emptyList())
        val before = file.readText()
        first.remove(gone.id)
        file.writeText(before)
        val again = LocalLists(file)
        assertFalse(again.items.value.any { it.id == gone.id })
        assertTrue(again.deletedIds.value.contains(gone.id))
    }

    @Test
    fun replaceAllKeepsADeletedNoteDeleted() {
        val (file, first) = store()
        val gone = LocalItem(id = "n-gone", kind = "note", text = "gone")
        val keep = LocalItem(id = "n-keep", kind = "note", text = "keep")
        first.replaceAll(listOf(gone, keep), deleted = listOf(gone.id))
        val again = LocalLists(file)
        assertEquals(listOf(keep.id), again.items.value.map { it.id })
        assertTrue(again.deletedIds.value.contains(gone.id))
    }
}
