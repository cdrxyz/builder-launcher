package xyz.cdr.builderlauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListTest {
    private fun app(label: String) = LaunchableApp(label, label.lowercase(), label)

    @Test
    fun moreCopyMatchesNotesStyle() {
        assertEquals("… all apps >", AppList.MORE)
        assertEquals("<", AppList.BACK)
    }

    @Test
    fun previewCapsAtFiveAndKeepsOrder() {
        val apps = (1..8).map { app("App $it") }
        assertEquals(
            listOf("App 1", "App 2", "App 3", "App 4", "App 5"),
            AppList.preview(apps).map { it.label },
        )
        assertEquals(listOf("Only"), AppList.preview(listOf(app("Only"))).map { it.label })
        assertEquals(emptyList<LaunchableApp>(), AppList.preview(emptyList()))
    }

    @Test
    fun commandMatchesAppsWordOnly() {
        assertTrue(AppList.matchesCommand("apps"))
        assertTrue(AppList.matchesCommand("APPS"))
        assertTrue(AppList.matchesCommand("/apps"))
        assertFalse(AppList.matchesCommand(""))
        assertFalse(AppList.matchesCommand("ap"))
        assertFalse(AppList.matchesCommand("app"))
        assertFalse(AppList.matchesCommand("maps"))
    }
}
