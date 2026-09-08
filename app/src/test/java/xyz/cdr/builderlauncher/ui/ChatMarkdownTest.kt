package xyz.cdr.builderlauncher.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {
    @Test
    fun parsesHeadingsListsAndCode() {
        val src = """
            # Title

            A paragraph with **bold** and `code`.

            - one
            - two

            1. first
            2. second

            ```kotlin
            val x = 1
            ```

            ---
        """.trimIndent()
        val blocks = parseMarkdown(src)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals("Title", (blocks[0] as MdBlock.Heading).text)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.ListBlock)
        assertEquals(listOf("one", "two"), (blocks[2] as MdBlock.ListBlock).items)
        assertTrue(blocks[3] is MdBlock.ListBlock)
        assertTrue((blocks[3] as MdBlock.ListBlock).ordered)
        assertTrue(blocks[4] is MdBlock.Code)
        assertEquals("val x = 1", (blocks[4] as MdBlock.Code).text)
        assertEquals(MdBlock.Rule, blocks[5])
    }

    @Test
    fun parsesGithubTables() {
        val src = """
            | Lang | GC |
            | --- | --- |
            | Kotlin | yes |
            | Rust | no |
        """.trimIndent()
        val table = parseMarkdown(src).single() as MdBlock.Table
        assertEquals(listOf("Lang", "GC"), table.headers)
        assertEquals(listOf(listOf("Kotlin", "yes"), listOf("Rust", "no")), table.rows)
    }

    @Test
    fun inlineDropsMarkers() {
        val styled = annotatedInline("**bold** and *italic* and `code`", Color.Green)
        assertEquals("bold and italic and code", styled.text)
        assertTrue(styled.spanStyles.size >= 3)
    }
}
