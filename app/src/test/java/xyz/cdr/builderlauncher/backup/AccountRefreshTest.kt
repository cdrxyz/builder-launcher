package xyz.cdr.builderlauncher.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRefreshTest {
    @Test
    fun retriesOnlyADeadSessionThatStillHasARefreshToken() {
        assertTrue(AccountRefresh.shouldRetry("Not signed in", "refresh"))
        assertFalse(AccountRefresh.shouldRetry("Not signed in", ""))
        assertFalse(AccountRefresh.shouldRetry("HTTP 500", "refresh"))
        assertFalse(AccountRefresh.shouldRetry(null, "refresh"))
    }
}
