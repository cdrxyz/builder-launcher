package xyz.cdr.builderlauncher.data

object HomeTodos {
    const val PREVIEW = 3
    const val MORE_TASKS = "… more tasks >"
    const val TASK_PREFIX = "-"
    const val BACK = "<"
    const val TITLE = "tasks"

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    fun open(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { !it.done }

    fun completed(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

    fun preview(todos: List<LocalItem>): List<LocalItem> =
        open(todos).take(PREVIEW)

    fun completedToday(
        todos: List<LocalItem>,
        nowMs: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Int = completedByDay(todos, days = 1, nowMs = nowMs, zone = zone).firstOrNull()?.count ?: 0

    fun completedOn(todos: List<LocalItem>, dayStartMs: Long, dayEndMs: Long): List<LocalItem> =
        todos.filter { it.done && it.completedAt != null && it.completedAt >= dayStartMs && it.completedAt < dayEndMs }
            .sortedByDescending { it.completedAt ?: 0L }

    fun completedByDay(
        todos: List<LocalItem>,
        days: Int = 7,
        nowMs: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<DayDone> {
        val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        val pattern = if (days > 7) "d MMM" else "EEE"
        val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US)
        return (0 until days).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            DayDone(
                label = if (offset == 0) "today" else date.format(fmt),
                count = completedOn(todos, start, end).size,
            )
        }
    }

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

data class DayDone(val label: String, val count: Int)
