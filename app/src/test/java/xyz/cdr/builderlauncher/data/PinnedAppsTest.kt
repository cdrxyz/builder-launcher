package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedAppsTest {
    @Test
    fun visibleMoveReordersTheDraggedPackageNotANeighbor() {
        val stored = listOf("a", "b", "c", "d")
        assertEquals(
            listOf("a", "c", "b", "d"),
            PinnedApps.orderAfterMove(stored, stored, from = 2, to = 1),
        )
        assertEquals(
            listOf("d", "a", "b", "c"),
            PinnedApps.orderAfterMove(stored, stored, from = 3, to = 0),
        )
        assertEquals(stored, PinnedApps.orderAfterMove(stored, stored, from = 1, to = 1))
    }

    @Test
    fun visibleMoveKeepsMissingPinsAndDoesNotShiftThem() {
        val stored = listOf("a", "gone", "b", "c")
        val visible = listOf("a", "b", "c")
        assertEquals(
            listOf("b", "a", "c", "gone"),
            PinnedApps.orderAfterMove(stored, visible, from = 1, to = 0),
        )
        assertEquals(
            listOf("a", "c", "b", "gone"),
            PinnedApps.orderAfterMove(stored, visible, from = 1, to = 2),
        )
    }
}
