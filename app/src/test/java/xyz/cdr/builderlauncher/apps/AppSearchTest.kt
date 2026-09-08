package xyz.cdr.builderlauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private val apps = listOf(
        app("Camera", "com.android.camera"),
        app("Molly", "im.molly.app"),
        app("Signal", "org.thoughtcrime.securesms"),
        app("Termux", "com.termux"),
    )

    @Test
    fun signalIncludesMolly() {
        val hits = AppSearch.filter(apps, "Signal").map { it.label }
        assertEquals(listOf("Molly", "Signal"), hits)
    }

    @Test
    fun signalPrefixIncludesMolly() {
        val hits = AppSearch.filter(apps, "sig").map { it.label }
        assertEquals(listOf("Molly", "Signal"), hits)
    }

    @Test
    fun shortPrefixDoesNotAlias() {
        val hits = AppSearch.filter(apps, "si").map { it.label }
        assertEquals(listOf("Signal"), hits)
    }

    @Test
    fun mollyByPackage() {
        val catalog = listOf(app("Messenger", "im.molly.app"))
        val hits = AppSearch.filter(catalog, "signal").map { it.packageName }
        assertEquals(listOf("im.molly.app"), hits)
    }

    @Test
    fun unrelatedQueryUnchanged() {
        val hits = AppSearch.filter(apps, "term").map { it.label }
        assertEquals(listOf("Termux"), hits)
    }

    @Test
    fun emptyReturnsAll() {
        assertEquals(apps, AppSearch.filter(apps, "  "))
    }

    @Test
    fun mollyQueryDoesNotPullSignal() {
        val hits = AppSearch.filter(apps, "molly").map { it.label }
        assertEquals(listOf("Molly"), hits)
        assertTrue(hits.none { it == "Signal" })
    }

    private fun app(label: String, packageName: String) = LaunchableApp(
        label = label,
        packageName = packageName,
        activityName = "$packageName.Main",
    )
}
