package xyz.cdr.builderlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMarkdownTest {
    @Test
    fun stylingPreservesCharactersForCursorMapping() {
        val src = "# Title\n\n**bold** and *italic* and `code`\n- list"
        val styled = styleMarkdown(src)
        assertEquals(src, styled.text)
        assertEquals(src.length, styled.length)
        assertTrue(styled.spanStyles.isNotEmpty())
    }
}
