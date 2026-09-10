package xyz.cdr.builderlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiThemeTest {
    @Test
    fun labelsAndDefault() {
        assertEquals(
            listOf("cyberpunk", "plain", "material", "ios"),
            UiTheme.entries.map { it.label },
        )
        assertEquals(UiTheme.CYBERPUNK, UiTheme.parse(null))
        assertEquals(UiTheme.MATERIAL, UiTheme.parse("material"))
        assertEquals(UiTheme.CYBERPUNK, UiTheme.parse("nope"))
        assertEquals(AccentColor.DEFAULT_HEX, UiTheme.CYBERPUNK.defaultAccentHex)
        assertEquals("#007AFF", UiTheme.IOS.defaultAccentHex)
        assertEquals("#6750A4", UiTheme.MATERIAL.defaultAccentHex)
    }
}
