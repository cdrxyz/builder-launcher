package xyz.cdr.builderlauncher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable
enum class HabitKind {
    @SerialName("daily") DAILY,
    @SerialName("interval") INTERVAL,
    @SerialName("times") TIMES,
}

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val kind: HabitKind,
    val everyMinutes: Int = 0,
    val times: List<Int> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long = 0,
    val enabled: Boolean = true,
)

data class HabitDraft(
    val name: String,
    val kind: HabitKind,
    val everyMinutes: Int = 0,
    val times: List<Int> = emptyList(),
)

@Serializable
data class HabitMark(
    val id: String,
    val habitId: String,
    val slotAt: Long,
    val done: Boolean,
    val updatedAt: Long,
)

data class DayStat(
    val startMs: Long,
    val completed: Int,
    val due: Int,
) {
    val percent: Int get() = if (due == 0) 0 else (completed * 100) / due
}

data class HabitReminder(
    val habitId: String,
    val slotAt: Long,
    val name: String,
)

object Habits {
    const val DEFAULT_MINUTE = 8 * 60
    const val DAY_MINUTES = 24 * 60
    const val PREVIEW = 3
    const val GRAPH_DAYS = 14
    const val TITLE = "habits"
    const val MORE = "… more habits >"
    const val EMPTY = "No habits yet."
    const val HINT = "stretch daily · daily at noon Vitamin D · water every 4 hours"

