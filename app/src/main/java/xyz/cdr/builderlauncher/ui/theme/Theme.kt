package xyz.cdr.builderlauncher.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.data.UiTheme

enum class ThemeChrome { TUI, PLAIN, MATERIAL, IOS }

data class ThemeTokens(
    val ink: Color,
    val paper: Color,
    val dim: Color,
    val prompt: Color,
    val line: Color,
    val gain: Color,
    val loss: Color,
    val field: Color,
    val font: FontFamily,
    val light: Boolean,
    val radius: Dp,
    val chrome: ThemeChrome,
) {
    val tui: Boolean get() = chrome == ThemeChrome.TUI
}

object ThemeCatalog {
    val Cyberpunk = ThemeTokens(
        ink = Color(0xFF0B0B0B),
        paper = Color(0xFFE8E4D9),
        dim = Color(0xFF8A867C),
        prompt = Color(0xFF00FF41),
        line = Color(0xFF2A2A2A),
        gain = Color(AccentColor.argb(AccentColor.DEFAULT_UP_HEX)),
        loss = Color(AccentColor.argb(AccentColor.DEFAULT_DOWN_HEX)),
        field = Color(0xFF0B0B0B),
        font = FontFamily.Monospace,
        light = false,
        radius = 0.dp,
        chrome = ThemeChrome.TUI,
    )

    val Plain = ThemeTokens(
        ink = Color(0xFF000000),
        paper = Color(0xFFF5F5F5),
        dim = Color(0xFF9A9A9A),
        prompt = Color(0xFFF5F5F5),
        line = Color(0xFF2A2A2A),
        gain = Color(0xFF34C759),
        loss = Color(0xFFFF3B30),
        field = Color(0xFF000000),
        font = FontFamily.SansSerif,
        light = false,
        radius = 24.dp,
        chrome = ThemeChrome.PLAIN,
    )

    val Material = ThemeTokens(
        ink = Color(0xFFFFFBFE),
        paper = Color(0xFF1C1B1F),
        dim = Color(0xFF49454F),
        prompt = Color(0xFF6750A4),
        line = Color(0xFFCAC4D0),
        gain = Color(0xFF1E8E3E),
        loss = Color(0xFFC5221F),
        field = Color(0xFFE7E0EC),
        font = FontFamily.SansSerif,
        light = true,
        radius = 12.dp,
        chrome = ThemeChrome.MATERIAL,
    )

    val Ios = ThemeTokens(
        ink = Color(0xFFF2F2F7),
        paper = Color(0xFF000000),
        dim = Color(0xFF8E8E93),
        prompt = Color(0xFF007AFF),
        line = Color(0xFFC6C6C8),
        gain = Color(0xFF34C759),
        loss = Color(0xFFFF3B30),
        field = Color(0xFFFFFFFF),
        font = FontFamily.SansSerif,
        light = true,
        radius = 10.dp,
        chrome = ThemeChrome.IOS,
    )

    fun tokens(theme: UiTheme): ThemeTokens = when (theme) {
        UiTheme.CYBERPUNK -> Cyberpunk
        UiTheme.PLAIN -> Plain
        UiTheme.MATERIAL -> Material
        UiTheme.IOS -> Ios
    }
}

val LocalTokens = staticCompositionLocalOf { ThemeCatalog.Cyberpunk }
val LocalAccent = staticCompositionLocalOf { ThemeCatalog.Cyberpunk.prompt }

val Ink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.ink

val Paper: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.paper

val Dim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.dim

val Prompt: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.prompt

val Line: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.line

val Gain: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.gain

val Loss: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTokens.current.loss

val Accent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccent.current

fun accentColor(hex: String?): Color = Color(AccentColor.argb(hex))

private fun typeFor(tokens: ThemeTokens): Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = tokens.font,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = tokens.paper,
    ),
    bodyMedium = TextStyle(
        fontFamily = tokens.font,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = tokens.paper,
    ),
    labelSmall = TextStyle(
        fontFamily = tokens.font,
        fontSize = 12.sp,
        color = tokens.dim,
    ),
    headlineLarge = TextStyle(
        fontFamily = tokens.font,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        color = tokens.paper,
    ),
    titleLarge = TextStyle(
        fontFamily = tokens.font,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        color = tokens.paper,
    ),
)

@Composable
fun Modifier.inputChrome(): Modifier {
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(tokens.radius)
    return when (tokens.chrome) {
        ThemeChrome.TUI -> this
        ThemeChrome.PLAIN -> this
            .border(1.5.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 4.dp)
        ThemeChrome.MATERIAL, ThemeChrome.IOS -> this
            .clip(shape)
            .background(tokens.field)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    }
}

@Composable
fun Modifier.commandBarChrome(): Modifier = inputChrome()

@Composable
fun FieldRule() {
    if (LocalTokens.current.tui) {
        HorizontalDivider(color = Line)
    }
}

@Composable
fun CommandBarRule() {
    if (LocalTokens.current.tui) {
        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun BuilderTheme(
    theme: UiTheme = UiTheme.CYBERPUNK,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val tokens = ThemeCatalog.tokens(theme)
    val resolvedAccent = accent ?: tokens.prompt
    val colors = if (tokens.light) {
        lightColorScheme(
            background = tokens.ink,
            surface = tokens.ink,
            onBackground = tokens.paper,
            onSurface = tokens.paper,
            primary = resolvedAccent,
            onPrimary = tokens.ink,
            secondary = tokens.dim,
            outline = tokens.line,
        )
    } else {
        darkColorScheme(
            background = tokens.ink,
            surface = tokens.ink,
            onBackground = tokens.paper,
            onSurface = tokens.paper,
            primary = resolvedAccent,
            onPrimary = tokens.ink,
            secondary = tokens.dim,
            outline = tokens.line,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = tokens.light
        }
    }
    CompositionLocalProvider(
        LocalTokens provides tokens,
        LocalAccent provides resolvedAccent,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typeFor(tokens),
            content = content,
        )
    }
}
