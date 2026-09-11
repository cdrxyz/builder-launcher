package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodosTest {
    private fun todo(text: String, id: String = text, completedAt: Long? = null) =
        LocalItem(id = id, kind = "todo", text = text, createdAt = 0, completedAt = completedAt)

    @Test
    fun moreTasksCopyMatchesNotesLink() {
        assertEquals("… more tasks >", HomeTodos.MORE_TASKS)
        assertEquals("tasks", HomeTodos.TITLE)
    }

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
    fun moveOpenPermutesOpenTodosAndLeavesNotesAndDoneInPlace() {
        val items = listOf(
            todo("one"),
            LocalItem("n", "note", "ignore", 0),
            todo("two"),
            todo("done", completedAt = 1),
            todo("three"),
        )
        val moved = HomeTodos.moveOpen(items, 0, 2)
        assertEquals(listOf("two", "ignore", "three", "done", "one"), moved.map { it.text })
        assertEquals(listOf("two", "three", "one"), HomeTodos.open(HomeTodos.of(moved)).map { it.text })
        assertEquals(items, HomeTodos.moveOpen(items, 1, 1))
        assertEquals(items, HomeTodos.moveOpen(items, -1, 0))
        assertEquals(listOf("one", "two", "three"), HomeTodos.preview(HomeTodos.of(items)).map { it.text })
        assertEquals(listOf("two", "three", "one"), HomeTodos.preview(HomeTodos.of(moved)).map { it.text })
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

    @Test
    fun shareMarkdownIsDatedChecklistOfOpenTodos() {
        val items = listOf(
            todo("milk"),
            todo("eggs"),
            todo("bread", completedAt = 1),
        )
        val markdown = HomeTodos.shareMarkdown(HomeTodos.of(items), date = "2026-06-30")
        assertEquals(
            """
            ## 2026-06-30
            - [ ] milk
            - [ ] eggs
            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun shareMarkdownDateIsIsoDay() {
        assertEquals(
            "2026-09-07",
            HomeTodos.shareDate(1_788_739_200_000L, java.util.TimeZone.getTimeZone("UTC")),
        )
    }

    @Test
    fun completedTodayCountsOnlyThisLocalDay() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 9, 7)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val items = listOf(
            todo("today", id = "a", completedAt = start + 1),
            todo("yesterday", id = "b", completedAt = start - 1),
            todo("open"),
            LocalItem("n", "note", "note", 0, completedAt = start + 2),
        )
        assertEquals(1, HomeTodos.completedToday(HomeTodos.of(items), nowMs = now, zone = zone))
    }

    @Test
    fun completedByDayIsNewestFirst() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 9, 7)
        val now = today.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val t0 = today.atStartOfDay(zone).toInstant().toEpochMilli() + 1
        val t1 = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 1
        val todos = listOf(
            todo("a", id = "a", completedAt = t0),
            todo("b", id = "b", completedAt = t1),
            todo("c", id = "c", completedAt = t1 + 2),
        )
        val days = HomeTodos.completedByDay(todos, days = 3, nowMs = now, zone = zone)
        assertEquals(listOf("today", "Sun", "Sat"), days.map { it.label })
        assertEquals(listOf(1, 2, 0), days.map { it.count })
    }
}
