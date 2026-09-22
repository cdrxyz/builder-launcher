package xyz.cdr.builderlauncher.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object HomeTodos {
    const val PREVIEW = 3
    const val PREVIEW_MIN = 0
    const val PREVIEW_MAX = 5
    const val MORE_TASKS = "… more tasks >"
    const val TASK_PREFIX = "-"
    const val BACK = "<"
    const val TITLE = "tasks"
    const val COMPLETED = "completed"
    const val DAY_MS = 86_400_000L
    const val COMPLETE_HOLD_MS = 1_000L
    const val COMPLETE_FADE_MS = 280L
    const val HOME_SETTING = "Home tasks"
    const val HOME_SETTING_BLURB =
        "Open tasks on home. 0 hides them. … more tasks > still opens the full list."

    fun clampPreview(limit: Int): Int = limit.coerceIn(PREVIEW_MIN, PREVIEW_MAX)

    fun previewChoices(): IntRange = PREVIEW_MIN..PREVIEW_MAX

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    /** Higher is closer to the top. Unset order falls back to createdAt, so a new task sorts above a placed one. */
    fun rank(item: LocalItem): Long = if (item.order != 0L) item.order else item.createdAt

    fun open(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { !it.done }.sortedByDescending { rank(it) }

    fun completed(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

    /** Tasks page: tap open-row text to edit. */
    fun showEdit(done: Boolean): Boolean = !done

    /** Tasks page: delete on completed rows only. */
    fun showDelete(done: Boolean): Boolean = done

    fun dayStartMs(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    fun recentDayStarts(
        now: Long = System.currentTimeMillis(),
        count: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val today = dayStartMs(now, zone)
        return (count - 1 downTo 0).map { today - it * DAY_MS }
    }

    fun completedCount(todos: List<LocalItem>, startMs: Long, endMs: Long): Int =
        completed(todos).count { item ->
            val at = item.completedAt ?: return@count false
            at in startMs until endMs
        }

    fun completedToday(
        todos: List<LocalItem>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val start = dayStartMs(now, zone)
        return completedCount(todos, start, start + DAY_MS)
    }

    fun completedByDays(
        todos: List<LocalItem>,
        dayStarts: List<Long>,
        dayMs: Long = DAY_MS,
    ): List<Int> = dayStarts.map { completedCount(todos, it, it + dayMs) }

    fun completedLabel(count: Int): String =
        if (count == 1) "1 completed" else "$count completed"

    fun completedDayLabel(count: Int, detail: String): String = "$count · $detail"

    fun sampleCompleted(dayStart: Long = 1_725_667_200_000L): List<LocalItem> {
        val perDay = listOf(2, 0, 1, 4, 0, 3, 2)
        var n = 0
        return perDay.flatMapIndexed { day, count ->
            List(count) {
                n += 1
                LocalItem(
                    id = "t$n",
                    kind = "todo",
                    text = "task $n",
                    createdAt = dayStart,
                    completedAt = dayStart + day * DAY_MS + 3_600_000L,
                )
            }
        }
    }

    fun displayOpen(
        open: List<LocalItem>,
        completed: List<LocalItem>,
        pendingIndexById: Map<String, Int>,
    ): List<LocalItem> {
        if (pendingIndexById.isEmpty()) return open
        val shown = open.toMutableList()
        completed
            .filter { it.id in pendingIndexById }
            .sortedBy { pendingIndexById.getValue(it.id) }
            .forEach { item ->
                val at = pendingIndexById.getValue(item.id).coerceIn(0, shown.size)
                shown.add(at, item)
            }
        return shown
    }

    fun displayCompleted(
        completed: List<LocalItem>,
        pendingIds: Set<String>,
    ): List<LocalItem> =
        if (pendingIds.isEmpty()) completed else completed.filterNot { it.id in pendingIds }

    fun preview(
        todos: List<LocalItem>,
        limit: Int = PREVIEW,
        pendingIndexById: Map<String, Int> = emptyMap(),
    ): List<LocalItem> =
        displayOpen(open(todos), completed(todos), pendingIndexById).take(clampPreview(limit))

    fun moveOpen(items: List<LocalItem>, from: Int, to: Int, now: Long = System.currentTimeMillis()): List<LocalItem> {
        val current = open(of(items))
        val moved = ListReorder.move(current, from, to)
        if (moved === current) return items
        val ranked = stampOrder(moved, now)
        var i = 0
        return items.map { item ->
            if (isOpenTodo(item)) ranked[i++] else item
        }
    }

    /**
     * Keep a pre-rank manual layout. Skipped when the list is already newest-first,
     * so a fresh add still sorts by createdAt and lands above this block after sync.
     */
    fun migrateOpenOrder(items: List<LocalItem>, now: Long = System.currentTimeMillis()): List<LocalItem> {
        val open = items.filter { isOpenTodo(it) }
        if (open.any { it.order != 0L } || open.size < 2) return items
        if (open.map { it.id } == open.sortedByDescending { rank(it) }.map { it.id }) return items
        val byId = stampOrder(open, now, orderedAt = 0L).associateBy { it.id }
        return items.map { item -> byId[item.id] ?: item }
    }

    /**
     * Tasks the other side has not placed yet stay at the top, newest first,
     * unless that side already dragged them lower.
     */
    fun alignOpenOrder(left: List<LocalItem>, right: List<LocalItem>, merged: List<LocalItem>): List<LocalItem> {
        val lifted = liftUnseen(left, right, merged)
        val open = lifted.filter { isOpenTodo(it) }.sortedByDescending { rank(it) }
        val rest = lifted.filterNot { isOpenTodo(it) }
        return open + rest
    }

    private fun liftUnseen(left: List<LocalItem>, right: List<LocalItem>, merged: List<LocalItem>): List<LocalItem> {
        val leftIds = left.map { it.id }.toSet()
        val rightIds = right.map { it.id }.toSet()
        val open = merged.filter { isOpenTodo(it) }
        val shared = open.filter { it.id in leftIds && it.id in rightIds }
        val unseen = open.filter { (it.id in leftIds) != (it.id in rightIds) }
        if (shared.isEmpty() || unseen.isEmpty()) return merged
        val maxShared = shared.maxOf { rank(it) }
        val need = unseen
            .filter { it.orderedAt == 0L && rank(it) <= maxShared }
            .sortedBy { it.createdAt }
        if (need.isEmpty()) return merged
        val updates = need.mapIndexed { index, item -> item.id to (maxShared + 1 + index) }.toMap()
        return merged.map { item ->
            val order = updates[item.id] ?: return@map item
            item.copy(order = order)
        }
    }

    fun stampOrder(sequence: List<LocalItem>, now: Long, orderedAt: Long = now): List<LocalItem> {
        if (sequence.isEmpty()) return sequence
        val top = if (now > sequence.size) now - 1 else sequence.size.toLong()
        return sequence.mapIndexed { index, item ->
            item.copy(order = top - index, orderedAt = orderedAt)
        }
    }

    private fun isOpenTodo(item: LocalItem): Boolean =
        item.kind.equals("todo", ignoreCase = true) && !item.done

    fun enterDraft(): String = TASK_PREFIX

    fun keepDraft(input: String): String = input.ifBlank { TASK_PREFIX }

    fun leaveDraft(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.isEmpty() || trimmed == TASK_PREFIX) "" else input
    }

    fun shareDate(now: Long = System.currentTimeMillis(), zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = zone
        return fmt.format(java.util.Date(now))
    }

    fun shareMarkdown(todos: List<LocalItem>, date: String = shareDate()): String {
        val lines = buildList {
            add("## $date")
            open(todos).forEach { add("- [ ] ${it.text}") }
        }
        return lines.joinToString("\n")
    }
}
