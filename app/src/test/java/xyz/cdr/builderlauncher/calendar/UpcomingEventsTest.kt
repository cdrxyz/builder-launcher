package xyz.cdr.builderlauncher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class UpcomingEventsTest {
    private val zone = TimeZone.getTimeZone("America/Toronto")
    private val locale = Locale.US
    private val now = local(2026, Calendar.SEPTEMBER, 9, 10, 0)

    @Test
    fun timedTodayUsesClock() {
        val event = timed(1, "dentist", 2026, Calendar.SEPTEMBER, 9, 15, 0)
        assertEquals("dentist · 15:00", UpcomingEvents.line(event, now, zone, locale))
    }

    @Test
    fun timedLaterDayIncludesWeekday() {
        val event = timed(1, "standup", 2026, Calendar.SEPTEMBER, 10, 9, 30)
        assertEquals("standup · Thu 09:30", UpcomingEvents.line(event, now, zone, locale))
    }

    @Test
    fun allDayTodayIgnoresUtcMidnightOffset() {
        val event = allDay(2, "birthday", 2026, Calendar.SEPTEMBER, 9)
        assertEquals("birthday · today", UpcomingEvents.line(event, now, zone, locale))
    }

    @Test
    fun allDayLaterDayUsesWeekday() {
        val event = allDay(2, "offsite", 2026, Calendar.SEPTEMBER, 11)
        assertEquals("offsite · Fri", UpcomingEvents.line(event, now, zone, locale))
    }

    @Test
    fun pickPrefersTimedOverAllDay() {
        val birthday = allDay(2, "birthday", 2026, Calendar.SEPTEMBER, 9)
        val dentist = timed(1, "dentist", 2026, Calendar.SEPTEMBER, 9, 15, 0)
        assertEquals(dentist, UpcomingEvents.pick(listOf(birthday, dentist), now, zone))
    }

    @Test
    fun pickKeepsInProgressMeeting() {
        val meeting = timed(1, "standup", 2026, Calendar.SEPTEMBER, 9, 9, 30, endHour = 10, endMinute = 30)
        assertEquals(meeting, UpcomingEvents.pick(listOf(meeting), now, zone))
        assertEquals("standup · 09:30", UpcomingEvents.line(meeting, now, zone, locale))
    }

    @Test
    fun pickSkipsEndedMeeting() {
        val done = timed(1, "standup", 2026, Calendar.SEPTEMBER, 9, 9, 0, endHour = 9, endMinute = 30)
        val next = timed(2, "dentist", 2026, Calendar.SEPTEMBER, 9, 15, 0)
        assertEquals(next, UpcomingEvents.pick(listOf(done, next), now, zone))
    }

    @Test
    fun pickFallsBackToAllDay() {
        val birthday = allDay(2, "birthday", 2026, Calendar.SEPTEMBER, 9)
        assertEquals(birthday, UpcomingEvents.pick(listOf(birthday), now, zone))
    }

    @Test
    fun emptyIsNull() {
        assertNull(UpcomingEvents.pick(emptyList(), now, zone))
    }

    private fun timed(
        id: Long,
        title: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        endHour: Int = hour + 1,
        endMinute: Int = minute,
    ) = UpcomingEvent(
        eventId = id,
        title = title,
        begin = local(year, month, day, hour, minute),
        end = local(year, month, day, endHour, endMinute),
        allDay = false,
    )

    private fun allDay(id: Long, title: String, year: Int, month: Int, day: Int): UpcomingEvent {
        val utc = TimeZone.getTimeZone("UTC")
        val begin = GregorianCalendar(utc).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
        return UpcomingEvent(
            eventId = id,
            title = title,
            begin = begin,
            end = begin + java.util.concurrent.TimeUnit.DAYS.toMillis(1),
            allDay = true,
        )
    }

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
