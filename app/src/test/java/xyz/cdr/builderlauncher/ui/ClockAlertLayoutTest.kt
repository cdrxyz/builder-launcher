package xyz.cdr.builderlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockAlertLayoutTest {
    @Test
    fun shortDisplayStaysReadable() {
        assertEquals(48, ClockAlertLayout.titleSp(419))
        assertEquals(36, ClockAlertLayout.timeSp(419))
        assertEquals(28, ClockAlertLayout.actionSp(419))
        assertEquals(72, ClockAlertLayout.actionMinHeightDp(419))
    }

    @Test
    fun keyboardUpSlabUsesMidSizes() {
        assertEquals(56, ClockAlertLayout.titleSp(420))
        assertEquals(56, ClockAlertLayout.titleSp(559))
        assertEquals(40, ClockAlertLayout.timeSp(500))
        assertEquals(32, ClockAlertLayout.actionSp(500))
        assertEquals(84, ClockAlertLayout.actionMinHeightDp(500))
    }

    @Test
    fun fullScreenUsesLargeTitleAndActions() {
        assertEquals(72, ClockAlertLayout.titleSp(560))
        assertEquals(72, ClockAlertLayout.titleSp(851))
        assertEquals(48, ClockAlertLayout.timeSp(851))
        assertEquals(36, ClockAlertLayout.actionSp(851))
        assertEquals(96, ClockAlertLayout.actionMinHeightDp(851))
    }
}
