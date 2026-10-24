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
        assertTrue(AccountRefresh.showsSignIn("me@example.com", "dead-token"))
        assertTrue(AccountRefresh.showsSignIn("", ""))
    }

    @Test
    fun signedInBackupScreenStillOffersSignIn() {
        val screen = java.io.File("src/main/java/xyz/cdr/builderlauncher/ui/SettingsScreens.kt").readText()
        val backup = screen.substring(screen.indexOf("fun BackupSettingsPage"), screen.indexOf("fun AiProvidersPage"))
        assertTrue(backup.contains("AccountRefresh.showsSignIn"))
        assertFalse(backup.contains("if (settings.accountToken.isNotBlank()) {\n"))
    }
}
