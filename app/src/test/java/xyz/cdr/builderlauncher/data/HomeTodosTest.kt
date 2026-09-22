package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodosTest {
    private fun todo(text: String, id: String = text, completedAt: Long? = null, createdAt: Long = 0) =
        LocalItem(id = id, kind = "todo", text = text, createdAt = createdAt, completedAt = completedAt)

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
    fun previewHonorsLimitAndClamps() {
        val todos = HomeTodos.of(
            listOf(todo("one"), todo("two"), todo("three"), todo("four"), todo("five"), todo("six")),
        )
        assertEquals(emptyList<String>(), HomeTodos.preview(todos, 0).map { it.text })
        assertEquals(listOf("one"), HomeTodos.preview(todos, 1).map { it.text })
        assertEquals(listOf("one", "two", "three", "four", "five"), HomeTodos.preview(todos, 5).map { it.text })
        assertEquals(listOf("one", "two", "three", "four", "five"), HomeTodos.preview(todos, 99).map { it.text })
        assertEquals(0, HomeTodos.clampPreview(-4))
        assertEquals(5, HomeTodos.clampPreview(9))
        assertEquals(3, HomeTodos.clampPreview(3))
        assertEquals(0..5, HomeTodos.previewChoices())
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
    fun newestCreatedAtSortsAboveAnAppendedTask() {
        val items = listOf(
            todo("older", createdAt = 10),
            todo("newer", createdAt = 40),
        )
        assertEquals(listOf("newer", "older"), HomeTodos.open(items).map { it.text })
    }

    @Test
    fun moveStampsOrderBelowNowSoALaterAddSortsToTheTop() {
        val items = listOf(
            todo("top", createdAt = 30),
            todo("mid", createdAt = 20),
            todo("low", createdAt = 10),
        )
        val moved = HomeTodos.moveOpen(items, 0, 2, now = 100)
        assertEquals(listOf("mid", "low", "top"), HomeTodos.open(moved).map { it.text })
        assertTrue(moved.filter { !it.done }.all { it.orderedAt == 100L && it.order < 100L })
        val added = moved + todo("fresh", createdAt = 100)
        assertEquals(listOf("fresh", "mid", "low", "top"), HomeTodos.open(added).map { it.text })
    }

    @Test
    fun migrateKeepsAManualLayoutAndLeavesNewestFirstAlone() {
        val manual = listOf(
            todo("mid", createdAt = 20),
            todo("old", createdAt = 10),
            todo("new", createdAt = 40),
        )
        val migrated = HomeTodos.migrateOpenOrder(manual, now = 80)
        assertEquals(listOf("mid", "old", "new"), HomeTodos.open(migrated).map { it.text })
        assertTrue(migrated.filter { !it.done }.all { it.order != 0L && it.orderedAt == 0L })
        val newestFirst = listOf(todo("new", createdAt = 40), todo("old", createdAt = 10))
        assertEquals(newestFirst, HomeTodos.migrateOpenOrder(newestFirst, now = 80))
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
    fun displayOpenKeepsPendingCompletedInPlace() {
        val open = listOf(todo("a"), todo("c"))
        val completed = listOf(todo("b", completedAt = 2), todo("d", completedAt = 3))
        val pending = mapOf("b" to 1)
        assertEquals(
            listOf("a", "b", "c"),
            HomeTodos.displayOpen(open, completed, pending).map { it.text },
        )
        assertEquals(listOf("d"), HomeTodos.displayCompleted(completed, pending.keys).map { it.text })
        assertEquals(
            listOf("a", "b", "c"),
            HomeTodos.preview(open + completed, 3, pending).map { it.text },
        )
    }

    @Test
    fun completeAnimDurationsHoldThenFade() {
        assertEquals(1_000L, HomeTodos.COMPLETE_HOLD_MS)
        assertEquals(280L, HomeTodos.COMPLETE_FADE_MS)
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
    fun tasksPageShowsEditOnOpenAndDeleteOnCompleted() {
        assertTrue(HomeTodos.showEdit(done = false))
        assertFalse(HomeTodos.showDelete(done = false))
        assertFalse(HomeTodos.showEdit(done = true))
        assertTrue(HomeTodos.showDelete(done = true))
    }

    @Test
    fun shareMarkdownDateIsIsoDay() {
        assertEquals(
            "2026-09-07",
            HomeTodos.shareDate(1_788_739_200_000L, java.util.TimeZone.getTimeZone("UTC")),
        )
    }

    @Test
    fun completedTodayCountsOnlyThatLocalDay() {
        val zone = java.time.ZoneOffset.UTC
        val start = HomeTodos.dayStartMs(1_788_739_200_000L, zone)
        val items = listOf(
            todo("today-a", completedAt = start + 1_000L),
            todo("today-b", completedAt = start + 8 * 3_600_000L),
            todo("yesterday", completedAt = start - 1L),
            todo("tomorrow", completedAt = start + HomeTodos.DAY_MS),
            todo("open"),
        )
        assertEquals(2, HomeTodos.completedToday(HomeTodos.of(items), now = start + 12 * 3_600_000L, zone = zone))
        assertEquals("2 completed", HomeTodos.completedLabel(2))
        assertEquals("1 completed", HomeTodos.completedLabel(1))
        assertEquals("4 · Tue 10 Sep", HomeTodos.completedDayLabel(4, "Tue 10 Sep"))
    }

    @Test
    fun completedByDaysFollowsBarStarts() {
        val start = 1_725_667_200_000L
        val starts = (0 until 7).map { start + it * HomeTodos.DAY_MS }
        assertEquals(
            listOf(2, 0, 1, 4, 0, 3, 2),
            HomeTodos.completedByDays(HomeTodos.sampleCompleted(start), starts),
        )
        assertEquals(7, HomeTodos.recentDayStarts(now = start + 3_600_000L, zone = java.time.ZoneOffset.UTC).size)
        assertEquals(start, HomeTodos.recentDayStarts(now = start + 3_600_000L, zone = java.time.ZoneOffset.UTC).last())
    }
}
