package xyz.cdr.builderlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.cdr.builderlauncher.data.AccentColor

val Ink = Color(0xFF0B0B0B)
val Paper = Color(0xFFE8E4D9)
val Dim = Color(0xFF8A867C)
val Prompt = Color(0xFF00FF41)
val Gain = Color(AccentColor.argb(AccentColor.DEFAULT_UP_HEX))
val Loss = Color(AccentColor.argb(AccentColor.DEFAULT_DOWN_HEX))
val Line = Color(0xFF2A2A2A)

val LocalAccent = staticCompositionLocalOf { Prompt }

val Accent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccent.current

fun accentColor(hex: String?): Color = Color(AccentColor.argb(hex))

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
fun BuilderTheme(accent: Color = Prompt, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = Ink,
        surface = Ink,
        onBackground = Paper,
        onSurface = Paper,
        primary = accent,
        onPrimary = Ink,
        secondary = Dim,
        outline = Line,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = colors,
            typography = Type,
            content = content,
        )
    }
}
