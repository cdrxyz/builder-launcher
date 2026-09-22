package xyz.cdr.builderlauncher.commands

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRoutingTest {
    @Test
    fun stocksKeepsTickerModeAndDefaultPrompt() {
        assertTrue(PromptRouting.screenOwns('$', '$'))
        assertTrue(PromptRouting.screenOwns('$', '>'))
    }

    @Test
    fun taskModeOnStocksIsNotAStockSearch() {
        assertFalse(PromptRouting.screenOwns('$', '-'))
    }

    @Test
    fun askModeOnStocksStaysAPromptNotATicker() {
        assertFalse(PromptRouting.screenOwns('$', '?'))
    }

    @Test
    fun slashOnStocksIsNotATicker() {
        assertFalse(PromptRouting.screenOwns('$', '/'))
    }

    @Test
    fun otherScreensYieldToADifferentMode() {
        assertFalse(PromptRouting.screenOwns('-', '$'))
        assertFalse(PromptRouting.screenOwns('?', '-'))
        assertFalse(PromptRouting.screenOwns('>', '-'))
        assertTrue(PromptRouting.screenOwns('?', '?'))
        assertTrue(PromptRouting.screenOwns('>', '>'))
    }

    @Test
    fun unfinishedTodoEditSurvivesHelpAndEmpty() {
        assertFalse(PromptRouting.abandonTodoEdit(stillOnTodos = true, Command.Help))
        assertFalse(PromptRouting.abandonTodoEdit(stillOnTodos = true, Command.Empty))
    }

    @Test
    fun todoEditDropsOnceAnotherCommandRuns() {
        assertTrue(PromptRouting.abandonTodoEdit(stillOnTodos = true, Command.Ask("hi")))
        assertTrue(PromptRouting.abandonTodoEdit(stillOnTodos = false, Command.Help))
    }
}
