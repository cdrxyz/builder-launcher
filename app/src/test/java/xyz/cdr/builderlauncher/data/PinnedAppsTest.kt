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
    fun unpinRecordsAChangeNewerThanThePreviousPin() {
        val (pins, changes) = PinnedApps.record(
            pins = listOf("keep", "gone"),
            changes = listOf(PinChange("gone", pinned = true, at = 5)),
            packageName = "gone",
            pinned = false,
            at = 9,
        )
        assertEquals(listOf("keep"), pins)
        assertEquals(PinChange("gone", pinned = false, at = 9), changes.single())
    }

    @Test
    fun pinAfterUnpinPutsThePackageBackWithANewerStamp() {
        val (pins, changes) = PinnedApps.record(
            pins = listOf("keep"),
            changes = listOf(PinChange("gone", pinned = false, at = 9)),
            packageName = "gone",
            pinned = true,
            at = 12,
        )
        assertEquals(listOf("keep", "gone"), pins)
        assertEquals(PinChange("gone", pinned = true, at = 12), changes.single())
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
