package xyz.cdr.builderlauncher.stocks

object Stocks {
    const val MORE = "… all stocks >"
    const val BACK = "<"
    const val PREFIX = "$"
    const val COMMAND = "stocks"
    const val MAX = 20

    fun of(items: List<WatchItem>): List<WatchItem> = items

    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.length < 2) return false
        return COMMAND.startsWith(q) || "stock".startsWith(q)
    }

    fun queryFromInput(value: String): String {
        val t = value.trim()
        return if (t.startsWith(PREFIX)) t.drop(1).trim() else t
    }

    fun enterDraft(): String = "$PREFIX "

    fun keepDraft(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.isEmpty() || trimmed == PREFIX) enterDraft() else input
    }

    fun leaveDraft(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.isEmpty() || trimmed == PREFIX) "" else input
    }

    fun looksLikeSymbol(query: String): Boolean {
        val q = query.trim().uppercase()
        if (q.length !in 1..8) return false
        return q.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '^' }
    }

    fun formatPrice(price: Double, currency: String = "USD"): String {
        val amount = if (price >= 1000) {
            "%,.2f".format(java.util.Locale.US, price)
        } else {
            "%.2f".format(java.util.Locale.US, price)
        }
        return if (currency.equals("USD", ignoreCase = true) || currency.isBlank()) {
            "$$amount"
        } else {
            "$amount $currency"
        }
    }

    fun formatChange(change: Double): String {
        val sign = if (change >= 0) "+" else ""
        return sign + "%.2f".format(java.util.Locale.US, change)
    }

    fun formatPercent(percent: Double): String {
        val sign = if (percent >= 0) "+" else ""
        return sign + "%.2f%%".format(java.util.Locale.US, percent)
    }

    fun formatVolume(volume: Long): String {
        val abs = kotlin.math.abs(volume).toDouble()
        return when {
            abs >= 1_000_000_000 -> "%.1fB".format(java.util.Locale.US, volume / 1_000_000_000.0)
            abs >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, volume / 1_000_000.0)
            abs >= 1_000 -> "%.1fK".format(java.util.Locale.US, volume / 1_000.0)
            else -> volume.toString()
        }
    }

    fun formatNumber(value: Double?): String {
        if (value == null) return "—"
        return if (kotlin.math.abs(value) >= 1000) {
            "%,.2f".format(java.util.Locale.US, value)
        } else {
            "%.2f".format(java.util.Locale.US, value)
        }
    }
}

enum class StockRange(
    val label: String,
    val yahooRange: String,
    val interval: String,
) {
    D1("1D", "1d", "5m"),
    W1("1W", "5d", "15m"),
    M1("1M", "1mo", "1d"),
    M3("3M", "3mo", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk"),
    ;

    companion object {
        val default: StockRange = D1
    }
}
