package xyz.cdr.builderlauncher.ui

import android.content.res.Configuration
import android.view.WindowManager
import xyz.cdr.builderlauncher.data.KeyboardMode

object KeyboardPresence {
    fun usesHardwareKeys(mode: KeyboardMode, configurationKeyboard: Int): Boolean =
        when (mode) {
            KeyboardMode.HARDWARE -> true
            KeyboardMode.SOFTWARE -> false
            KeyboardMode.AUTO -> configurationKeyboard == Configuration.KEYBOARD_QWERTY
        }

    fun softInputMode(hardware: Boolean): Int {
        val state = if (hardware) {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }
        return state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
}
