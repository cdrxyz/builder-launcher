package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListReorderTest {
    @Test
    fun moveCrossesSeveralItems() {
        val items = listOf("A", "B", "C", "D", "E")
        assertEquals(listOf("A", "C", "D", "E", "B"), ListReorder.move(items, 1, 4))
        assertEquals(listOf("D", "A", "B", "C", "E"), ListReorder.move(items, 3, 0))
        assertEquals(items, ListReorder.move(items, 1, 1))
        assertEquals(items, ListReorder.move(items, -1, 0))
        assertEquals(items, ListReorder.move(items, 0, 9))
    }

    @Test
    fun targetIndexCrossesSeveralSteps() {
        assertEquals(3, ListReorder.targetIndex(0, 3.2f * 50f, 50f, 5))
        assertEquals(0, ListReorder.targetIndex(2, -2.4f * 50f, 50f, 5))
        assertEquals(2, ListReorder.targetIndex(2, 0.4f * 50f, 50f, 5))
        assertEquals(5, ListReorder.targetIndex(4, 80f, 50f, 5))
    }

    @Test
    fun neighborOffsetMakesAGap() {
        assertEquals(-50f, ListReorder.neighborOffset(2, 1, 4, 50f))
        assertEquals(-50f, ListReorder.neighborOffset(4, 1, 4, 50f))
        assertEquals(0f, ListReorder.neighborOffset(0, 1, 4, 50f))
        assertEquals(0f, ListReorder.neighborOffset(1, 1, 4, 50f))
        assertEquals(50f, ListReorder.neighborOffset(2, 4, 1, 50f))
        assertEquals(50f, ListReorder.neighborOffset(1, 4, 1, 50f))
        assertEquals(0f, ListReorder.neighborOffset(0, 4, 1, 50f))
    }

    @Test
    fun liveIndexIgnoresStaleSlotAfterMove() {
        val before = listOf("A", "B", "C")
        val after = ListReorder.move(before, 2, 0)
        assertEquals(2, ListReorder.liveIndex(before) { it == "C" })
        assertEquals(0, ListReorder.liveIndex(after) { it == "C" })
        assertEquals(-1, ListReorder.liveIndex(after) { it == "Z" })
    }
}
