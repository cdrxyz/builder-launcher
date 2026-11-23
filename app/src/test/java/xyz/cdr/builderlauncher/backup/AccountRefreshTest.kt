package xyz.cdr.builderlauncher.backup

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
        assertTrue(AccountRefresh.isExpired("Not signed in"))
        assertFalse(AccountRefresh.isExpired("HTTP 500"))
        assertFalse(AccountRefresh.isExpired("Too many attempts. Try again later."))
        assertFalse(AccountRefresh.isExpired(null))
        assertTrue(AccountRefresh.showsSignIn("me@example.com", "dead-token"))
        assertTrue(AccountRefresh.showsSignIn("", ""))
        assertTrue(AccountSignup.opens("timetobuild"))
        assertTrue(AccountSignup.opens(" TimeToBuild "))
        assertFalse(AccountSignup.opens("nope"))
        assertFalse(AccountSignup.opens(""))
    }

    @Test
    fun signedInBackupScreenStillOffersSignIn() {
        val screen = java.io.File("src/main/java/xyz/cdr/builderlauncher/ui/SettingsScreens.kt").readText()
        val backup = screen.substring(screen.indexOf("fun BackupSettingsPage"), screen.indexOf("fun AiProvidersPage"))
        // The sign-in form stays reachable even while a session looks live, so a
        // dead session can always be replaced from the same screen.
        assertTrue(backup.contains("AccountRefresh.showsSignIn"))
        // The signed-in banner needs a live token; a saved email is not proof.
        assertTrue(backup.contains("if (settings.accountToken.isNotBlank()) {"))
    }

    private fun client(code: Int, body: String): AccountClient {
        val http = OkHttpClient.Builder().addInterceptor {
            Response.Builder()
                .request(it.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody())
                .build()
        }.build()
        return AccountClient(http = http, origin = "https://unit.test")
    }

    @Test
    fun aDeadSessionKeepsTheMessageTheRefreshPathMatchesOn() {
        val err = runCatching { client(401, """{"error":"Not signed in"}""").getVault("token") }
            .exceptionOrNull()
        assertTrue(err is IllegalStateException)
        assertEquals(AccountRefresh.SIGNED_OUT, err?.message)
        assertTrue(AccountRefresh.isExpired(err?.message))
    }

    @Test
    fun aRateLimitedRefreshIsNotReadAsAnExpiredSession() {
        val err = runCatching {
            client(429, """{"error":"Too many attempts. Try again later."}""").refresh("refresh")
        }.exceptionOrNull()
        assertTrue(err is IllegalStateException)
        assertFalse(AccountRefresh.isExpired(err?.message))
    }

    @Test
    fun syncErrorLabelNamesTheFailureAndWhen() {
        assertEquals("", BackupService.lastSyncErrorLabel("", 5))
        assertEquals(
            "Sync failed: Not signed in (${BackupService.lastBackupLabel(1_000L)})",
            BackupService.lastSyncErrorLabel("Not signed in", 1_000L),
        )
    }
}
