package xyz.cdr.builderlauncher.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.ChatMessage

class HermesWebUiTest {
    @Test
    fun promptUsesLastTurnWhenThereIsOnlyOne() {
        assertEquals(
            "hello",
            HermesWebUi.prompt(listOf(ChatMessage(role = "user", content = "hello"))),
        )
    }

    @Test
    fun promptJoinsAThreadForTheWebUi() {
        val text = HermesWebUi.prompt(
            listOf(
                ChatMessage(role = "user", content = "compare kotlin and rust"),
                ChatMessage(role = "assistant", content = "Use Kotlin on Android."),
                ChatMessage(role = "user", content = "why"),
            ),
        )
        assertTrue(text.contains("User: compare kotlin and rust"))
        assertTrue(text.contains("Assistant: Use Kotlin on Android."))
        assertTrue(text.endsWith("User: why"))
    }

    @Test
    fun sessionAndStreamIdsReadNestedOrFlatJson() {
        assertEquals(
            "s-1",
            HermesWebUi.sessionId("""{"session":{"session_id":"s-1"}}"""),
        )
        assertEquals("st-9", HermesWebUi.streamId("""{"stream_id":"st-9"}"""))
    }

    @Test
    fun authFlagsReadPasswordRequired() {
        assertTrue(HermesWebUi.passwordRequired("""{"authenticated":false,"password_required":true}"""))
        assertFalse(HermesWebUi.authenticated("""{"authenticated":false,"password_required":true}"""))
        assertTrue(HermesWebUi.authenticated("""{"authenticated":true,"password_required":false}"""))
    }

    @Test
    fun needsLoginOn401EvenWithoutPasswordRequiredFlag() {
        val gated = """{"error":"Authentication required"}"""
        assertTrue(HermesWebUi.needsLogin(401, gated))
        assertFalse(HermesWebUi.alreadyAuthenticated(401, gated))
        assertTrue(HermesWebUi.needsLogin(200, """{"authenticated":false,"password_required":true}"""))
        assertFalse(HermesWebUi.needsLogin(200, """{"authenticated":true,"password_required":false}"""))
        assertFalse(HermesWebUi.needsLogin(200, """{"authenticated":false,"password_required":false}"""))
    }

    @Test
    fun authFailedMatchesQuestion401() {
        assertTrue(HermesWebUi.authFailed("""LLM error 401: {"error":"Authentication required"}"""))
        assertTrue(HermesWebUi.authFailed("Web UI needs a password in settings."))
        assertFalse(HermesWebUi.authFailed("Here is a short answer."))
    }

    @Test
    fun loginPostsTheWebUiPasswordNotABearerToken() {
        assertEquals(
            """{"password":"hunter2"}""",
            HermesWebUi.loginBody("hunter2"),
        )
        assertEquals(
            """{"session_id":"s-1","message":"hello"}""",
            HermesWebUi.startBody("s-1", "hello"),
        )
        assertEquals("{}", HermesWebUi.newSessionBody("default"))
        assertEquals("""{"model":"hermes-agent"}""", HermesWebUi.newSessionBody("hermes-agent"))
    }
}
