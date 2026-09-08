package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorTest {
    private val cyberpunk = 0xFF00FF41L.toInt()
    private val sage = 0xFFB7C9A8L.toInt()

    @Test
    fun defaultIsCyberpunkGreen() {
        assertEquals("#00FF41", AccentColor.DEFAULT_HEX)
        assertEquals("cyberpunk green", AccentColor.nameOf(AccentColor.DEFAULT_HEX))
        assertEquals(cyberpunk, AccentColor.argb(null))
        assertEquals(cyberpunk, AccentColor.parse(AccentColor.DEFAULT_HEX))
    }

    @Test
    fun parsesHashRgbAndShortForm() {
        assertEquals(cyberpunk, AccentColor.parse("#00FF41"))
        assertEquals(cyberpunk, AccentColor.parse("00ff41"))
        assertEquals(0xFF00FF44L.toInt(), AccentColor.parse("#0F4"))
        assertEquals(sage, AccentColor.parse("#B7C9A8"))
    }

    @Test
    fun rejectsJunkAndForcesOpaque() {
        assertNull(AccentColor.parse(""))
        assertNull(AccentColor.parse("#GG0000"))
        assertNull(AccentColor.parse("#00FF4"))
        assertEquals(cyberpunk, AccentColor.parse("#8800FF41"))
    }

    @Test
    fun normalizeAndSameIgnoreCase() {
        assertEquals("#00FF41", AccentColor.normalize("00ff41"))
        assertTrue(AccentColor.same("#00ff41", AccentColor.DEFAULT_HEX))
        assertFalse(AccentColor.same("#00FF41", "#B7C9A8"))
        assertEquals("sage", AccentColor.nameOf("#b7c9a8"))
        assertNull(AccentColor.nameOf("#112233"))
    }

    @Test
    fun stockUpDownDefaultsArePickerGreenAndRed() {
        assertEquals(AccentColor.DEFAULT_HEX, AccentColor.DEFAULT_UP_HEX)
        assertEquals("#FF3B30", AccentColor.DEFAULT_DOWN_HEX)
        assertEquals("red", AccentColor.nameOf(AccentColor.DEFAULT_DOWN_HEX))
    }
}
