package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodosTest {
    private fun todo(text: String, id: String = text, completedAt: Long? = null) =
        LocalItem(id = id, kind = "todo", text = text, createdAt = 0, completedAt = completedAt)

    @Test
    fun previewIsOpenOnlyCappedAtThree() {
        val items = listOf(
            todo("one"),
            LocalItem("n", "note", "ignore", 0),
            todo("two"),
            todo("three"),
            todo("four"),
            todo("done", completedAt = 1),
        )
        val todos = HomeTodos.of(items)
        val preview = HomeTodos.preview(todos)
        assertEquals(listOf("one", "two", "three"), preview.map { it.text })
        assertTrue(preview.none { it.done })
    }

    @Test
    fun previewStaysEmptyWhenOnlyCompletedExist() {
        val todos = HomeTodos.of(listOf(todo("done", completedAt = 9)))
        assertEquals(emptyList<LocalItem>(), HomeTodos.preview(todos))
        assertEquals(listOf("done"), HomeTodos.completed(todos).map { it.text })
    }

    @Test
    fun fullListKeepsAllOpenThenCompletedNewestFirst() {
        val items = listOf(
            todo("open-new"),
            todo("done-old", completedAt = 10),
            todo("open-older"),
            todo("done-new", completedAt = 50),
            todo("done-mid", completedAt = 30),
        )
        val todos = HomeTodos.of(items)
        assertEquals(listOf("open-new", "open-older"), HomeTodos.open(todos).map { it.text })
        assertEquals(listOf("done-new", "done-mid", "done-old"), HomeTodos.completed(todos).map { it.text })
    }

    @Test
    fun todosScreenOpensInTaskMode() {
        assertEquals("-", HomeTodos.enterDraft())
        assertEquals("-", HomeTodos.keepDraft(""))
        assertEquals("-buy milk", HomeTodos.keepDraft("-buy milk"))
    }

    @Test
    fun leavingTodosClearsBareTaskPrefix() {
        assertEquals("", HomeTodos.leaveDraft("-"))
        assertEquals("", HomeTodos.leaveDraft("  "))
        assertEquals("-buy milk", HomeTodos.leaveDraft("-buy milk"))
    }
}
