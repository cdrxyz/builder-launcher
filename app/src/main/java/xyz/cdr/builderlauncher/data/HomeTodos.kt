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
    const val HOME_SETTING = "Home tasks"
    const val HOME_SETTING_BLURB =
        "Open tasks on home. 0 hides them. … more tasks > still opens the full list."

    fun clampPreview(limit: Int): Int = limit.coerceIn(PREVIEW_MIN, PREVIEW_MAX)

    fun previewChoices(): IntRange = PREVIEW_MIN..PREVIEW_MAX

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    fun open(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { !it.done }

    fun completed(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

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

    fun preview(todos: List<LocalItem>, limit: Int = PREVIEW): List<LocalItem> =
        open(todos).take(clampPreview(limit))

    fun moveOpen(items: List<LocalItem>, from: Int, to: Int): List<LocalItem> {
        val current = open(of(items))
        val moved = ListReorder.move(current, from, to)
        if (moved === current) return items
        var i = 0
        return items.map { item ->
            if (item.kind.equals("todo", ignoreCase = true) && !item.done) moved[i++] else item
        }
    }

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
