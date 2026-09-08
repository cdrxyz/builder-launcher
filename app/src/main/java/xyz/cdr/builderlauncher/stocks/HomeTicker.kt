package xyz.cdr.builderlauncher.stocks

data class HomeTickerLine(
    val symbol: String,
    val percent: Double?,
) {
    val up: Boolean get() = (percent ?: 0.0) >= 0.0
    val change: String get() = percent?.let { Stocks.formatPercent(it) } ?: "—"
}

object HomeTicker {
    const val ROTATE_MS = 5_000L

    fun line(
        items: List<WatchItem>,
        quotes: Map<String, StockQuote>,
        index: Int,
    ): HomeTickerLine? {
        if (items.isEmpty()) return null
        val item = items[index.mod(items.size)]
        val percent = quotes[item.symbol]?.changePercent ?: item.changePercent
        return HomeTickerLine(item.symbol, percent)
    }

    fun nextIndex(size: Int, index: Int): Int {
        if (size <= 0) return 0
        return (index + 1).mod(size)
    }
}
