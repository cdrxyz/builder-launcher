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
        assertEquals("#F5F5F5", UiTheme.PLAIN.defaultAccentHex)
        assertEquals("#007AFF", UiTheme.IOS.defaultAccentHex)
        assertEquals("#6750A4", UiTheme.MATERIAL.defaultAccentHex)
        assertEquals(UiTone.DARK, UiTheme.CYBERPUNK.defaultTone)
        assertEquals(UiTone.LIGHT, UiTheme.MATERIAL.defaultTone)
        assertEquals("#111111", UiTheme.PLAIN.defaultAccentHex(UiTone.LIGHT))
        assertEquals("#D0BCFF", UiTheme.MATERIAL.defaultAccentHex(UiTone.DARK))
    }

    @Test
    fun toneParse() {
        assertEquals(listOf("dark", "light"), UiTone.entries.map { it.label })
        assertEquals(UiTone.DARK, UiTone.parse(null))
        assertEquals(UiTone.LIGHT, UiTone.parse("light"))
        assertEquals(UiTone.DARK, UiTone.parse("nope"))
    }
}
