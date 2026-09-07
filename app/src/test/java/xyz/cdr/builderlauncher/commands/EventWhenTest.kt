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
}
