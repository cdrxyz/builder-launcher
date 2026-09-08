package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefixCommandsTest {
    @Test
    fun fillPrefillsGlyphAndSpace() {
        assertEquals("@ ", PrefixCommands.fill('@'))
        assertEquals("# ", PrefixCommands.fill('#'))
        assertEquals("? ", PrefixCommands.fill('?'))
    }

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
    fun typingWhileMenuOpenClosesAndKeepsTheCharacter() {
        val typed = PrefixCommands.typeWhileOpen("h")
        assertEquals(false, typed.menuOpen)
        assertEquals("h", typed.value)
    }
}
