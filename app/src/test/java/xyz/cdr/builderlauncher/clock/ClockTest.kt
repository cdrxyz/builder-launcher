package xyz.cdr.builderlauncher.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockTest {
    @Test
    fun formatsTimer() {
        assertEquals("5:00", Clock.formatTimer(5 * 60_000L))
        assertEquals("0:01", Clock.formatTimer(1L))
        assertEquals("1:01:01", Clock.formatTimer((3600 + 61) * 1000L))
    }

    @Test
    fun parseTimerMinutesAndClock() {
        assertEquals(5 * 60_000L, Clock.parseTimer("5"))
        assertEquals(90_000L, Clock.parseTimer("1:30"))
        assertEquals(45_000L, Clock.parseTimer("45s"))
        assertNull(Clock.parseTimer("abc"))
        assertNull(Clock.parseTimer("1:99"))
    }

    @Test
    fun parseAlarmTwelveAndTwentyFour() {
        assertEquals(7 to 30, Clock.parseAlarm("7:30"))
        assertEquals(19 to 0, Clock.parseAlarm("7pm"))
        assertEquals(0 to 0, Clock.parseAlarm("12am"))
        assertEquals(12 to 0, Clock.parseAlarm("12:00pm"))
        assertEquals(7 to 30, Clock.parseAlarm("0730"))
        assertNull(Clock.parseAlarm("25:00"))
    }

    @Test
    fun homeClockShowsCountdownWhileTimerRuns() {
        val now = 1_000_000L
        val idle = TimerState(durationMs = 60_000, remainingMs = 60_000)
        assertEquals("15:42", Clock.homeClockLabel(idle, now, "15:42"))
        val running = Clock.start(idle, now)
        assertEquals("0:30", Clock.homeClockLabel(running, now + 30_000, "15:42"))
        val paused = Clock.pause(running, now + 20_000)
        assertEquals("15:42", Clock.homeClockLabel(paused, now + 20_000, "15:42"))
    }

    @Test
    fun startPauseReset() {
        val now = 1_000_000L
        val started = Clock.start(TimerState(durationMs = 60_000, remainingMs = 60_000), now)
        assertTrue(started.running)
        assertEquals(now + 60_000, started.endsAt)
        assertEquals(30_000L, Clock.remainingMs(started, now + 30_000))
        val paused = Clock.pause(started, now + 20_000)
        assertFalse(paused.running)
        assertEquals(40_000L, paused.remainingMs)
        val reset = Clock.reset(paused)
        assertEquals(60_000L, reset.remainingMs)
        assertFalse(reset.running)
    }

    @Test
    fun nextTriggerRollsToTomorrow() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val later = Clock.nextTrigger(15, 0, now, zone = zone)
        val earlier = Clock.nextTrigger(8, 0, now, zone = zone)
        val laterZ = java.time.Instant.ofEpochMilli(later).atZone(zone)
        val earlierZ = java.time.Instant.ofEpochMilli(earlier).atZone(zone)
        assertEquals(15, laterZ.hour)
        assertEquals(8, earlierZ.hour)
        assertEquals(9, earlierZ.dayOfMonth)
    }

    @Test
    fun zoneTimeUsesZoneId() {
        val now = ZonedDateTime.of(2026, 9, 8, 16, 42, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("16:42", Clock.formatZoneTime("UTC", now))
        assertEquals("12:42", Clock.formatZoneTime("America/New_York", now))
    }
}
