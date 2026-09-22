package xyz.cdr.builderlauncher.commands

object PromptRouting {
    /**
     * A screen may swallow the bar only for its own mode and the default prompt.
     * Any other mode (todo, ask, slash, text, call) is dispatched as that mode.
     */
    fun screenOwns(screenMode: Char, prompt: Char): Boolean {
        if (prompt == screenMode) return true
        if (prompt == PrefixCommands.DEFAULT_PROMPT) return true
        return !PrefixCommands.isModePrompt(prompt)
    }

    /** Drop an in-progress todo edit only after another command actually runs, or the screen changes. */
    fun abandonTodoEdit(stillOnTodos: Boolean, command: Command): Boolean {
        if (!stillOnTodos) return true
        return command !is Command.Empty && command !is Command.Help
    }
}
