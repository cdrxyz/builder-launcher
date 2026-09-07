package xyz.cdr.builderlauncher.data

object HomeTodos {
    const val PREVIEW = 3

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    fun visible(todos: List<LocalItem>, expanded: Boolean): List<LocalItem> =
        if (expanded) todos else todos.take(PREVIEW)

    fun hasMore(todos: List<LocalItem>): Boolean = todos.size > PREVIEW
}
