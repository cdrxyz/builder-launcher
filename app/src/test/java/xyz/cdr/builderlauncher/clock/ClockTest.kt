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
    fun parseIntervalAlarmSeries() {
        val advil = Clock.parseAlarmInputs("Advil every 4 hours starting at 8pm")
        assertEquals(
            listOf(0 to 0, 4 to 0, 8 to 0, 12 to 0, 16 to 0, 20 to 0),
            advil.map { it.hour to it.minute },
        )
        assertTrue(advil.all { it.label == "Advil" && it.days.isEmpty() })

        val dose = Clock.parseAlarmInputs("every 6 hours starting at 9:30am ibuprofen")
        assertEquals(
            listOf(3 to 30, 9 to 30, 15 to 30, 21 to 30),
            dose.map { it.hour to it.minute },
        )
        assertTrue(dose.all { it.label == "ibuprofen" })

        val gym = Clock.parseAlarmInputs("Gym every 12 hours from 6am weekdays")
        assertEquals(listOf(6 to 0, 18 to 0), gym.map { it.hour to it.minute })
        assertTrue(gym.all { it.label == "Gym" && it.days == (1..5).toSet() })

        val compact = Clock.parseAlarmInputs("Advil every 4h starting at 20:00")
        assertEquals(
            listOf(0 to 0, 4 to 0, 8 to 0, 12 to 0, 16 to 0, 20 to 0),
            compact.map { it.hour to it.minute },
        )

        assertTrue(Clock.parseAlarmInputs("every 5 hours starting at 8pm").isEmpty())
        assertTrue(Clock.parseAlarmInputs("Advil every 4 hours").isEmpty())
        assertTrue(Clock.parseAlarmInputs("every 4.5 hours starting at 8pm").isEmpty())
        assertEquals(1, Clock.parseAlarmInputs("Take out garbage Wednesdays 10:30pm").size)

        val reordered = Clock.parseAlarmInputs("starting at 8pm every 4 hours Advil")
        assertEquals(
            listOf(0 to 0, 4 to 0, 8 to 0, 12 to 0, 16 to 0, 20 to 0),
            reordered.map { it.hour to it.minute },
        )
        assertTrue(reordered.all { it.label == "Advil" })

        val comma = Clock.parseAlarmInputs("Advil every 4 hours, starting at 8pm")
        assertEquals(6, comma.size)
        assertTrue(comma.all { it.label == "Advil" })

        val daily = Clock.parseAlarmInputs("every 24 hours starting at 8pm")
        assertEquals(listOf(20 to 0), daily.map { it.hour to it.minute })
        assertEquals("", daily.single().label)

        val minutes = Clock.parseAlarmInputs("every 90 minutes starting at 8pm")
        assertEquals(16, minutes.size)
        assertEquals(20 to 0, minutes.first { it.hour == 20 }.let { it.hour to it.minute })
        assertEquals(21 to 30, minutes.first { it.hour == 21 }.let { it.hour to it.minute })
        assertTrue(minutes.all { it.label == "" })

        val attack = Clock.parseAlarmInputs("every 4 hours attack at 9pm")
        assertEquals(6, attack.size)
        assertTrue(attack.all { it.label == "attack" })
        assertTrue(attack.any { it.hour == 21 && it.minute == 0 })

        val fromWork = Clock.parseAlarmInputs("Call mom from work every 4 hours starting at 8pm")
        assertEquals(6, fromWork.size)
        assertTrue(fromWork.all { it.label == "Call mom from work" })

        val starts = Clock.parseAlarmInputs("every 4 hours starts at 9pm")
        assertEquals(6, starts.size)
        assertTrue(starts.all { it.label == "" })
        assertTrue(starts.any { it.hour == 21 && it.minute == 0 })
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
        val now = 3_000_000L
        val snoozed = Clock.snooze(alarm, now)
        assertTrue(Clock.snoozed(snoozed, now))
        assertEquals("snoozed  8 min", Clock.alarmStatus(snoozed, now))
        assertEquals("snoozed  1 min", Clock.alarmStatus(snoozed, now + Clock.SNOOZE_MS - 1_000L))
        assertEquals("on  Wed", Clock.alarmStatus(Clock.acknowledge(snoozed, now), now))
        assertFalse(Clock.snoozed(Clock.acknowledge(snoozed, now), now))
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
        val snoozed = Clock.snooze(ClockAlarm(id = "a", hour = 6, minute = 30), now)
        assertEquals("7:30", Clock.homeClockLabel(idle, listOf(snoozed), now + 30_000, "15:42"))
        assertEquals("0:30", Clock.homeClockLabel(running, listOf(snoozed), now + 30_000, "15:42"))
        val later = Clock.snooze(ClockAlarm(id = "b", hour = 7, minute = 0), now + 60_000)
        assertEquals(snoozed.id, Clock.activeSnooze(listOf(later, snoozed), now)?.id)
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
    fun dismissMarksThisOccurrenceSoCatchUpDoesNotReplayIt() {
        val zone = ZoneId.of("America/Toronto")
        val slot = ZonedDateTime.of(2026, 9, 10, 7, 0, 0, 0, zone)
        val late = slot.plusMinutes(20).toInstant().toEpochMilli()
        val alarm = ClockAlarm(id = "thu", hour = 7, minute = 0, days = setOf(4))
        assertTrue(Clock.catchUpDue(alarm, late, zone))
        val dismissed = Clock.acknowledge(alarm, late)
        assertEquals(late, dismissed.lastFiredAt)
        assertNull(dismissed.snoozeUntil)
        assertFalse(Clock.catchUpDue(dismissed, late, zone))
        assertTrue(Clock.occurrenceHandled(dismissed, late, zone))
        assertFalse(Clock.occurrenceHandled(alarm, late, zone))
        val snoozed = Clock.snooze(dismissed, late)
        assertFalse(Clock.occurrenceHandled(snoozed, late + Clock.SNOOZE_MS, zone))
        assertTrue(Clock.occurrenceHandled(Clock.acknowledge(snoozed, late + Clock.SNOOZE_MS), late + Clock.SNOOZE_MS, zone))
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
