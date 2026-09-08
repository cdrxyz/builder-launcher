package xyz.cdr.builderlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStripTest {
    @Test
    fun usageHomeHubOrderMatchesSwipe() {
        assertEquals(listOf(Page.Usage, Page.Home, Page.Hub), HomeStrip.pages)
        assertEquals(0, HomeStrip.indexOf(Page.Usage))
        assertEquals(1, HomeStrip.indexOf(Page.Home))
        assertEquals(2, HomeStrip.indexOf(Page.Hub))
        assertEquals(null, HomeStrip.indexOf(Page.Todos))
        assertTrue(HomeStrip.contains(Page.Home))
        assertFalse(HomeStrip.contains(Page.Settings))
        assertEquals(Page.Hub, HomeStrip.pageAt(2))
        assertEquals(Page.Home, HomeStrip.pageAt(9))
    }

    @Test
    fun leavingTodosDoesNotLandOnUsage() {
        assertTrue(HomeStrip.coversPager(Page.Todos))
        assertFalse(HomeStrip.contains(Page.Todos))
        assertEquals(Page.Home, HomeStrip.homeAfterOverlay())
        assertEquals(Page.Usage, HomeStrip.pageAt(HomeStrip.USAGE))
        assertEquals(Page.Home, HomeStrip.pageAt(HomeStrip.HOME))
    }

    @Test
    fun homePressDoesNotFollowStaleUsagePage() {
        assertEquals(
            null,
            HomeStrip.followSettled(Page.Home, settledIndex = 0, currentIndex = 1, scrolling = false),
        )
        assertEquals(
            null,
            HomeStrip.followSettled(Page.Todos, settledIndex = 0, currentIndex = 0, scrolling = false),
        )
        assertEquals(
            null,
            HomeStrip.followSettled(Page.Home, settledIndex = 0, currentIndex = 0, scrolling = true),
        )
        assertEquals(
            Page.Usage,
            HomeStrip.followSettled(Page.Home, settledIndex = 0, currentIndex = 0, scrolling = false),
        )
        assertEquals(
            null,
            HomeStrip.followSettled(Page.Home, settledIndex = 1, currentIndex = 1, scrolling = false),
        )
    }
}
