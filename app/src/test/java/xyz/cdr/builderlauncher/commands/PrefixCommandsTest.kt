package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefixCommandsTest {
    @Test
    fun findKnownGlyph() {
        assertEquals("text", PrefixCommands.find('@')?.label)
        assertEquals("ask AI", PrefixCommands.find('?')?.label)
        assertNull(PrefixCommands.find('>'))
    }

    @Test
    fun catalogCoversPrefixCommands() {
        assertEquals(listOf('@', '#', '*', '-', '+', '?'), PrefixCommands.all.map { it.glyph })
    }

    @Test
    fun pickReplacesPromptAndLeavesInput() {
        val started = PrefixCommands.Mode(input = "buy milk")
        val picked = PrefixCommands.pick(started, '-')
        assertEquals('-', picked.prompt)
        assertEquals("buy milk", picked.input)
        assertEquals("-buy milk", picked.line)
    }

    @Test
    fun pickDoesNotPrefillTheGlyph() {
        val picked = PrefixCommands.pick(PrefixCommands.Mode(), '-')
        assertEquals('-', picked.prompt)
        assertEquals("", picked.input)
        assertEquals("-", picked.line)
    }

    @Test
    fun typeMovesLeadingGlyphOntoThePrompt() {
        val typed = PrefixCommands.type(PrefixCommands.Mode(), "-buy milk")
        assertEquals('-', typed.prompt)
        assertEquals("buy milk", typed.input)
        assertEquals("-buy milk", typed.line)
    }

    @Test
    fun typeSwitchesModeWhenADifferentGlyphIsTyped() {
        val inTodo = PrefixCommands.Mode(prompt = '-', input = "")
        val typed = PrefixCommands.type(inTodo, "@jason ")
        assertEquals('@', typed.prompt)
        assertEquals("jason ", typed.input)
        assertEquals("@jason ", typed.line)
    }

    @Test
    fun typeLeavesOrdinaryTextInTheField() {
        val inTodo = PrefixCommands.Mode(prompt = '-', input = "bu")
        val typed = PrefixCommands.type(inTodo, "buy")
        assertEquals('-', typed.prompt)
        assertEquals("buy", typed.input)
    }

    @Test
    fun defaultPromptIsNotPartOfTheLine() {
        val mode = PrefixCommands.Mode(input = "Signal")
        assertEquals('>', mode.prompt)
        assertEquals("Signal", mode.line)
    }

    @Test
    fun emptyBackspaceLeavesAModePromptUntilCleared() {
        val emptyTodo = PrefixCommands.Mode(prompt = '-', input = "")
        assertEquals(emptyTodo, PrefixCommands.type(emptyTodo, ""))
        assertEquals(PrefixCommands.Mode(), PrefixCommands.clearMode(emptyTodo))
    }
}
