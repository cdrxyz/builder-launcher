package xyz.cdr.builderlauncher.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object UpcomingEvents {
    val LOOKBACK_MS: Long = TimeUnit.HOURS.toMillis(12)
    val HORIZON_MS: Long = TimeUnit.DAYS.toMillis(14)
    private val HOUR_MS: Long = TimeUnit.HOURS.toMillis(1)

    fun pick(
        events: List<UpcomingEvent>,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): UpcomingEvent? {
        val live = events.filter { !ended(it, now, zone) && it.title.isNotBlank() }
        val timed = live.filter { !it.allDay }.minByOrNull { it.begin }
        if (timed != null) return timed
        return live.minByOrNull { sortKey(it, zone) }
    }

    fun line(
        event: UpcomingEvent,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val title = event.title.trim().ifBlank { "event" }
        val dayFmt = SimpleDateFormat("EEE", locale).apply { timeZone = zone }
        val timeFmt = SimpleDateFormat("HH:mm", locale).apply { timeZone = zone }
        val nowCal = Calendar.getInstance(zone).apply { timeInMillis = now }
        val start = eventLocalCal(event, zone)
        val whenText = when {
            event.allDay && sameDay(start, nowCal) -> "today"
            event.allDay -> dayFmt.format(start.time)
            sameDay(start, nowCal) -> timeFmt.format(Date(event.begin))
            else -> "${dayFmt.format(Date(event.begin))} ${timeFmt.format(Date(event.begin))}"
        }
        return "$title · $whenText"
    }

    fun ended(event: UpcomingEvent, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (!event.allDay) {
            val end = if (event.end > event.begin) event.end else event.begin + HOUR_MS
            return end <= now
        }
        val exclusiveEnd = allDayLocalMidnight(event.end.takeIf { it > event.begin } ?: nextUtcDay(event.begin), zone)
        return now >= exclusiveEnd
    }

    private fun sortKey(event: UpcomingEvent, zone: TimeZone): Long =
        if (event.allDay) eventLocalCal(event, zone).timeInMillis else event.begin

    internal fun eventLocalCal(event: UpcomingEvent, zone: TimeZone): Calendar {
        if (!event.allDay) {
            return Calendar.getInstance(zone).apply { timeInMillis = event.begin }
        }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = event.begin }
        return Calendar.getInstance(zone).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }
    }

    private fun allDayLocalMidnight(utcMillis: Long, zone: TimeZone): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance(zone).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun nextUtcDay(begin: Long): Long = begin + TimeUnit.DAYS.toMillis(1)

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
