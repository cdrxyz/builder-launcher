package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodosTest {
    private fun todo(text: String, id: String = text) =
        LocalItem(id = id, kind = "todo", text = text, createdAt = 0)

    @Test
    fun newestThreeWhenCollapsed() {
        val items = listOf(
            todo("one"),
            LocalItem("n", "note", "ignore", 0),
            todo("two"),
            todo("three"),
            todo("four"),
        )
        val todos = HomeTodos.of(items)
        assertEquals(listOf("one", "two", "three", "four"), todos.map { it.text })
        assertEquals(listOf("one", "two", "three"), HomeTodos.visible(todos, expanded = false).map { it.text })
        assertTrue(HomeTodos.hasMore(todos))
    }

    @Test
    fun allWhenExpanded() {
        val todos = listOf(todo("one"), todo("two"), todo("three"), todo("four"))
        assertEquals(todos, HomeTodos.visible(todos, expanded = true))
        assertTrue(HomeTodos.hasMore(todos))
    }

    @Test
    fun noMoreLinkWhenThreeOrFewer() {
        val todos = listOf(todo("one"), todo("two"))
        assertFalse(HomeTodos.hasMore(todos))
        assertEquals(todos, HomeTodos.visible(todos, expanded = false))
    }
}
