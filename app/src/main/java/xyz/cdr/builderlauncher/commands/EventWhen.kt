package xyz.cdr.builderlauncher.commands

import java.util.Calendar
import java.util.Locale

object EventWhen {
    fun millis(whenText: String, now: Long = System.currentTimeMillis()): Long? {
        val raw = whenText.trim().lowercase(Locale.US)
        if (raw.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val time = parseClock(raw)
        when {
            raw.startsWith("tonight") -> {
                cal.set(Calendar.HOUR_OF_DAY, time?.first ?: 20)
                cal.set(Calendar.MINUTE, time?.second ?: 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
            raw.startsWith("today") -> {
                applyClock(cal, time)
                return cal.timeInMillis
            }
            raw.startsWith("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                applyClock(cal, time)
                return cal.timeInMillis
            }
        }
        val monthDay = MONTH_DAY.find(raw) ?: return clockOnly(cal, time, now)
        val month = monthIndex(monthDay.groupValues[1]) ?: return clockOnly(cal, time, now)
        val day = monthDay.groupValues[2].toInt()
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        if (cal.timeInMillis < now - 12 * 60 * 60 * 1000L) {
            cal.add(Calendar.YEAR, 1)
        }
        applyClock(cal, parseClock(raw.substring(monthDay.range.last + 1)) ?: time)
        return cal.timeInMillis
    }

    private fun clockOnly(cal: Calendar, time: Pair<Int, Int>?, now: Long): Long? {
        if (time == null) return null
        applyClock(cal, time)
        if (cal.timeInMillis < now - 60_000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
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

    private fun parseClock(raw: String): Pair<Int, Int>? {
        val m = CLOCK.find(raw) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ampm = m.groupValues[3]
        if (ampm.startsWith("p") && hour < 12) hour += 12
        if (ampm.startsWith("a") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun monthIndex(token: String): Int? {
        val key = token.take(3).lowercase(Locale.US)
        return MONTHS.indexOf(key).takeIf { it >= 0 }
    }

    private val MONTHS = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )
    private val MONTH_DAY = Regex(
        """\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?\s+(\d{1,2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOCK = Regex(
        """\b(\d{1,2})(?::(\d{2}))?\s*(a|am|p|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )
}
