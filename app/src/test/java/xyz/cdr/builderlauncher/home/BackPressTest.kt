package xyz.cdr.builderlauncher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class BackPressTest {
    @Test
    fun backOnInnerPageGoesHome() {
        assertEquals(
            BackResult.OpenHome,
            BackPress.result(onHome = false, overlayOpen = false),
        )
    }

    @Test
    fun backOnHomeStaysWhenPromptIsIdle() {
        assertEquals(
            BackResult.Stay,
            BackPress.result(onHome = true, overlayOpen = false, promptActive = false),
        )
    }

    @Test
    fun backOnHomeResetsActivePromptMode() {
        assertEquals(
            BackResult.ResetPrompt,
            BackPress.result(onHome = true, overlayOpen = false, promptActive = true),
        )
    }

    @Test
    fun backDismissesOverlayBeforeLeavingPage() {
        assertEquals(
            BackResult.DismissUi,
            BackPress.result(onHome = false, overlayOpen = true),
        )
        assertEquals(
            BackResult.DismissUi,
            BackPress.result(onHome = true, overlayOpen = true, promptActive = true),
        )
    }
}