    private val INTERVAL = Regex("""(?i)\bevery\s+(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""")
    private val NAMED = Regex("""(?i)(?<![A-Za-z0-9])(noon|midday|midnight)\b""")
    private val AMPM = Regex("""(?i)(?<![A-Za-z0-9])(\d{1,2})(?::([0-5]\d))?\s*(am|pm|a|p)\b""")
    private val COLON = Regex("""(?<![A-Za-z0-9])([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val COMPACT = Regex("""(?<![A-Za-z0-9])(\d{3,4})\b""")
    private val DAILY_WORD = Regex("""(?i)\b(?:daily|every\s+day|everyday)\b""")
    private val FILLER = Regex("""(?i)\b(?:starting at|starts at|from|at|and|daily|every\s+day|everyday)\b|[,]+""")

    fun parse(raw: String, now: Long = System.currentTimeMillis()): HabitDraft? {
        val text = raw.trim().replace(Regex("""\s+"""), " ")
        if (text.isEmpty()) return null
        val interval = INTERVAL.find(text)
        if (interval != null) {
            val count = interval.groupValues[1].toIntOrNull() ?: return null
            val unit = interval.groupValues[2].lowercase()
            val minutes = if (unit.startsWith("h")) count * 60 else count
            if (minutes <= 0 || minutes > DAY_MINUTES || DAY_MINUTES % minutes != 0) return null
            val start = clockHits(text).firstOrNull { it.range.first > interval.range.last }?.minute ?: DEFAULT_MINUTE
            val name = cleanName(text.removeRange(interval.range))
            if (name.isEmpty()) return null
            return HabitDraft(name, HabitKind.INTERVAL, minutes, listOf(start))
        }
        val times = clockHits(text).map { it.minute }
        if (times.size >= 2) {
            val name = cleanName(text)
            if (name.isEmpty()) return null
            return HabitDraft(name, HabitKind.TIMES, 0, times.distinct().sorted())
        }
        if (DAILY_WORD.containsMatchIn(text) || times.size == 1) {
            val name = cleanName(text)
            if (name.isEmpty()) return null
            return HabitDraft(name, HabitKind.DAILY, 0, listOf(times.singleOrNull() ?: DEFAULT_MINUTE))
        }
        return null
    }

    fun phrase(draft: HabitDraft): String = when (draft.kind) {
        HabitKind.DAILY -> {
            val minute = draft.times.firstOrNull() ?: DEFAULT_MINUTE
            if (minute == DEFAULT_MINUTE) "${draft.name} daily" else "${draft.name} daily at ${formatMinute(minute)}"
        }
        HabitKind.INTERVAL -> {
            val every = if (draft.everyMinutes % 60 == 0) {
                "every ${draft.everyMinutes / 60} hours"
            } else {
                "every ${draft.everyMinutes} minutes"
            }
            val start = draft.times.firstOrNull() ?: DEFAULT_MINUTE
            if (start == DEFAULT_MINUTE) "${draft.name} $every" else "${draft.name} $every starting at ${formatMinute(start)}"
        }
        HabitKind.TIMES -> "${draft.name} ${draft.times.joinToString(" and ") { formatMinute(it) }}"
    }

    fun phrase(habit: Habit): String = phrase(HabitDraft(habit.name, habit.kind, habit.everyMinutes, habit.times))

    fun applyEdit(habit: Habit, draft: HabitDraft, now: Long): Habit = habit.copy(
        name = draft.name,
        kind = draft.kind,
        everyMinutes = draft.everyMinutes,
        times = draft.times,
        updatedAt = now,
    )

    fun dayStart(epoch: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun plusDays(dayStart: Long, days: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(dayStart).atZone(zone).plusDays(days.toLong()).toInstant().toEpochMilli()

    fun slotsOnDay(habit: Habit, dayStart: Long, zone: ZoneId): List<Long> {
        if (!habit.enabled) return emptyList()
        val end = plusDays(dayStart, 1, zone)
        if (habit.createdAt >= end) return emptyList()
        return when (habit.kind) {
            HabitKind.DAILY -> listOf(dayStart)
            HabitKind.TIMES -> minutesOn(habit.times, dayStart).filter { it >= habit.createdAt }
            HabitKind.INTERVAL -> minutesOn(intervalMinutes(habit), dayStart).filter { it >= habit.createdAt }
        }
    }

    fun openSlot(habit: Habit, now: Long, zone: ZoneId): Long? {
        val slots = slotsOnDay(habit, dayStart(now, zone), zone)
        if (slots.isEmpty()) return null
        if (habit.kind == HabitKind.DAILY) return slots.single()
        return slots.lastOrNull { it <= now } ?: slots.firstOrNull { it > now }
    }

    fun markId(habitId: String, slotAt: Long): String = "$habitId:$slotAt"

    fun done(marks: List<HabitMark>, habitId: String, slotAt: Long): Boolean =
        marks.any { it.habitId == habitId && it.slotAt == slotAt && it.done }

    fun toggle(marks: List<HabitMark>, habitId: String, slotAt: Long, now: Long): List<HabitMark> {
        val id = markId(habitId, slotAt)
        val prev = marks.find { it.id == id }
        val next = HabitMark(id, habitId, slotAt, done = prev?.done != true, updatedAt = now)
        return marks.filterNot { it.id == id } + next
    }

    fun graph(habits: List<Habit>, marks: List<HabitMark>, dayStarts: List<Long>, zone: ZoneId): List<DayStat> =
        dayStarts.map { day ->
            var due = 0
            var completed = 0
            habits.forEach { habit ->
                slotsOnDay(habit, day, zone).forEach { slot ->
                    due++
                    if (done(marks, habit.id, slot)) completed++
                }
            }
            DayStat(day, completed, due)
        }

    fun recentDayStarts(now: Long = System.currentTimeMillis(), days: Int = GRAPH_DAYS, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val today = dayStart(now, zone)
        return (days - 1 downTo 0).map { plusDays(today, -it, zone) }
    }

    fun dayLabel(stat: DayStat): String = "${stat.completed} of ${stat.due} · ${stat.percent}%"

    fun pageAfterSwipe(page: Int, dx: Float, threshold: Float): Int = when {
        dx <= -threshold -> 1
        dx >= threshold -> 0
        else -> page
    }

    fun reminderAt(habit: Habit, slotAt: Long, zone: ZoneId): Long = when (habit.kind) {
        HabitKind.DAILY -> dayStart(slotAt, zone) + (habit.times.firstOrNull() ?: DEFAULT_MINUTE) * 60_000L
        else -> slotAt
    }

    fun dueReminder(
        habits: List<Habit>,
        marks: List<HabitMark>,
        now: Long,
        reminded: Set<String>,
        zone: ZoneId,
    ): HabitReminder? {
        habits.filter { it.enabled }.forEach { habit ->
            val slot = openSlot(habit, now, zone) ?: return@forEach
            if (done(marks, habit.id, slot)) return@forEach
            if (now < reminderAt(habit, slot, zone)) return@forEach
            val key = markId(habit.id, slot)
            if (key in reminded) return@forEach
            return HabitReminder(habit.id, slot, habit.name)
        }
        return null
    }

    fun nextReminderAt(habits: List<Habit>, marks: List<HabitMark>, now: Long, zone: ZoneId): Long? {
        var next: Long? = null
        val today = dayStart(now, zone)
        habits.filter { it.enabled }.forEach { habit ->
            listOf(today, plusDays(today, 1, zone)).forEach { day ->
                slotsOnDay(habit, day, zone).forEach { slot ->
                    if (done(marks, habit.id, slot)) return@forEach
                    val at = reminderAt(habit, slot, zone)
                    if (at <= now) {
                        if (openSlot(habit, now, zone) == slot) next = now
                        return@forEach
                    }
                    if (next == null || at < next!!) next = at
                }
            }
        }
        return next
    }

    fun scheduleLabel(habit: Habit): String = when (habit.kind) {
        HabitKind.DAILY -> {
            val minute = habit.times.firstOrNull() ?: DEFAULT_MINUTE
            if (minute == DEFAULT_MINUTE) "daily" else "daily at ${formatMinute(minute)}"
        }
        HabitKind.INTERVAL -> {
            if (habit.everyMinutes % 60 == 0) "every ${habit.everyMinutes / 60} hours" else "every ${habit.everyMinutes} minutes"
        }
        HabitKind.TIMES -> habit.times.joinToString(" and ") { formatMinute(it) }
    }

    fun formatMinute(minute: Int): String {
        val h24 = minute / 60
        val m = minute % 60
        val ap = if (h24 < 12) "am" else "pm"
        val h = if (h24 % 12 == 0) 12 else h24 % 12
        return if (m == 0) "$h$ap" else "$h:${m.toString().padStart(2, '0')}$ap"
    }

    fun mergeHabits(a: List<Habit>, b: List<Habit>): List<Habit> {
        val map = LinkedHashMap<String, Habit>()
        (a + b).forEach { item ->
            val prev = map[item.id]
            map[item.id] = if (prev == null) item else mergeHabit(prev, item)
        }
        return map.values.toList()
    }

    fun mergeHabit(a: Habit, b: Habit): Habit {
        val au = maxOf(a.updatedAt, a.createdAt)
        val bu = maxOf(b.updatedAt, b.createdAt)
        return when {
            au != bu -> if (au > bu) a else b
            !a.enabled && b.enabled -> a
            !b.enabled && a.enabled -> b
            else -> b
        }
    }

    fun mergeMarks(a: List<HabitMark>, b: List<HabitMark>): List<HabitMark> {
        val map = LinkedHashMap<String, HabitMark>()
        (a + b).forEach { item ->
            val prev = map[item.id]
            map[item.id] = if (prev == null || item.updatedAt >= prev.updatedAt) item else prev
        }
        return map.values.toList()
    }

    private fun intervalMinutes(habit: Habit): List<Int> {
        val step = habit.everyMinutes
        if (step <= 0) return emptyList()
        val phase = (habit.times.firstOrNull() ?: DEFAULT_MINUTE).mod(step)
        return generateSequence(phase) { it + step }.takeWhile { it < DAY_MINUTES }.toList()
    }

    private fun minutesOn(minutes: List<Int>, dayStart: Long): List<Long> =
        minutes.map { dayStart + it * 60_000L }

    private data class ClockHit(val minute: Int, val range: IntRange)

    private fun clockHits(text: String): List<ClockHit> {
        val found = mutableListOf<ClockHit>()
        NAMED.findAll(text).forEach { match ->
            val minute = if (match.groupValues[1].equals("midnight", ignoreCase = true)) 0 else 12 * 60
            found += ClockHit(minute, match.range)
        }
        AMPM.findAll(text).forEach { match ->
            ampmMinute(match)?.let { found += ClockHit(it, match.range) }
        }
        COLON.findAll(text).forEach { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) found += ClockHit(hour * 60 + minute, match.range)
        }
        COMPACT.findAll(text).forEach { match ->
            compactMinute(match.value)?.let { found += ClockHit(it, match.range) }
        }
        return found
            .sortedWith(compareBy<ClockHit> { it.range.first }.thenByDescending { it.range.last })
            .fold(mutableListOf()) { kept, hit ->
                if (kept.none { it.range.first <= hit.range.last && hit.range.first <= it.range.last }) kept += hit
                kept
            }
    }

    private fun ampmMinute(match: MatchResult): Int? {
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 1..12 || minute !in 0..59) return null
        if (hour == 12) hour = 0
        if (match.groupValues[3].startsWith("p", ignoreCase = true)) hour += 12
        return hour * 60 + minute
    }

    private fun compactMinute(raw: String): Int? {
        val hour = if (raw.length == 3) raw.take(1).toInt() else raw.take(2).toInt()
        val minute = raw.takeLast(2).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun cleanName(raw: String): String {
        var text = raw
        clockHits(text).sortedByDescending { it.range.first }.forEach { hit ->
            text = text.removeRange(hit.range)
        }
        return text.replace(FILLER, " ").replace(Regex("""\s+"""), " ").trim()
    }
}
