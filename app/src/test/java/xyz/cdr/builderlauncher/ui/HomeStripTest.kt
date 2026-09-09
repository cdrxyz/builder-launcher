package xyz.cdr.builderlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStripTest {
    @Test
    fun podcastsHomeHubOrderMatchesSwipe() {
        assertEquals(listOf(Page.Podcasts, Page.Home, Page.Hub), HomeStrip.pages)
        assertEquals(0, HomeStrip.indexOf(Page.Podcasts))
        assertEquals(1, HomeStrip.indexOf(Page.Home))
        assertEquals(2, HomeStrip.indexOf(Page.Hub))
        assertEquals(null, HomeStrip.indexOf(Page.Todos))
        assertTrue(HomeStrip.contains(Page.Home))
        assertFalse(HomeStrip.contains(Page.Settings))
        assertFalse(HomeStrip.contains(Page.Usage))
        assertEquals(Page.Hub, HomeStrip.pageAt(2))
        assertEquals(Page.Home, HomeStrip.pageAt(9))
    }

    @Test
    fun leavingTodosDoesNotLandOnPodcasts() {
        assertTrue(HomeStrip.coversPager(Page.Todos))
        assertTrue(HomeStrip.coversPager(Page.Usage))
        assertFalse(HomeStrip.contains(Page.Todos))
        assertEquals(Page.Home, HomeStrip.homeAfterOverlay())
        assertEquals(Page.Podcasts, HomeStrip.pageAt(HomeStrip.PODCASTS))
        assertEquals(Page.Home, HomeStrip.pageAt(HomeStrip.HOME))
    }

    @Test
    fun homePressDoesNotFollowStalePodcastsPage() {
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
            Page.Podcasts,
            HomeStrip.followSettled(Page.Home, settledIndex = 0, currentIndex = 0, scrolling = false),
        )
        assertEquals(
            null,
            HomeStrip.followSettled(Page.Home, settledIndex = 1, currentIndex = 1, scrolling = false),
        )
    }
}
