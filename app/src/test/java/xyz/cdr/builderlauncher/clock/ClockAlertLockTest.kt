package xyz.cdr.builderlauncher.clock

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAlertLockTest {
    @Test
    fun alertingWindowShowsOverLockAndWakesScreen() {
        val window = ClockAlertLock.window(alerting = true)
        assertTrue(window.showWhenLocked)
        assertTrue(window.turnScreenOn)
        assertTrue(window.keepScreenOn)
    }

    @Test
    fun idleWindowStaysBehindLock() {
        val window = ClockAlertLock.window(alerting = false)
        assertFalse(window.showWhenLocked)
        assertFalse(window.turnScreenOn)
        assertFalse(window.keepScreenOn)
    }

    @Test
    fun fullScreenOpensDedicatedAlertActivity() {
        assertEquals(ClockAlertActivity::class.java, ClockAlertLock.activityClass)
    }

    @Test
    fun fullScreenIntentCanLaunchWhenLocked() {
        val flags = ClockAlertLock.intentFlags()
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NO_USER_ACTION != 0)
    }

    @Test
    fun dedicatedActivityLaunchesOnlyWhenLockedAndAlerting() {
        assertTrue(ClockAlertLock.shouldLaunch(alerting = true, keyguardLocked = true))
        assertFalse(ClockAlertLock.shouldLaunch(alerting = true, keyguardLocked = false))
        assertFalse(ClockAlertLock.shouldLaunch(alerting = false, keyguardLocked = true))
    }
}
