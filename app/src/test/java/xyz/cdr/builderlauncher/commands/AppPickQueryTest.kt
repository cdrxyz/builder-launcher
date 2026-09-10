package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.cdr.builderlauncher.apps.LaunchableApp

class AppPickQueryTest {
    private val apps = listOf(
        app("Pinterest", "com.pinterest"),
        app("Termux", "com.termux"),
        app("X", "com.twitter.android"),
    )

    @Test
    fun defaultQueryLaunchesAndKeepsTheWholeNeedle() {
        val parsed = AppPickQuery.parse("twitter")
        assertEquals(AppPick.Launch, parsed.pick)
        assertEquals("twitter", parsed.query)
        assertEquals(listOf("X"), parsed.filter(apps).map { it.label })
    }

    @Test
    fun pinPrefixFiltersTheRemainderAndMarksPin() {
        val parsed = AppPickQuery.parse("pin twitter")
        assertEquals(AppPick.Pin, parsed.pick)
        assertEquals("twitter", parsed.query)
        assertEquals(listOf("X"), parsed.filter(apps).map { it.label })
    }

    @Test
    fun unpinPrefixFiltersPinnedMatchesOnly() {
        val parsed = AppPickQuery.parse("unpin twitter")
        assertEquals(AppPick.Unpin, parsed.pick)
        assertEquals("twitter", parsed.query)
        assertEquals(
            listOf("X"),
            parsed.filter(apps, pinned = setOf("com.twitter.android")).map { it.label },
        )
        assertEquals(
            emptyList<String>(),
            parsed.filter(apps, pinned = emptySet()).map { it.label },
        )
    }

    @Test
    fun pinterestIsLaunchNotPin() {
        val parsed = AppPickQuery.parse("pinterest")
        assertEquals(AppPick.Launch, parsed.pick)
        assertEquals("pinterest", parsed.query)
        assertEquals(listOf("Pinterest"), parsed.filter(apps).map { it.label })
    }

    @Test
    fun barePinAndUnpinHaveEmptyQuery() {
        assertEquals(AppPickQuery(AppPick.Pin, ""), AppPickQuery.parse("pin"))
        assertEquals(AppPickQuery(AppPick.Unpin, ""), AppPickQuery.parse("UNPIN"))
        assertEquals(listOf("Pinterest", "Termux", "X"), AppPickQuery.parse("pin ").filter(apps).map { it.label })
    }

    private fun app(label: String, packageName: String) = LaunchableApp(
        label = label,
        packageName = packageName,
        activityName = "$packageName.Main",
    )
}
