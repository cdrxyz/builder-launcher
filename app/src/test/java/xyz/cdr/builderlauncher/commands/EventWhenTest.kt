package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class EventWhenTest {
    private val noon = GregorianCalendar(2026, Calendar.SEPTEMBER, 7, 12, 0, 0).timeInMillis

    @Test
    fun empty() {
        assertEquals(null, EventWhen.millis("", noon))
    }

    @Test
    fun mar24NineA() {
        val ms = EventWhen.millis("mar 24 9a", noon)
        assertNotNull(ms)
        val cal = Calendar.getInstance().apply { timeInMillis = ms!! }
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(2027, cal.get(Calendar.YEAR))
    }

    @Test
    fun tomorrow() {
        val ms = EventWhen.millis("tomorrow 2pm", noon)!!
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(8, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun tonight() {
        val ms = EventWhen.millis("tonight", noon)!!
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun clockOnlyFuture() {
        val ms = EventWhen.millis("3pm", noon)!!
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
        assertTrue(ms > noon)
    }

    @Test
    fun fridayFromMonday() {
        assertBegin("friday", 2026, Calendar.SEPTEMBER, 11, 9, 0)
    }

    @Test
    fun fridayWithClockAndEnd() {
        val parsed = EventWhen.parse("friday 9a-10:30", noon)!!
        assertClock(parsed.beginMillis, 2026, Calendar.SEPTEMBER, 11, 9, 0)
        assertClock(parsed.endMillis, 2026, Calendar.SEPTEMBER, 11, 10, 30)
    }

    @Test
    fun mondayStillAheadUsesToday() {
        assertBegin("monday 3pm", 2026, Calendar.SEPTEMBER, 7, 15, 0)
    }

    @Test
    fun mondayMorningAlreadyPassedRollsAWeek() {
        assertBegin("mon 9a", 2026, Calendar.SEPTEMBER, 14, 9, 0)
    }

    @Test
    fun nextMondayOnMondaySkipsAWeek() {
        assertBegin("next monday 3pm", 2026, Calendar.SEPTEMBER, 14, 15, 0)
    }

    @Test
    fun nextFridayOnMondayIsThisFriday() {
        assertBegin("next friday", 2026, Calendar.SEPTEMBER, 11, 9, 0)
    }

    @Test
    fun numericDate() {
        assertBegin("9/28 9a", 2026, Calendar.SEPTEMBER, 28, 9, 0)
    }

    @Test
    fun numericDateRollsToNextYear() {
        assertBegin("1/5 9a", 2027, Calendar.JANUARY, 5, 9, 0)
    }

    @Test
    fun isoDateKeepsExplicitYear() {
        assertBegin("2026-10-01 14:00", 2026, Calendar.OCTOBER, 1, 14, 0)
        assertBegin("2020-01-05", 2020, Calendar.JANUARY, 5, 9, 0)
    }

    @Test
    fun explicitMonthYearDoesNotRoll() {
        assertBegin("mar 24 2020", 2020, Calendar.MARCH, 24, 9, 0)
    }

    @Test
    fun invalidDateIsNotATime() {
        assertEquals(null, EventWhen.parse("2/31", noon))
    }

    @Test
    fun splitKeepsWeekdayAndDateOutOfTheTitle() {
        assertEquals("dentist" to "friday 9a", EventWhen.split("dentist friday 9a"))
        assertEquals("standup" to "9/28 9:30", EventWhen.split("standup 9/28 9:30"))
        assertEquals("ship" to "2026-10-01", EventWhen.split("ship 2026-10-01"))
        assertEquals("dentist" to "on next friday", EventWhen.split("dentist on next friday"))
        assertEquals("friday meeting", EventWhen.split("friday meeting").first)
        assertEquals("", EventWhen.split("friday meeting").second)
    }

    private fun assertBegin(raw: String, year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val parsed = EventWhen.parse(raw, noon)!!
        assertClock(parsed.beginMillis, year, month, day, hour, minute)
        assertEquals(parsed.beginMillis + 3_600_000L, parsed.endMillis)
    }

    private fun assertClock(ms: Long, year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(year, cal.get(Calendar.YEAR))
        assertEquals(month, cal.get(Calendar.MONTH))
        assertEquals(day, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(hour, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, cal.get(Calendar.MINUTE))
    }
}
