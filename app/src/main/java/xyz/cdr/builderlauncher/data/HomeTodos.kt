package xyz.cdr.builderlauncher.data

object HomeTodos {
    const val PREVIEW = 3

    fun of(items: List<LocalItem>): List<LocalItem> =
        items.filter { it.kind.equals("todo", ignoreCase = true) }

    fun open(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { !it.done }

    fun completed(todos: List<LocalItem>): List<LocalItem> =
        todos.filter { it.done }.sortedByDescending { it.completedAt ?: 0L }

    fun visibleOpen(open: List<LocalItem>, expanded: Boolean): List<LocalItem> =
        if (expanded) open else open.take(PREVIEW)

    fun visibleDone(done: List<LocalItem>, expanded: Boolean): List<LocalItem> =
        if (expanded) done else done.take(PREVIEW)

    fun hasMore(open: List<LocalItem>): Boolean = open.size > PREVIEW

    fun hasMoreDone(done: List<LocalItem>): Boolean = done.size > PREVIEW
}
