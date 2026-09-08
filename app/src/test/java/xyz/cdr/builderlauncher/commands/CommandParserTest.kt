package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    @Test
    fun empty() {
        assertEquals(Command.Empty, CommandParser.parse("  "))
    }

    @Test
    fun message() {
        val cmd = CommandParser.parse("@jason on my way!")
        assertEquals(Command.Message("jason", "on my way!"), cmd)
    }

    @Test
    fun messageTargetOnly() {
        assertEquals(Command.Message("sam", ""), CommandParser.parse("@sam"))
    }

    @Test
    fun call() {
        assertEquals(Command.Call("lauren"), CommandParser.parse("#lauren"))
        assertEquals(Command.Call("5551234"), CommandParser.parse("# 5551234"))
    }

    @Test
    fun eventWithWhen() {
        val cmd = CommandParser.parse("*dentist mar 24 9a") as Command.Event
        assertEquals("dentist", cmd.title)
        assertTrue(cmd.whenText.lowercase().contains("mar"))
    }

    @Test
    fun eventTitleOnly() {
        assertEquals(Command.Event("dentist", ""), CommandParser.parse("*dentist"))
    }

    @Test
    fun todo() {
        assertEquals(Command.Todo("buy milk"), CommandParser.parse("-buy milk"))
    }

    @Test
    fun note() {
        assertEquals(Command.Note("ship it"), CommandParser.parse("+ship it"))
    }

    @Test
    fun ask() {
        assertEquals(Command.Ask("weather tomorrow"), CommandParser.parse("?weather tomorrow"))
    }

    @Test
    fun launch() {
        assertEquals(Command.LaunchApp("Signal"), CommandParser.parse("Signal"))
    }

    @Test
    fun builtins() {
        assertEquals(Command.OpenSettings, CommandParser.parse("settings"))
        assertEquals(Command.OpenSettings, CommandParser.parse("/settings"))
        assertEquals(Command.OpenHub, CommandParser.parse("hub"))
        assertEquals(Command.OpenHub, CommandParser.parse("/hub"))
        assertEquals(Command.Help, CommandParser.parse("help"))
        assertEquals(Command.Help, CommandParser.parse("/help"))
        assertEquals(Command.Help, CommandParser.parse("?"))
        assertEquals(Command.OpenNotes, CommandParser.parse("notes"))
        assertEquals(Command.OpenNotes, CommandParser.parse("/notes"))
        assertEquals(Command.OpenTodos, CommandParser.parse("todos"))
        assertEquals(Command.OpenTodos, CommandParser.parse("/todos"))
        assertEquals(Command.OpenTodos, CommandParser.parse("/tasks"))
        assertEquals(Command.OpenTodos, CommandParser.parse("tasks"))
    }

    @Test
    fun pinAndUnpin() {
        assertEquals(Command.Pin("Termux"), CommandParser.parse("pin Termux"))
        assertEquals(Command.Unpin("Termux"), CommandParser.parse("unpin Termux"))
        assertEquals(Command.Help, CommandParser.parse("pin"))
        assertEquals(Command.Help, CommandParser.parse("unpin"))
    }
}
