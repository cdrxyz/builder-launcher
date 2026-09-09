package xyz.cdr.builderlauncher.ui

import android.content.res.Configuration
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.KeyboardMode

class KeyboardPresenceTest {
    @Test
    fun autoFollowsHardwareQwerty() {
        assertTrue(
            KeyboardPresence.usesHardwareKeys(KeyboardMode.AUTO, Configuration.KEYBOARD_QWERTY),
        )
        assertFalse(
            KeyboardPresence.usesHardwareKeys(KeyboardMode.AUTO, Configuration.KEYBOARD_NOKEYS),
        )
    }

    @Test
    fun softwareAlwaysUsesIme() {
        assertFalse(
            KeyboardPresence.usesHardwareKeys(KeyboardMode.SOFTWARE, Configuration.KEYBOARD_QWERTY),
        )
    }

    @Test
    fun hardwareAlwaysHidesIme() {
        assertTrue(
            KeyboardPresence.usesHardwareKeys(KeyboardMode.HARDWARE, Configuration.KEYBOARD_NOKEYS),
        )
    }

    @Test
    fun slabKeepsImeOpen() {
        val mode = KeyboardPresence.softInputMode(hardware = false)
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode,
        )
    }

    @Test
    fun physicalKeysHideIme() {
        val mode = KeyboardPresence.softInputMode(hardware = true)
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode,
        )
    }
}
