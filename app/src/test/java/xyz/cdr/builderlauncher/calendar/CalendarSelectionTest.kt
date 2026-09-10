package xyz.cdr.builderlauncher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSelectionTest {
    private val personal = DeviceCalendar(1, "Personal", "alex@example.com", visible = true)
    private val work = DeviceCalendar(2, "Work", "work@example.com", visible = true)
    private val holidays = DeviceCalendar(3, "Holidays", "Holidays", visible = false)
    private val calendars = listOf(personal, work, holidays)

    @Test
    fun defaultIncludesVisibleOnly() {
        val selection = CalendarSelection()
        assertTrue(selection.allows(personal.id))
        assertTrue(selection.checked(personal))
        assertFalse(selection.checked(holidays))
    }

    @Test
    fun uncheckVisibleRestrictsToTheRest() {
        val next = CalendarSelection().toggle(personal.id, calendars)
        assertTrue(next.restrict)
        assertEquals(setOf(work.id), next.ids)
        assertFalse(next.checked(personal))
        assertTrue(next.checked(work))
        assertFalse(next.allows(personal.id))
    }

    @Test
    fun checkHiddenAddsIt() {
        val next = CalendarSelection().toggle(holidays.id, calendars)
        assertTrue(next.restrict)
        assertEquals(setOf(personal.id, work.id, holidays.id), next.ids)
        assertTrue(next.checked(holidays))
        assertTrue(next.allows(holidays.id))
    }

    @Test
    fun restoringVisibleSetClearsRestrict() {
        val next = CalendarSelection()
            .toggle(personal.id, calendars)
            .toggle(personal.id, calendars)
        assertEquals(CalendarSelection(), next)
    }

    @Test
    fun emptyRestrictShowsNothing() {
        val empty = CalendarSelection(restrict = true, ids = emptySet())
        assertFalse(empty.allows(personal.id))
        assertFalse(empty.checked(personal))
    }

    @Test
    fun roundTripEmptyIsUnrestricted() {
        val raw = CalendarSelection.decode("false", "1,2")
        assertEquals(CalendarSelection(), raw)
        assertEquals("false", CalendarSelection.encodeRestrict(raw))
        assertEquals("", CalendarSelection.encodeIds(raw))
    }

    @Test
    fun roundTripRestrictedIds() {
        val selection = CalendarSelection(restrict = true, ids = setOf(2, 1))
        val encoded = CalendarSelection.encodeIds(selection)
        assertEquals("true", CalendarSelection.encodeRestrict(selection))
        assertEquals("1,2", encoded)
        assertEquals(selection.copy(ids = setOf(1, 2)), CalendarSelection.decode("true", encoded))
    }
}
