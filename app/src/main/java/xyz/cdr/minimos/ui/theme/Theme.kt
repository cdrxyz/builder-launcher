package xyz.cdr.minimos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val Ink = Color(0xFF0B0B0B)
val Paper = Color(0xFFE8E4D9)
val Dim = Color(0xFF8A867C)
val Prompt = Color(0xFFB7C9A8)
val Line = Color(0xFF2A2A2A)

private val Colors = darkColorScheme(
    background = Ink,
    surface = Ink,
    onBackground = Paper,
    onSurface = Paper,
    primary = Prompt,
    onPrimary = Ink,
    secondary = Dim,
    outline = Line,
)

private val Type = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Paper,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Paper,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = Dim,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        color = Paper,
    ),
)

@Composable
fun MinimosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        content = content,
    )
}
