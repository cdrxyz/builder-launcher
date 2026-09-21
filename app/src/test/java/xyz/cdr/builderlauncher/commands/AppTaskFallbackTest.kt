package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTaskFallbackTest {
    private fun step(
        input: String,
        matches: Int,
        state: AppTaskFallback.State = AppTaskFallback.State(),
        prompt: Char = PrefixCommands.DEFAULT_PROMPT,
    ) = AppTaskFallback.step(PrefixCommands.Mode(prompt = prompt, input = input), matches, state)

    @Test
    fun twoUnmatchedCharsStayInDefaultMode() {
        val first = step("b", matches = 0)
        assertFalse(first.switched)
        assertEquals('>', first.mode.prompt)
        val second = step("bu", matches = 0, state = first.state)
        assertFalse(second.switched)
        assertEquals("bu", second.mode.input)
    }

    @Test
    fun thirdUnmatchedCharSwitchesToTaskMode() {
        var state = AppTaskFallback.State()
        for (input in listOf("b", "bu")) {
            val result = step(input, matches = 0, state = state)
            assertFalse(result.switched)
            state = result.state
        }
        val switched = step("buy", matches = 0, state = state)
        assertTrue(switched.switched)
        assertEquals('-', switched.mode.prompt)
        assertEquals("buy", switched.mode.input)
        assertEquals("-buy", switched.mode.line)
    }

    @Test
    fun pasteOfThreeOrMoreUnmatchedCharsSwitches() {
        val result = step("buy milk", matches = 0)
        assertTrue(result.switched)
        assertEquals('-', result.mode.prompt)
        assertEquals("buy milk", result.mode.input)
    }

    @Test
    fun matchingAppsResetTheUnmatchedRun() {
        val matched = step("ch", matches = 2)
        assertFalse(matched.switched)
        val typo = step("chx", matches = 0, state = matched.state)
        assertFalse(typo.switched)
        val second = step("chxx", matches = 0, state = typo.state)
        assertFalse(second.switched)
        val third = step("chxxx", matches = 0, state = second.state)
        assertTrue(third.switched)
        assertEquals("chxxx", third.mode.input)
    }

    @Test
    fun prefixModesNeverSwitch() {
        val result = step("buy milk", matches = 0, prompt = '@')
        assertFalse(result.switched)
        assertEquals('@', result.mode.prompt)
    }

    @Test
    fun calculatorExpressionsStayInDefaultMode() {
        val result = step("2+2", matches = 0)
        assertFalse(result.switched)
        assertEquals('>', result.mode.prompt)
    }

    @Test
    fun slashCommandPrefixesStayInDefaultMode() {
        val result = step("set", matches = 0)
        assertFalse(result.switched)
        val longer = step("settings", matches = 0, state = result.state)
        assertFalse(longer.switched)
    }

    @Test
    fun pinQueriesStayInDefaultMode() {
        val result = AppTaskFallback.step(
            PrefixCommands.Mode(input = "pin twitter"),
            matchCount = 0,
            state = AppTaskFallback.State(),
        )
        assertFalse(result.switched)
        assertEquals('>', result.mode.prompt)
    }

    @Test
    fun notesAndStocksQueriesStayInDefaultMode() {
        assertFalse(step("no", matches = 0).switched)
        assertFalse(step("sto", matches = 0).switched)
        assertFalse(step("po", matches = 0).switched)
    }

    @Test
    fun replacingAMatchWithShorterUnmatchedTextSwitches() {
        val matched = step("chrome", matches = 1)
        assertFalse(matched.switched)
        val replaced = step("buy", matches = 0, state = matched.state)
        assertTrue(replaced.switched)
        assertEquals('-', replaced.mode.prompt)
        assertEquals("buy", replaced.mode.input)
    }

    @Test
    fun extraCharsAfterAReservedPrefixStillSwitch() {
        val reserved = step("help", matches = 0)
        assertFalse(reserved.switched)
        var state = reserved.state
        var switched = false
        var last = reserved
        for (input in listOf("help ", "help m", "help mo")) {
            last = step(input, matches = 0, state = state)
            state = last.state
            if (last.switched) {
                switched = true
                break
            }
        }
        assertTrue(switched)
        assertEquals('-', last.mode.prompt)
        assertEquals("help mo", last.mode.input)
    }
}
