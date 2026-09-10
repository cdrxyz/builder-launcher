package xyz.cdr.builderlauncher.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubMessagesTest {
    @Test
    fun signalWithoutRemoteInputStillCounts() {
        assertTrue(
            HubMessages.isReplyable(
                packageName = "org.thoughtcrime.securesms",
                category = null,
                template = null,
            ),
        )
    }

    @Test
    fun mollyWithoutRemoteInputStillCounts() {
        assertTrue(
            HubMessages.isReplyable(
                packageName = "im.molly.app",
                category = null,
                template = null,
            ),
        )
    }

    @Test
    fun categoryMessageCounts() {
        assertTrue(
            HubMessages.isReplyable(
                packageName = "com.example.chat",
                category = HubMessages.CATEGORY_MESSAGE,
                template = null,
            ),
        )
    }

    @Test
    fun messagingStyleCounts() {
        assertTrue(
            HubMessages.isReplyable(
                packageName = "com.example.other",
                category = null,
                template = HubMessages.MESSAGING_STYLE,
            ),
        )
    }

    @Test
    fun calendarDoesNotCount() {
        assertFalse(
            HubMessages.isReplyable(
                packageName = "com.google.android.calendar",
                category = "event",
                template = null,
            ),
        )
    }

    @Test
    fun unknownAppDoesNotCount() {
        assertFalse(
            HubMessages.isReplyable(
                packageName = "com.example.downloads",
                category = null,
                template = null,
            ),
        )
    }

    @Test
    fun ongoingMessengerIsSkipped() {
        assertFalse(
            HubMessages.isReplyable(
                packageName = "org.thoughtcrime.securesms",
                category = null,
                template = null,
                ongoing = true,
            ),
        )
    }

    @Test
    fun groupSummaryIsSkipped() {
        assertFalse(
            HubMessages.isReplyable(
                packageName = "com.google.android.apps.messaging",
                category = HubMessages.CATEGORY_MESSAGE,
                template = null,
                groupSummary = true,
            ),
        )
    }

    @Test
    fun signalInPackageNameCounts() {
        assertTrue(HubMessages.isKnownMessenger("org.signal.nightly"))
    }

    @Test
    fun backMatchesOtherScreens() {
        assertEquals("<", HubMessages.BACK)
        assertEquals("hub", HubMessages.TITLE)
        assertEquals("clear all", HubMessages.CLEAR_ALL)
    }
}
