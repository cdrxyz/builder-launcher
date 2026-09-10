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
        assertEquals(8 * 60_000L, Clock.parseTimer("8 minutes"))
        assertEquals(8 * 60_000L, Clock.parseTimer("8 min"))
        assertNull(Clock.parseTimer("abc"))
        assertNull(Clock.parseTimer("1:99"))
    }

    @Test
    fun parseLabeledTimer() {
        val pasta = Clock.parseTimerInput("Pasta 8 minutes")!!
        assertEquals(8 * 60_000L, pasta.durationMs)
        assertEquals("Pasta", pasta.label)
        val reverse = Clock.parseTimerInput("8 minutes Pasta")!!
        assertEquals(8 * 60_000L, reverse.durationMs)
        assertEquals("Pasta", reverse.label)
        val bare = Clock.parseTimerInput("eggs 3")!!
        assertEquals(3 * 60_000L, bare.durationMs)
        assertEquals("eggs", bare.label)
        assertEquals("", Clock.parseTimerInput("5")!!.label)
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
    fun parseLabeledRecurringAlarm() {
        val garbage = Clock.parseAlarmInput("Take out garbage Wednesdays 10:30pm")!!
        assertEquals(22, garbage.hour)
        assertEquals(30, garbage.minute)
        assertEquals("Take out garbage", garbage.label)
        assertEquals(setOf(3), garbage.days)
        val gym = Clock.parseAlarmInput("Gym weekdays 6:30")!!
        assertEquals(6, gym.hour)
        assertEquals(30, gym.minute)
        assertEquals("Gym", gym.label)
        assertEquals((1..5).toSet(), gym.days)
        val plain = Clock.parseAlarmInput("7:30am")!!
        assertEquals(7, plain.hour)
        assertEquals(30, plain.minute)
        assertEquals("", plain.label)
        assertTrue(plain.days.isEmpty())
    }

    @Test
    fun formatDaysAndAlarmStatus() {
        assertEquals("", Clock.formatDays(emptySet()))
        assertEquals("Wed", Clock.formatDays(setOf(3)))
        assertEquals("weekdays", Clock.formatDays((1..5).toSet()))
        assertEquals("weekends", Clock.formatDays(setOf(6, 7)))
        assertEquals("daily", Clock.formatDays((1..7).toSet()))
        val alarm = ClockAlarm(id = "1", hour = 22, minute = 30, label = "Take out garbage", days = setOf(3))
        assertEquals("on  Wed", Clock.alarmStatus(alarm))
        assertEquals("off", Clock.alarmStatus(alarm.copy(enabled = false, days = emptySet())))
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
    fun homeTickSlowsWhenIdle() {
        assertEquals(Clock.TIMER_TICK_MS, Clock.homeTickMs(timerRunning = true, analog = true))
        assertEquals(Clock.ANALOG_TICK_MS, Clock.homeTickMs(timerRunning = false, analog = true))
        assertEquals(Clock.DIGITAL_TICK_MS, Clock.homeTickMs(timerRunning = false, analog = false))
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
    fun nextTriggerSkipsToRequestedWeekday() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val wed = Clock.nextTrigger(22, 30, now, setOf(3), zone = zone)
        val z = java.time.Instant.ofEpochMilli(wed).atZone(zone)
        assertEquals(9, z.dayOfMonth)
        assertEquals(3, z.dayOfWeek.value)
        assertEquals(22, z.hour)
        assertEquals(30, z.minute)
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
    fun thursdayAlarmCatchesUpAfterMissedSevenAm() {
        val zone = ZoneId.of("America/Toronto")
        val slot = ZonedDateTime.of(2026, 9, 10, 7, 0, 0, 0, zone)
        val late = slot.plusMinutes(36).toInstant().toEpochMilli()
        val early = slot.minusMinutes(10).toInstant().toEpochMilli()
        val alarm = ClockAlarm(id = "thu", hour = 7, minute = 0, days = setOf(4))
        assertFalse(Clock.catchUpDue(alarm, early, zone))
        assertTrue(Clock.catchUpDue(alarm, late, zone))
        val next = Clock.nextTrigger(7, 0, late, setOf(4), zone)
        val nextZ = java.time.Instant.ofEpochMilli(next).atZone(zone)
        assertEquals(17, nextZ.dayOfMonth)
        val fired = Clock.fireAlarm(alarm, late)
        assertFalse(Clock.catchUpDue(fired.alarm, late, zone))
        assertFalse(Clock.catchUpDue(alarm.copy(enabled = false), late, zone))
        val evening = slot.plusHours(3).toInstant().toEpochMilli()
        assertFalse(Clock.catchUpDue(alarm, evening, zone))
    }

    @Test
    fun overdueTimerWhenEndIsPast() {
        val now = 5_000_000L
        val running = TimerState(durationMs = 60_000, remainingMs = 60_000, running = true, endsAt = now - 1)
        assertTrue(Clock.overdueTimer(running, now))
        assertFalse(Clock.overdueTimer(running.copy(endsAt = now + 1), now))
        assertFalse(Clock.overdueTimer(running.copy(running = false), now))
    }

    @Test
    fun parseThursdaySevenAm() {
        val parsed = Clock.parseAlarmInput("7am on Thursdays")!!
        assertEquals(7, parsed.hour)
        assertEquals(0, parsed.minute)
        assertEquals(setOf(4), parsed.days)
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
    fun mediaStreamOnlyForPreview() {
        assertFalse(Clock.useMediaStream(false))
        assertTrue(Clock.useMediaStream(true))
    }

    @Test
    fun timerFireStopsAndKeepsDurationForRunAgain() {
        val now = 2_000_000L
        val running = Clock.start(TimerState(durationMs = 90_000, remainingMs = 90_000, label = "Pasta"), now)
        val fired = Clock.fireTimer(running)
        assertFalse(fired.timer.running)
        assertEquals(90_000L, fired.timer.remainingMs)
        assertEquals(ClockAlertKind.TIMER, fired.alert.kind)
        assertEquals(90_000L, fired.alert.durationMs)
        assertEquals("Pasta", fired.alert.label)
        val again = Clock.runAgain(fired.alert, now + 5_000)
        assertTrue(again.running)
        assertEquals("Pasta", again.label)
        assertEquals(now + 5_000 + 90_000, again.endsAt)
    }

    @Test
    fun alarmSnoozeIsEightMinutes() {
        val now = 3_000_000L
        val alarm = ClockAlarm(id = "a1", hour = 6, minute = 30, label = "up")
        val fired = Clock.fireAlarm(alarm, now)
        assertEquals(ClockAlertKind.ALARM, fired.alert.kind)
        assertEquals("a1", fired.alert.alarmId)
        assertNull(fired.alarm.snoozeUntil)
        assertEquals(now, fired.alarm.lastFiredAt)
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
