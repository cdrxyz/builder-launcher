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

    @Test
    fun fadeGainRisesFromFloorToPeak() {
        assertEquals(0.45f, Clock.fadeGain(0), 0.0001f)
        assertEquals(0.45f, Clock.fadeGain(-1), 0.0001f)
        assertEquals(0.725f, Clock.fadeGain(2_000), 0.0001f)
        assertEquals(1.0f, Clock.fadeGain(4_000), 0.0001f)
        assertEquals(1.0f, Clock.fadeGain(30_000), 0.0001f)
    }

    @Test
    fun mediaStreamWhenAlarmMutedOrPreview() {
        assertTrue(Clock.useMediaStream(0, false))
        assertTrue(Clock.useMediaStream(7, true))
        assertFalse(Clock.useMediaStream(7, false))
    }

    @Test
    fun timerFireStopsAndKeepsDurationForRunAgain() {
        val now = 2_000_000L
        val running = Clock.start(TimerState(durationMs = 90_000, remainingMs = 90_000), now)
        val fired = Clock.fireTimer(running)
        assertFalse(fired.timer.running)
        assertEquals(90_000L, fired.timer.remainingMs)
        assertEquals(ClockAlertKind.TIMER, fired.alert.kind)
        assertEquals(90_000L, fired.alert.durationMs)
        val again = Clock.runAgain(fired.alert, now + 5_000)
        assertTrue(again.running)
        assertEquals(now + 5_000 + 90_000, again.endsAt)
    }

    @Test
    fun alarmSnoozeIsEightMinutes() {
        val now = 3_000_000L
        val alarm = ClockAlarm(id = "a1", hour = 6, minute = 30, label = "up")
        val fired = Clock.fireAlarm(alarm)
        assertEquals(ClockAlertKind.ALARM, fired.alert.kind)
        assertEquals("a1", fired.alert.alarmId)
        assertNull(fired.alarm.snoozeUntil)
        val snoozed = Clock.snooze(fired.alarm, now)
        assertEquals(now + Clock.SNOOZE_MS, snoozed.snoozeUntil)
        assertTrue(snoozed.enabled)
        assertEquals(now + Clock.SNOOZE_MS, Clock.nextFireAt(snoozed, now))
        assertTrue(Clock.nextFireAt(fired.alarm, now) > now)
        assertEquals(now, Clock.nextFireAt(snoozed.copy(snoozeUntil = now), now))
        assertEquals(now, Clock.nextFireAt(snoozed.copy(snoozeUntil = now - 1), now))
    }

    @Test
    fun clockSoundLabelsAndDefault() {
        assertEquals(listOf("pulse", "chime", "bell", "orthodox", "hum", "off"), ClockSound.entries.map { it.label })
        assertEquals(ClockSound.PULSE, ClockSound.parse(null))
        assertEquals(ClockSound.CHIME, ClockSound.parse("chime"))
        assertEquals(ClockSound.PULSE, ClockSound.parse("nope"))
        assertFalse(ClockSound.PULSE.silent)
        assertTrue(ClockSound.OFF.silent)
        ClockSound.entries.filterNot { it.silent }.forEach { sound ->
            assertTrue(ClockTone.loopMs(sound) in ClockTone.MIN_LOOP_MS..ClockTone.MAX_LOOP_MS)
        }
        assertEquals(0L, ClockTone.loopMs(ClockSound.OFF))
    }

    @Test
    fun startDoesNotJumpWhenDisplayClockIsStale() {
        val now = 1_000_000L
        val idle = TimerState(durationMs = 60_000, remainingMs = 60_000)
        val started = Clock.start(idle, now)
        assertEquals(60_000L, Clock.remainingMs(started, now - 15_000))
        assertEquals("1:00", Clock.formatTimer(Clock.remainingMs(started, now - 15_000)))
        assertEquals("1:00", Clock.formatTimer(Clock.remainingMs(started, now)))
        assertEquals("1:00", Clock.homeClockLabel(started, now - 15_000, "15:42"))
    }
}
