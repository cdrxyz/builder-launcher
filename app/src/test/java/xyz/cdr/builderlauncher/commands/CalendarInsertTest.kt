package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class CalendarInsertTest {
    private val noon = GregorianCalendar(2026, Calendar.SEPTEMBER, 7, 12, 0, 0).timeInMillis

    @Test
    fun weekdayGoesToBeginAndEnd() {
        val insert = CalendarInsert.from("dentist", "friday 9a", noon)
        val begin = Calendar.getInstance().apply { timeInMillis = insert.beginMillis!! }
        assertEquals(Calendar.SEPTEMBER, begin.get(Calendar.MONTH))
        assertEquals(11, begin.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, begin.get(Calendar.HOUR_OF_DAY))
        assertEquals(insert.beginMillis!! + 3_600_000L, insert.endMillis)
        assertNull(insert.description)
    }

    @Test
    fun numericDateGoesToBegin() {
        val insert = CalendarInsert.from("standup", "9/28 9:30", noon)
        val begin = Calendar.getInstance().apply { timeInMillis = insert.beginMillis!! }
        assertEquals(28, begin.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, begin.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, begin.get(Calendar.MINUTE))
        assertEquals(insert.beginMillis!! + 3_600_000L, insert.endMillis)
    }

    @Test
    fun unparsedWhenStaysDescription() {
        val insert = CalendarInsert.from("idea", "sometime later", noon)
        assertNull(insert.beginMillis)
        assertNull(insert.endMillis)
        assertEquals("sometime later", insert.description)
    }
}
