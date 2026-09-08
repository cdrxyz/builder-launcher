package xyz.cdr.builderlauncher.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Prompt

object MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(styleMarkdown(text.text), OffsetMapping.Identity)
    }
}

fun styleMarkdown(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = text.split('\n')
    lines.forEachIndexed { index, line ->
        appendMarkdownLine(builder, line)
        if (index < lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

private val inline = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|`[^`]+`)")
private val heading = Regex("^(#{1,3})\\s+(.*)$")

private fun appendMarkdownLine(builder: AnnotatedString.Builder, line: String) {
    val match = heading.find(line)
    if (match != null) {
        val start = builder.length
        builder.append(line)
        val size = when (match.groupValues[1].length) {
            1 -> 22.sp
            2 -> 18.sp
            else -> 16.sp
        }
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Medium, fontSize = size, color = Paper),
            start,
            builder.length,
        )
        return
    }
    appendInlines(builder, line)
}

private fun appendInlines(builder: AnnotatedString.Builder, text: String) {
    var last = 0
    inline.findAll(text).forEach { match ->
        builder.append(text.substring(last, match.range.first))
        val start = builder.length
        builder.append(match.value)
        val style = when {
            match.value.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold, color = Paper)
            match.value.startsWith("`") -> SpanStyle(color = Prompt)
            else -> SpanStyle(fontStyle = FontStyle.Italic, color = Paper)
        }
        builder.addStyle(style, start, builder.length)
        last = match.range.last + 1
    }
    builder.append(text.substring(last))
}
