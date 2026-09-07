package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodosTest {
    private fun todo(text: String, id: String = text, completedAt: Long? = null) =
        LocalItem(id = id, kind = "todo", text = text, createdAt = 0, completedAt = completedAt)

    @Test
    fun newestThreeOpenWhenCollapsed() {
        val items = listOf(
            todo("one"),
            LocalItem("n", "note", "ignore", 0),
            todo("two"),
            todo("three"),
            todo("four"),
        )
        val todos = HomeTodos.of(items)
        val open = HomeTodos.open(todos)
        assertEquals(listOf("one", "two", "three", "four"), open.map { it.text })
        assertEquals(listOf("one", "two", "three"), HomeTodos.visibleOpen(open, expanded = false).map { it.text })
        assertTrue(HomeTodos.hasMore(open))
    }

    @Test
    fun allOpenWhenExpanded() {
        val open = listOf(todo("one"), todo("two"), todo("three"), todo("four"))
        assertEquals(open, HomeTodos.visibleOpen(open, expanded = true))
        assertTrue(HomeTodos.hasMore(open))
    }

    @Test
    fun noMoreLinkWhenThreeOrFewerOpen() {
        val open = listOf(todo("one"), todo("two"))
        assertFalse(HomeTodos.hasMore(open))
        assertEquals(open, HomeTodos.visibleOpen(open, expanded = false))
    }

    @Test
    fun completedSitBelowOpenNewestDoneFirst() {
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
    fun completedDoNotCountTowardPreview() {
        val open = listOf(todo("one"), todo("two"))
        val done = listOf(todo("old", completedAt = 1), todo("older", completedAt = 2), todo("oldest", completedAt = 3))
        assertFalse(HomeTodos.hasMore(open))
        assertEquals(open, HomeTodos.visibleOpen(open, expanded = false))
        assertEquals(listOf("oldest", "older", "old"), HomeTodos.completed(done).map { it.text })
    }

    @Test
    fun collapsedHomeCapsCompleted() {
        val done = (1..8).map { todo("d$it", id = "$it", completedAt = it.toLong()) }
        val completed = HomeTodos.completed(done)
        assertEquals(3, HomeTodos.visibleDone(completed, expanded = false).size)
        assertTrue(HomeTodos.hasMoreDone(completed))
        assertEquals(8, HomeTodos.visibleDone(completed, expanded = true).size)
    }
}
