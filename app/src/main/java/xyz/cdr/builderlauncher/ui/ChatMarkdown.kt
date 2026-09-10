package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Accent

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MdBlock()
    data class Code(val language: String, val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data object Rule : MdBlock()
}

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            i++
            continue
        }
        if (trimmed.startsWith("```")) {
            val lang = trimmed.drop(3).trim()
            val body = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks.add(MdBlock.Code(lang, body.toString()))
            continue
        }
        if (isTableRow(trimmed) && i + 1 < lines.size && isTableSep(lines[i + 1].trim())) {
            val headers = tableCells(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && isTableRow(lines[i].trim())) {
                rows.add(tableCells(lines[i].trim()))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }
        if (RULE.matches(trimmed)) {
            blocks.add(MdBlock.Rule)
            i++
            continue
        }
        val heading = HEADING.find(trimmed)
        if (heading != null) {
            blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
            i++
            continue
        }
        if (trimmed.startsWith(">")) {
            val parts = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                parts.add(lines[i].trim().drop(1).trimStart())
                i++
            }
            blocks.add(MdBlock.Quote(parts.joinToString("\n")))
            continue
        }
        val bullet = BULLET.find(trimmed)
        val numbered = NUMBERED.find(trimmed)
        if (bullet != null || numbered != null) {
            val ordered = numbered != null
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                val b = BULLET.find(t)
                val n = NUMBERED.find(t)
                when {
                    ordered && n != null -> items.add(n.groupValues[1])
                    !ordered && b != null -> items.add(b.groupValues[1])
                    else -> break
                }
                i++
            }
            blocks.add(MdBlock.ListBlock(ordered, items))
            continue
        }
        val para = StringBuilder(trimmed)
        i++
        while (i < lines.size) {
            val next = lines[i].trim()
            if (next.isEmpty() || next.startsWith("```") || HEADING.matches(next) ||
                RULE.matches(next) || next.startsWith(">") || BULLET.containsMatchIn(next) ||
                NUMBERED.containsMatchIn(next) || isTableRow(next)
            ) {
                break
            }
            para.append(' ').append(next)
            i++
        }
        blocks.add(MdBlock.Paragraph(para.toString()))
    }
    return blocks
}

fun annotatedInline(
    text: String,
    accent: Color,
    paper: Color = xyz.cdr.builderlauncher.ui.theme.ThemeCatalog.Cyberpunk.paper,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var last = 0
    INLINE.findAll(text).forEach { match ->
        builder.append(text.substring(last, match.range.first))
        val token = match.value
        val start = builder.length
        when {
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                builder.append(token.removeSurrounding("**"))
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = paper), start, builder.length)
            }
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                builder.append(token.removeSurrounding("`"))
                builder.addStyle(SpanStyle(color = accent, fontFamily = FontFamily.Monospace), start, builder.length)
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                builder.append(token.removeSurrounding("*"))
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = paper), start, builder.length)
            }
            token.startsWith("[") -> {
                val label = match.groupValues[1]
                builder.append(label)
                builder.addStyle(SpanStyle(color = accent), start, builder.length)
            }
            else -> builder.append(token)
        }
        last = match.range.last + 1
    }
    builder.append(text.substring(last))
    return builder.toAnnotatedString()
}

@Composable
fun MarkdownDocument(source: String, modifier: Modifier = Modifier) {
    val accent = Accent
    val paper = Paper
    Column(modifier = modifier.fillMaxWidth()) {
        parseMarkdown(source).forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 22.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        annotatedInline(block.text, accent, paper),
                        color = Paper,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = size,
                            lineHeight = (size.value + 6).sp,
                        ),
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        annotatedInline(block.text, accent, paper),
                        color = Paper,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is MdBlock.ListBlock -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        block.items.forEachIndexed { n, item ->
                            val mark = if (block.ordered) "${n + 1}." else "•"
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    mark,
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Text(
                                    annotatedInline(item, accent, paper),
                                    color = Paper,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                is MdBlock.Code -> {
                    Text(
                        block.text.ifBlank { " " },
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                is MdBlock.Table -> MarkdownTable(block, accent, paper)
                is MdBlock.Quote -> {
                    Text(
                        annotatedInline(block.text, accent, paper),
                        color = Dim,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    )
                }
                MdBlock.Rule -> HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, accent: Color, paper: Color) {
    val cols = table.headers.size.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth()) {
        TableRow(table.headers, cols, accent, paper, header = true)
        HorizontalDivider(color = Line)
        table.rows.forEach { row ->
            TableRow(row.padTo(cols), cols, accent, paper, header = false)
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, cols: Int, accent: Color, paper: Color, header: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        cells.take(cols).forEach { cell ->
            Text(
                annotatedInline(cell, accent, paper),
                color = Paper,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }
    }
}

private fun List<String>.padTo(size: Int): List<String> =
    if (this.size >= size) take(size) else this + List(size - this.size) { "" }

private fun isTableRow(line: String): Boolean =
    line.startsWith("|") && line.count { it == '|' } >= 2

private fun isTableSep(line: String): Boolean = TABLE_SEP.matches(line)

private fun tableCells(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
private val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^\\d+[.)]\\s+(.*)$")
private val TABLE_SEP = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
private val INLINE = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\*[^*]+\\*|\\[([^\\]]+)\\]\\([^)]+\\))")
