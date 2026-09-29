package xyz.cdr.builderlauncher.commands

import java.util.Calendar
import java.util.Locale

data class ParsedWhen(
    val beginMillis: Long,
    val endMillis: Long,
)

object EventWhen {
    fun split(rest: String): Pair<String, String> {
        val match = WHEN_SUFFIX.find(rest) ?: return rest to ""
        if (match.range.first == 0) return rest to ""
        val whenText = match.groupValues[1].trim()
        val title = rest.substring(0, match.range.first).trim()
        if (title.isEmpty() || whenText.isEmpty()) return rest to ""
        return title to whenText
    }

    fun parse(whenText: String, now: Long = System.currentTimeMillis()): ParsedWhen? {
        val raw = whenText.trim().lowercase(Locale.US).removePrefix("on ").trim()
        if (raw.isEmpty()) return null
        val anchor = dateAnchor(raw)
        val clocks = clocksOutside(raw, anchor?.range)
        val begin = resolveBegin(raw, anchor, clocks.firstOrNull(), now) ?: return null
        val end = resolveEnd(begin, clocks)
        return ParsedWhen(begin, end)
    }

    fun millis(whenText: String, now: Long = System.currentTimeMillis()): Long? =
        parse(whenText, now)?.beginMillis

    private fun resolveBegin(
        raw: String,
        anchor: Anchor?,
        clock: Pair<Int, Int>?,
        now: Long,
    ): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (anchor) {
            is Anchor.Relative -> relative(cal, anchor.word, clock)
            is Anchor.Dated -> {
                val year = if (anchor.explicitYear) anchor.year else cal.get(Calendar.YEAR)
                if (!placeDate(cal, year, anchor.month, anchor.day, anchor.explicitYear, now)) {
                    null
                } else {
                    applyClock(cal, clock)
                    cal.timeInMillis
                }
            }
            is Anchor.Weekday -> weekday(cal, anchor, clock, now)
            null -> clock?.let { clockOnly(cal, it, now) }
        }
    }

    private fun relative(cal: Calendar, word: String, clock: Pair<Int, Int>?): Long {
        when (word) {
            "tonight" -> {
                cal.set(Calendar.HOUR_OF_DAY, clock?.first ?: 20)
                cal.set(Calendar.MINUTE, clock?.second ?: 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            "today" -> applyClock(cal, clock)
            "tomorrow" -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                applyClock(cal, clock)
            }
        }
        return cal.timeInMillis
    }

    private fun weekday(cal: Calendar, anchor: Anchor.Weekday, clock: Pair<Int, Int>?, now: Long): Long {
        val today = cal.get(Calendar.DAY_OF_WEEK)
        var delta = (anchor.dow - today + 7) % 7
        if (anchor.next && delta == 0) delta = 7
        cal.add(Calendar.DAY_OF_YEAR, delta)
        applyClock(cal, clock)
        if (cal.timeInMillis < now - 60_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return cal.timeInMillis
    }

    private fun clockOnly(cal: Calendar, clock: Pair<Int, Int>, now: Long): Long {
        applyClock(cal, clock)
        if (cal.timeInMillis < now - 60_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun resolveEnd(begin: Long, clocks: List<Pair<Int, Int>>): Long {
        val endClock = clocks.getOrNull(1) ?: return begin + HOUR_MS
        val end = Calendar.getInstance().apply { timeInMillis = begin }
        end.set(Calendar.HOUR_OF_DAY, endClock.first)
        end.set(Calendar.MINUTE, endClock.second)
        end.set(Calendar.SECOND, 0)
        end.set(Calendar.MILLISECOND, 0)
        if (end.timeInMillis <= begin) end.add(Calendar.DAY_OF_YEAR, 1)
        return end.timeInMillis
    }

    private fun placeDate(
        cal: Calendar,
        year: Int,
        month: Int,
        day: Int,
        explicitYear: Boolean,
        now: Long,
    ): Boolean {
        if (!setDay(cal, year, month, day)) return false
        if (!explicitYear && cal.timeInMillis < now - 12 * 60 * 60 * 1000L) {
            if (!setDay(cal, year + 1, month, day)) return false
        }
        return true
    }

    private fun setDay(cal: Calendar, year: Int, month: Int, day: Int): Boolean {
        if (month !in Calendar.JANUARY..Calendar.DECEMBER || day !in 1..31) return false
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        if (day > cal.getActualMaximum(Calendar.DAY_OF_MONTH)) return false
        cal.set(Calendar.DAY_OF_MONTH, day)
        return true
    }

    private fun applyClock(cal: Calendar, time: Pair<Int, Int>?) {
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (time == null) {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            return
        }
        cal.set(Calendar.HOUR_OF_DAY, time.first)
        cal.set(Calendar.MINUTE, time.second)
    }

    private fun clocksOutside(raw: String, anchor: IntRange?): List<Pair<Int, Int>> {
        val parts = if (anchor == null) {
            listOf(raw)
        } else {
            listOf(raw.substring(0, anchor.first), raw.substring((anchor.last + 1).coerceAtMost(raw.length)))
        }
        return parts.flatMap { part -> CLOCK.findAll(part).mapNotNull { clockFrom(it) } }
    }

    private fun clockFrom(m: MatchResult): Pair<Int, Int>? {
        val named = m.groupValues[1].lowercase(Locale.US)
        if (named.isNotEmpty()) {
            return if (named == "midnight") 0 to 0 else 12 to 0
        }
        var hour = m.groupValues[2].toIntOrNull() ?: return null
        val minute = m.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null
        val ampm = m.groupValues[4]
        if (ampm.startsWith("p") && hour < 12) hour += 12
        if (ampm.startsWith("a") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun dateAnchor(raw: String): Anchor? {
        RELATIVE.find(raw)?.takeIf { it.range.first == 0 }?.let {
            return Anchor.Relative(it.groupValues[1], it.range)
        }
        ISO.find(raw)?.let { m ->
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt() - 1
            val day = m.groupValues[3].toInt()
            return Anchor.Dated(year, month, day, explicitYear = true, range = m.range)
        }
        NUMERIC.find(raw)?.let { m ->
            val month = m.groupValues[1].toInt() - 1
            val day = m.groupValues[2].toInt()
            val yearToken = m.groupValues[3]
            val year = if (yearToken.isEmpty()) null else expandYear(yearToken.toInt())
            return Anchor.Dated(year ?: 0, month, day, explicitYear = year != null, range = m.range)
        }
        MONTH_DAY.find(raw)?.let { m ->
            val month = monthIndex(m.groupValues[1]) ?: return null
            val day = m.groupValues[2].toInt()
            val yearToken = m.groupValues[3]
            val year = if (yearToken.isEmpty()) null else yearToken.toInt()
            return Anchor.Dated(year ?: 0, month, day, explicitYear = year != null, range = m.range)
        }
        WEEKDAY.find(raw)?.let { m ->
            val dow = weekdayDow(m.groupValues[2]) ?: return null
            return Anchor.Weekday(dow, next = m.groupValues[1] == "next", range = m.range)
        }
        return null
    }

    private fun expandYear(year: Int): Int = if (year < 100) 2000 + year else year

    private fun monthIndex(token: String): Int? {
        val key = token.take(3).lowercase(Locale.US)
        return MONTHS.indexOf(key).takeIf { it >= 0 }
    }

    private fun weekdayDow(token: String): Int? = when (token.take(3)) {
        "mon" -> Calendar.MONDAY
        "tue" -> Calendar.TUESDAY
        "wed" -> Calendar.WEDNESDAY
        "thu" -> Calendar.THURSDAY
        "fri" -> Calendar.FRIDAY
        "sat" -> Calendar.SATURDAY
        "sun" -> Calendar.SUNDAY
        else -> null
    }

    private sealed class Anchor {
        abstract val range: IntRange

        data class Relative(val word: String, override val range: IntRange) : Anchor()
        data class Dated(
            val year: Int,
            val month: Int,
            val day: Int,
            val explicitYear: Boolean,
            override val range: IntRange,
        ) : Anchor()
        data class Weekday(val dow: Int, val next: Boolean, override val range: IntRange) : Anchor()
    }

    private val MONTHS = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )
    private val MONTH_NAME =
        """jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"""
    private val WEEKDAY_NAME =
        """mondays?|mon|tuesdays?|tues|tue|wednesdays?|weds|wed|thursdays?|thurs|thu|fridays?|fri|saturdays?|sat|sundays?|sun"""
    private val NAMED_CLOCK = """noon|midday|midnight"""
    private val CLOCK_BODY = """(?:$NAMED_CLOCK|\d{1,2}(?::\d{2})?\s*(?:a|am|p|pm)?)"""
    private val CLOCK_OPT = """$CLOCK_BODY(?:\s*(?:-|–|—|to)\s*$CLOCK_BODY)?"""
    private val CLOCK_MARKED =
        """(?:\d{1,2}:\d{2}\s*(?:a|am|p|pm)?|\d{1,2}\s*(?:a|am|p|pm)|$NAMED_CLOCK)(?:\s*(?:-|–|—|to)\s*$CLOCK_BODY)?"""
    private val WHEN_SUFFIX = Regex(
        "(?i)(?:^|\\s)(" +
            "(?:on\\s+)?(?:today|tomorrow|tonight)(?:\\s+$CLOCK_OPT)?" +
            "|(?:on\\s+)?(?:next\\s+|this\\s+)?(?:$WEEKDAY_NAME)(?:\\s+$CLOCK_OPT)?" +
            "|(?:on\\s+)?(?:$MONTH_NAME)\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?(?:\\s*,?\\s*\\d{4})?(?:\\s+$CLOCK_OPT)?" +
            "|(?:on\\s+)?\\d{4}-\\d{2}-\\d{2}(?:\\s+$CLOCK_OPT)?" +
            "|(?:on\\s+)?\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?(?:\\s+$CLOCK_OPT)?" +
            "|$CLOCK_MARKED" +
            ")\\s*$",
    )
    private val RELATIVE = Regex("""^(today|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE)
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val MONTH_DAY = Regex(
        """\b($MONTH_NAME)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(\d{4}))?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val WEEKDAY = Regex(
        """\b(?:(next|this)\s+)?($WEEKDAY_NAME)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOCK = Regex(
        """\b(?:(noon|midday|midnight)|(\d{1,2})(?::(\d{2}))?\s*(a|am|p|pm)?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private const val HOUR_MS = 60L * 60L * 1000L
}
