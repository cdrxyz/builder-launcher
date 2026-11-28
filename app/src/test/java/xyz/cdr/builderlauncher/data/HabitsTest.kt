package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitsTest {
    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 11, 15)
    private val morning = day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parsesDailyEveryFourHoursAndTwoClockTimes() {
        val daily = Habits.parse("stretch daily", morning)!!
        assertEquals("stretch", daily.name)
        assertEquals(HabitKind.DAILY, daily.kind)
        assertEquals(8 * 60, daily.times.single())

        val timed = Habits.parse("vitamins daily at 9am", morning)!!
        assertEquals("vitamins", timed.name)
        assertEquals(HabitKind.DAILY, timed.kind)
        assertEquals(9 * 60, timed.times.single())

        val water = Habits.parse("water every 4 hours", morning)!!
        assertEquals("water", water.name)
        assertEquals(HabitKind.INTERVAL, water.kind)
        assertEquals(4 * 60, water.everyMinutes)
        assertEquals(8 * 60, water.times.single())

        val started = Habits.parse("water every 4 hours starting at 8am", morning)!!
        assertEquals(8 * 60, started.times.single())

        val meds = Habits.parse("meds 7am and 7pm daily", morning)!!
        assertEquals("meds", meds.name)
        assertEquals(HabitKind.TIMES, meds.kind)
        assertEquals(listOf(7 * 60, 19 * 60), meds.times)

        assertNull(Habits.parse("every 5 hours starting at 8am", morning))
        assertNull(Habits.parse("daily", morning))
    }

    @Test
    fun parsesNamedClocksInEitherOrder() {
        val noon = Habits.parse("daily at noon Vitamin D", morning)!!
        assertEquals("Vitamin D", noon.name)
        assertEquals(HabitKind.DAILY, noon.kind)
        assertEquals(12 * 60, noon.times.single())

        val reversed = Habits.parse("Vitamin D at noon", morning)!!
        assertEquals("Vitamin D", reversed.name)
        assertEquals(12 * 60, reversed.times.single())

        val midnight = Habits.parse("every day at midnight stretch", morning)!!
        assertEquals("stretch", midnight.name)
        assertEquals(0, midnight.times.single())

        val shorthand = Habits.parse("vitamins daily at 9a", morning)!!
        assertEquals(9 * 60, shorthand.times.single())

        val compact = Habits.parse("meds daily at 0730", morning)!!
        assertEquals(7 * 60 + 30, compact.times.single())

        val started = Habits.parse("water every 4 hours starting at noon", morning)!!
        assertEquals(12 * 60, started.times.single())

        val both = Habits.parse("meds noon and midnight", morning)!!
        assertEquals(HabitKind.TIMES, both.kind)
        assertEquals(listOf(0, 12 * 60), both.times)
        assertEquals("meds", both.name)

        assertNull(Habits.parse("at noon", morning))
        assertEquals("afternoon walk", Habits.parse("afternoon walk daily", morning)!!.name)
    }

    @Test
    fun phraseRoundTripsAndEditKeepsTheId() {
        val draft = Habits.parse("daily at noon Vitamin D", morning)!!
        val again = Habits.parse(Habits.phrase(draft), morning)!!
        assertEquals(draft.name, again.name)
        assertEquals(draft.kind, again.kind)
        assertEquals(draft.times, again.times)

        val saved = habit(draft, id = "d", createdAt = morning)
        val edited = Habits.applyEdit(saved, Habits.parse("Vitamin D daily at 9pm", morning)!!, morning + 5)
        assertEquals("d", edited.id)
        assertEquals(morning, edited.createdAt)
        assertEquals(21 * 60, edited.times.single())
        assertEquals(morning + 5, edited.updatedAt)
        assertTrue(edited.enabled)
    }

    @Test
    fun openSlotIsTheCurrentWindowAndACheckClearsIt() {
        val meds = habit(Habits.parse("meds 7am and 7pm", morning)!!, createdAt = morning - 86_400_000L)
        val beforeSeven = Habits.dayStart(morning, zone) + 6 * 3_600_000L
        val slot = Habits.openSlot(meds, beforeSeven, zone)!!
        assertEquals(Habits.dayStart(morning, zone) + 7 * 3_600_000L, slot)

        val checked = Habits.toggle(emptyList(), meds.id, slot, beforeSeven)
        assertTrue(Habits.done(checked, meds.id, slot))
        assertNull(Habits.dueReminder(listOf(meds), checked, beforeSeven + 3_600_000L, emptySet(), zone))

        val cleared = Habits.toggle(checked, meds.id, slot, beforeSeven + 1)
        assertFalse(Habits.done(cleared, meds.id, slot))
    }

    @Test
    fun graphCountsCompletedSlotsAgainstTheDayTotal() {
        val day = Habits.dayStart(morning, zone)
        val stretch = habit(Habits.parse("stretch daily", morning)!!, createdAt = day - 86_400_000L)
        val meds = habit(Habits.parse("meds 7am and 7pm", morning)!!, id = "meds", createdAt = day - 86_400_000L)
        val slots = Habits.slotsOnDay(meds, day, zone)
        assertEquals(2, slots.size)
        val marks = Habits.toggle(emptyList(), stretch.id, day, morning)
        val stats = Habits.graph(listOf(stretch, meds), marks, listOf(day), zone)
        assertEquals(1, stats.single().completed)
        assertEquals(3, stats.single().due)
        assertEquals(33, stats.single().percent)
        assertEquals("1 of 3 · 33%", Habits.dayLabel(stats.single()))
    }

    @Test
    fun swipeOnTasksRevealsHabitsAndBack() {
        assertEquals(1, Habits.pageAfterSwipe(0, -80f, 48f))
        assertEquals(0, Habits.pageAfterSwipe(0, -10f, 48f))
        assertEquals(0, Habits.pageAfterSwipe(1, 80f, 48f))
    }

    @Test
    fun reminderFiresAtTheSlotAndNotAfterACheck() {
        val water = habit(Habits.parse("water every 4 hours starting at 8am", morning)!!, createdAt = morning - 86_400_000L)
        val eight = Habits.dayStart(morning, zone) + 8 * 3_600_000L
        val due = Habits.dueReminder(listOf(water), emptyList(), eight, emptySet(), zone)
        assertEquals(water.id, due?.habitId)
        assertEquals(eight, due?.slotAt)
        val marks = Habits.toggle(emptyList(), water.id, eight, eight)
        assertNull(Habits.dueReminder(listOf(water), marks, eight, emptySet(), zone))
    }

    private fun habit(draft: HabitDraft, id: String = "h", createdAt: Long = morning) = Habit(
        id = id,
        name = draft.name,
        kind = draft.kind,
        everyMinutes = draft.everyMinutes,
        times = draft.times,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
