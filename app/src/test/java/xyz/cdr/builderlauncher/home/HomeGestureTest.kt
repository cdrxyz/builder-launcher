package xyz.cdr.builderlauncher.home

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureTest {
    @Test
    fun homeWhileInLauncherGoesHome() {
        assertTrue(
            HomeGesture.shouldOpenHome(
                wasStopped = false,
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_HOME),
            ),
        )
    }

    @Test
    fun homeFromAnotherAppKeepsPage() {
        assertFalse(
            HomeGesture.shouldOpenHome(
                wasStopped = true,
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_HOME),
            ),
        )
    }

    @Test
    fun launcherIconIsNotHomePress() {
        assertFalse(
            HomeGesture.shouldOpenHome(
                wasStopped = false,
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_LAUNCHER),
            ),
        )
    }

    @Test
    fun missingHomeCategoryIsIgnored() {
        assertFalse(
            HomeGesture.shouldOpenHome(
                wasStopped = false,
                action = Intent.ACTION_MAIN,
                categories = emptySet(),
            ),
        )
    }
}
