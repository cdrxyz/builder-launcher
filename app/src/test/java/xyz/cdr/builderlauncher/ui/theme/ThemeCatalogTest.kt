package xyz.cdr.builderlauncher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.data.UiTheme
import xyz.cdr.builderlauncher.data.UiTone

class ThemeCatalogTest {
    @Test
    fun plainUsesRoundedAccentChrome() {
        assertEquals(ThemeChrome.PLAIN, ThemeCatalog.Plain.chrome)
        assertFalse(ThemeCatalog.Plain.tui)
        assertEquals(24.dp, ThemeCatalog.Plain.radius)
        assertEquals(8.dp, ThemeCatalog.Material.chartRadius)
        assertEquals(0.dp, ThemeCatalog.Ios.listGap)
        assertTrue(ThemeCatalog.Cyberpunk.tui)
    }

    @Test
    fun eachThemeHasLightAndDark() {
        assertFalse(ThemeCatalog.tokens(UiTheme.CYBERPUNK, UiTone.DARK).light)
        assertTrue(ThemeCatalog.tokens(UiTheme.CYBERPUNK, UiTone.LIGHT).light)
        assertFalse(ThemeCatalog.tokens(UiTheme.PLAIN, UiTone.DARK).light)
        assertTrue(ThemeCatalog.tokens(UiTheme.PLAIN, UiTone.LIGHT).light)
        assertTrue(ThemeCatalog.tokens(UiTheme.MATERIAL, UiTone.LIGHT).light)
        assertFalse(ThemeCatalog.tokens(UiTheme.MATERIAL, UiTone.DARK).light)
        assertTrue(ThemeCatalog.tokens(UiTheme.IOS, UiTone.LIGHT).light)
        assertFalse(ThemeCatalog.tokens(UiTheme.IOS, UiTone.DARK).light)
        assertEquals(ThemeChrome.TUI, ThemeCatalog.tokens(UiTheme.CYBERPUNK, UiTone.LIGHT).chrome)
        assertEquals(ThemeChrome.IOS, ThemeCatalog.tokens(UiTheme.IOS, UiTone.DARK).chrome)
    }
}
