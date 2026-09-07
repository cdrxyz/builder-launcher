package xyz.cdr.builderlauncher.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRoleTest {
    @Test
    fun asksOnceWhenNotHome() {
        assertTrue(HomeRole.shouldAsk(asked = false, held = false))
    }

    @Test
    fun skipsWhenAlreadyAsked() {
        assertFalse(HomeRole.shouldAsk(asked = true, held = false))
    }

    @Test
    fun skipsWhenAlreadyHome() {
        assertFalse(HomeRole.shouldAsk(asked = false, held = true))
    }
}
