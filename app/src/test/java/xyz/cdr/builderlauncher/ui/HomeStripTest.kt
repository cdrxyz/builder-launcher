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
}
