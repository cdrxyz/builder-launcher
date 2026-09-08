package xyz.cdr.builderlauncher.data

object HomeTodos {
    const val PREVIEW = 3
    const val MORE_TASKS = "…more tasks >"

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    fun open(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { !it.done }

    fun completed(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

    fun preview(todos: List<LocalItem>): List<LocalItem> =
        open(todos).take(PREVIEW)
}
