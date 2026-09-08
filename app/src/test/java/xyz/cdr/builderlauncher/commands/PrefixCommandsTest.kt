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
    fun cursorSitsAfterGlyphAndSpace() {
        assertEquals(2, PrefixCommands.cursorAfterFill('@'))
        assertEquals(PrefixCommands.fill('#').length, PrefixCommands.cursorAfterFill('#'))
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
}
