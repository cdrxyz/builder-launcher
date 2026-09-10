package xyz.cdr.builderlauncher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

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
}
