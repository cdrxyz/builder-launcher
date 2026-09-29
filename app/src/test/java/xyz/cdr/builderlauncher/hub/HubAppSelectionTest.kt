package xyz.cdr.builderlauncher.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAppSelectionTest {
    private val installed = listOf(
        "com.Slack",
        "com.google.android.apps.messaging",
        "org.thoughtcrime.securesms",
        "com.google.android.calendar",
    )

    @Test
    fun slackStartsChecked() {
        val selection = HubAppSelection()
        assertTrue(selection.checked("com.Slack"))
        assertFalse(selection.checked("com.google.android.calendar"))
    }

    @Test
    fun uncheckingSlackRestrictsToTheRest() {
        val next = HubAppSelection().toggle("com.Slack", installed)
        assertTrue(next.restrict)
        assertFalse(next.checked("com.Slack"))
        assertTrue(next.checked("org.thoughtcrime.securesms"))
        assertFalse(next.allows("com.google.android.calendar"))
    }

    @Test
    fun checkingCalendarAddsIt() {
        val next = HubAppSelection().toggle("com.google.android.calendar", installed)
        assertTrue(next.restrict)
        assertTrue(next.checked("com.google.android.calendar"))
        assertTrue(next.checked("com.Slack"))
    }

    @Test
    fun restoringDefaultsClearsTheRestriction() {
        val off = HubAppSelection().toggle("com.Slack", installed)
        val back = off.toggle("com.Slack", installed)
        assertFalse(back.restrict)
        assertEquals(HubAppSelection(), back)
    }

    @Test
    fun mixedCaseStoredPackageStillMatches() {
        val selection = HubAppSelection(restrict = true, packages = setOf("com.Slack"))
        assertTrue(selection.allows("com.slack"))
        assertTrue(selection.checked("com.Slack"))
    }

    @Test
    fun encodeRoundTrip() {
        val selection = HubAppSelection(restrict = true, packages = setOf("com.Slack", "com.discord"))
        val decoded = HubAppSelection.decode(
            HubAppSelection.encodeRestrict(selection),
            HubAppSelection.encodePackages(selection),
        )
        assertEquals(setOf("com.slack", "com.discord"), decoded.packages)
        assertTrue(decoded.restrict)
    }

    @Test
    fun unrestrictedDecodeIgnoresStoredPackages() {
        val decoded = HubAppSelection.decode("false", "com.slack")
        assertFalse(decoded.restrict)
        assertTrue(decoded.packages.isEmpty())
    }
}
