package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test
    fun catalogIsAlphabetical() {
        assertEquals(
            listOf("apps", "clock", "help", "hub", "notes", "settings", "stocks", "usage", "weather"),
            SlashCommands.all.map { it.name },
        )
        assertTrue(SlashCommands.all.map { it.name } == SlashCommands.all.map { it.name }.sorted())
    }

    @Test
    fun emptyQueryListsEveryCommand() {
        assertEquals(SlashCommands.all, SlashCommands.matches(""))
        assertEquals(SlashCommands.all, SlashCommands.matches("  "))
    }

    @Test
    fun matchesPrefix() {
        assertEquals(listOf("clock"), SlashCommands.matches("c").map { it.name })
        assertEquals(listOf("help", "hub"), SlashCommands.matches("h").map { it.name })
        assertEquals(listOf("settings"), SlashCommands.matches("set").map { it.name })
        assertEquals(emptyList<SlashCommand>(), SlashCommands.matches("zzz"))
    }

    @Test
    fun exactIgnoresCase() {
        assertEquals(Command.OpenSettings, SlashCommands.exact("Settings")?.command)
        assertNull(SlashCommands.exact("set"))
        assertNull(SlashCommands.exact(""))
    }

    @Test
    fun resolvePrefersExactThenUniquePrefix() {
        assertEquals("settings", SlashCommands.resolve("settings")?.name)
        assertEquals("settings", SlashCommands.resolve("set")?.name)
        assertNull(SlashCommands.resolve("h"))
        assertEquals("help", SlashCommands.resolve("he")?.name)
        assertNull(SlashCommands.resolve(""))
    }
}
