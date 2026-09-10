package xyz.cdr.builderlauncher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandMenuTest {
    @Test
    fun unboundedHeightFallsBack() {
        assertEquals(280.dp, commandMenuMaxHeight(Dp.Infinity))
    }

    @Test
    fun boundedHeightReservesInputRow() {
        assertEquals(144.dp, commandMenuMaxHeight(200.dp))
    }

    @Test
    fun promptTouchIs48dp() {
        assertEquals(48.dp, CommandTouch)
    }
}
