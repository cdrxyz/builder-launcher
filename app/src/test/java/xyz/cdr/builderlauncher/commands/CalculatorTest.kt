package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test
    fun addsWhileTyping() {
        assertEquals("4", Calculator.preview("2+2"))
    }

    @Test
    fun multipliesAndPowers() {
        assertEquals("555", Calculator.preview("15*37"))
        assertEquals("1024", Calculator.preview("2^10"))
        assertEquals("9", Calculator.preview("(1+2)*3"))
    }

    @Test
    fun functionsAndConstants() {
        assertEquals("12", Calculator.preview("sqrt(144)"))
        assertEquals("0", Calculator.preview("sin(0)"))
        assertEquals("6.28318530718", Calculator.preview("pi*2"))
    }

    @Test
    fun ignoresProseCommandsAndBareNumbers() {
        assertNull(Calculator.preview("42"))
        assertNull(Calculator.preview("Signal"))
        assertNull(Calculator.preview("what is 2+2"))
        assertNull(Calculator.preview("/help"))
        assertNull(Calculator.preview("@jason 2+2"))
        assertNull(Calculator.preview("-buy milk"))
        assertNull(Calculator.preview("?weather"))
        assertNull(Calculator.preview("2+"))
        assertNull(Calculator.preview(""))
    }

    @Test
    fun enterKeepsTheResultInTheBar() {
        assertEquals("4", Calculator.commit("2+2"))
        assertNull(Calculator.commit("Signal"))
    }

    @Test
    fun askPrefixOnTheFullLineIsIgnoredButTheFieldBodyEvaluates() {
        assertNull(Calculator.preview("?2+2"))
        assertEquals("4", Calculator.preview("2+2"))
    }
}
