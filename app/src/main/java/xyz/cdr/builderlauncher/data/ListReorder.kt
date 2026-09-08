package xyz.cdr.builderlauncher.data

object ListReorder {
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return items
        if (from !in items.indices || to !in items.indices) return items
        val next = items.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }

    fun targetIndex(from: Int, displacement: Float, step: Float, lastIndex: Int): Int {
        if (step <= 1f || lastIndex < 0) return from
        val shift = kotlin.math.round(displacement / step).toInt()
        return (from + shift).coerceIn(0, lastIndex)
    }

    fun neighborOffset(index: Int, from: Int, to: Int, step: Float): Float =
        when {
            from < to && index in (from + 1)..to -> -step
            from > to && index in to until from -> step
            else -> 0f
        }

    fun <T> liveIndex(items: List<T>, match: (T) -> Boolean): Int = items.indexOfFirst(match)
}
