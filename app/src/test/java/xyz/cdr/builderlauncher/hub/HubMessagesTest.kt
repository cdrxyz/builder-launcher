package xyz.cdr.builderlauncher.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubMessagesTest {
    @Test
    fun signalWithoutRemoteInputStillCounts() {
        assertTrue(HubMessages.isReplyable(packageName = "org.thoughtcrime.securesms"))
    }

    @Test
    fun mollyWithoutRemoteInputStillCounts() {
        assertTrue(HubMessages.isReplyable(packageName = "im.molly.app"))
    }

    @Test
    fun messageCategoryDoesNotBypassTheAppList() {
        assertFalse(HubMessages.isReplyable(packageName = "com.example.chat"))
    }

    @Test
    fun checkedAppCountsEvenWithoutAMessageCategory() {
        val selection = HubAppSelection(restrict = true, packages = setOf("com.example.chat"))
        assertTrue(HubMessages.isReplyable(packageName = "com.example.chat", selection = selection))
    }

    @Test
    fun calendarDoesNotCount() {
        assertFalse(HubMessages.isReplyable(packageName = "com.google.android.calendar"))
    }

    @Test
    fun unknownAppDoesNotCount() {
        assertFalse(HubMessages.isReplyable(packageName = "com.example.downloads"))
    }

    @Test
    fun ongoingMessengerIsSkipped() {
        assertFalse(HubMessages.isReplyable(packageName = "org.thoughtcrime.securesms", ongoing = true))
    }

    @Test
    fun groupSummaryIsSkipped() {
        assertFalse(
            HubMessages.isReplyable(
                packageName = "com.google.android.apps.messaging",
                groupSummary = true,
            ),
        )
    }

    @Test
    fun signalInPackageNameCounts() {
        assertTrue(HubMessages.isKnownMessenger("org.signal.nightly"))
    }

    @Test
    fun slackPackageCounts() {
        assertTrue(HubMessages.isKnownMessenger("com.Slack"))
        assertTrue(HubMessages.isReplyable(packageName = "com.Slack"))
    }

    @Test
    fun slackInPackageNameCounts() {
        assertTrue(HubMessages.isKnownMessenger("com.Slack.intune"))
    }

    @Test
    fun restrictedSelectionDropsUncheckedMessenger() {
        val selection = HubAppSelection(restrict = true, packages = setOf("com.slack"))
        assertFalse(HubMessages.isReplyable(packageName = "org.thoughtcrime.securesms", selection = selection))
        assertTrue(HubMessages.isReplyable(packageName = "com.Slack", selection = selection))
    }

    @Test
    fun emptyAndroidTextFallsBackToMessageLines() {
        assertEquals(
            "stand up in 5",
            HubMessages.notificationBody(text = "", lines = listOf("stand up in 5")),
        )
    }

    @Test
    fun backMatchesOtherScreens() {
        assertEquals("<", HubMessages.BACK)
        assertEquals("hub", HubMessages.TITLE)
        assertEquals("clear all", HubMessages.CLEAR_ALL)
    }
}
