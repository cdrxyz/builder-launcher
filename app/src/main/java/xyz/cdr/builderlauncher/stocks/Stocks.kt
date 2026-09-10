package xyz.cdr.builderlauncher.stocks

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt
import xyz.cdr.builderlauncher.data.StockInsert

object Stocks {
    const val MORE = "… all stocks >"
    const val BACK = "<"
    const val PREFIX = "$"
    const val COMMAND = "stocks"
    const val MAX = 100
    const val QUOTE_MS = 15 * 60_000L
    const val HOME_QUOTE_MS = QUOTE_MS

    fun quoteIntervalMs(onHome: Boolean): Long = if (onHome) HOME_QUOTE_MS else QUOTE_MS

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

    fun indexAt(x: Float, width: Float, count: Int): Int {
        if (count <= 1 || width <= 0f) return 0
        val t = (x / width).coerceIn(0f, 1f)
        return (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    fun scrubBaseline(points: List<StockPoint>, range: StockRange, previousClose: Double?): Double? {
        if (range == StockRange.D1) {
            previousClose?.takeIf { it > 0.0 }?.let { return it }
        }
        return points.firstOrNull()?.close?.takeIf { it > 0.0 }
    }

    fun formatChartTime(
        timeSec: Long,
        range: StockRange,
        locale: Locale = Locale.US,
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val pattern = when (range) {
            StockRange.D1 -> "h:mm a"
            StockRange.W1 -> "EEE h:mm a"
            StockRange.M1, StockRange.M3 -> "MMM d"
            StockRange.Y1, StockRange.Y5 -> "MMM d, yyyy"
        }
        val fmt = SimpleDateFormat(pattern, locale)
        fmt.timeZone = zone
        return fmt.format(Date(timeSec * 1000L))
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

    fun place(items: List<WatchItem>, item: WatchItem, insert: StockInsert): List<WatchItem> =
        when (insert) {
            StockInsert.TOP -> listOf(item) + items
            StockInsert.BOTTOM -> items + item
        }

    fun placeAll(
        items: List<WatchItem>,
        added: List<WatchItem>,
        insert: StockInsert,
    ): List<WatchItem> =
        when (insert) {
            StockInsert.TOP -> added + items
            StockInsert.BOTTOM -> items + added
        }

    fun move(items: List<WatchItem>, from: Int, to: Int): List<WatchItem> =
        xyz.cdr.builderlauncher.data.ListReorder.move(items, from, to)

    fun formatNumber(value: Double?): String {
        if (value == null) return "—"
        return if (kotlin.math.abs(value) >= 1000) {
            "%,.2f".format(java.util.Locale.US, value)
        } else {
            "%.2f".format(java.util.Locale.US, value)
        }
    }

    fun formatRatio(value: Double?, decimals: Int = 2): String {
        if (value == null) return "—"
        return "%.${decimals}f".format(java.util.Locale.US, value)
    }

    fun formatCompact(value: Double?, prefix: String = ""): String {
        if (value == null) return "—"
        val abs = kotlin.math.abs(value)
        val (scaled, suffix) = when {
            abs >= 1_000_000_000_000 -> value / 1_000_000_000_000.0 to "T"
            abs >= 1_000_000_000 -> value / 1_000_000_000.0 to "B"
            abs >= 1_000_000 -> value / 1_000_000.0 to "M"
            abs >= 1_000 -> value / 1_000.0 to "K"
            else -> return prefix + formatNumber(value)
        }
        return prefix + "%.1f%s".format(java.util.Locale.US, scaled, suffix)
    }

    fun formatMarketCap(value: Double?): String = formatCompact(value, "$")

    fun formatYield(ratio: Double?): String {
        if (ratio == null) return "—"
        val percent = if (kotlin.math.abs(ratio) <= 1.0) ratio * 100.0 else ratio
        return "%.2f%%".format(java.util.Locale.US, percent)
    }

    fun cagr(start: Double, end: Double, years: Double): Double? {
        if (start <= 0.0 || end <= 0.0 || years <= 0.0) return null
        return (end / start).pow(1.0 / years) - 1.0
    }

    fun closeBefore(points: List<StockPoint>, target: Long, maxSkewSec: Long = 21L * 86_400L): StockPoint? {
        val hit = points.filter { it.time <= target }.maxByOrNull { it.time } ?: return null
        if (target - hit.time > maxSkewSec) return null
        return hit
    }

    fun performance(points: List<StockPoint>, endPrice: Double, nowSec: Long = points.lastOrNull()?.time ?: 0L): StockCagr {
        if (points.size < 2 || endPrice <= 0.0 || nowSec <= 0L) return StockCagr()
        fun at(years: Double): Double? {
            val target = nowSec - (years * YEAR_SEC).toLong()
            val start = closeBefore(points, target) ?: return null
            val actualYears = (nowSec - start.time) / YEAR_SEC
            if (actualYears < years * 0.85) return null
            return cagr(start.close, endPrice, actualYears)?.times(100.0)
        }
        return StockCagr(y1 = at(1.0), y3 = at(3.0), y5 = at(5.0), y10 = at(10.0))
    }

    fun beta(stock: List<StockPoint>, market: List<StockPoint>, nowSec: Long = stock.lastOrNull()?.time ?: 0L): Double? {
        if (stock.size < 30 || market.size < 30 || nowSec <= 0L) return null
        val cutoff = nowSec - (5.0 * YEAR_SEC).toLong()
        val stockRet = weeklyReturns(stock.filter { it.time >= cutoff })
        val marketRet = weeklyReturns(market.filter { it.time >= cutoff })
        val keys = stockRet.keys.intersect(marketRet.keys).sorted()
        if (keys.size < 26) return null
        val xs = keys.map { marketRet.getValue(it) }
        val ys = keys.map { stockRet.getValue(it) }
        val xMean = xs.average()
        val yMean = ys.average()
        var cov = 0.0
        var varX = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - xMean
            cov += dx * (ys[i] - yMean)
            varX += dx * dx
        }
        if (varX == 0.0) return null
        return cov / varX
    }

    fun avgVolume(volumes: List<Long>): Long? {
        if (volumes.isEmpty()) return null
        return volumes.average().toLong()
    }

    fun formatExtended(quote: StockQuote): String? {
        val label = quote.extendedLabel?.takeIf { it.isNotBlank() } ?: return null
        val extendedPrice = quote.extendedPrice ?: return null
        val price = formatPrice(extendedPrice, quote.currency)
        val change = quote.extendedChange
        val percent = quote.extendedPercent
        return when {
            change != null && percent != null ->
                "$label $price ${formatChange(change)} (${formatPercent(percent)})"
            else -> "$label $price"
        }
    }

    fun quoteStats(quote: StockQuote): List<StockStatLine> = listOf(
        StockStatLine("Open", formatNumber(quote.open), "High", formatNumber(quote.high)),
        StockStatLine("Low", formatNumber(quote.low), "Vol", quote.volume?.let { formatVolume(it) } ?: "—"),
        StockStatLine("P/E", formatRatio(quote.pe), "Mkt Cap", formatMarketCap(quote.marketCap)),
        StockStatLine("EPS", formatRatio(quote.eps), "Yield", formatYield(quote.dividendYield)),
        StockStatLine("Beta", formatRatio(quote.beta), "Avg Vol", quote.avgVolume?.let { formatVolume(it) } ?: "—"),
        StockStatLine("52W H", formatNumber(quote.week52High), "52W L", formatNumber(quote.week52Low)),
    )

    fun cagrStats(cagr: StockCagr): List<StockStatLine> = listOf(
        StockStatLine("1Y", cagr.y1?.let { formatPercent(it) } ?: "—", "3Y", cagr.y3?.let { formatPercent(it) } ?: "—"),
        StockStatLine("5Y", cagr.y5?.let { formatPercent(it) } ?: "—", "10Y", cagr.y10?.let { formatPercent(it) } ?: "—"),
    )

    private fun weeklyReturns(points: List<StockPoint>): Map<Long, Double> {
        val sorted = points.sortedBy { it.time }
        if (sorted.size < 2) return emptyMap()
        val out = mutableMapOf<Long, Double>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1].close
            val cur = sorted[i].close
            if (prev > 0.0) out[sorted[i].time / WEEK_SEC] = cur / prev - 1.0
        }
        return out
    }

    private const val YEAR_SEC = 365.25 * 86_400.0
    private const val WEEK_SEC = 7L * 86_400L
}

data class StockStatLine(
    val leftLabel: String,
    val leftValue: String,
    val rightLabel: String,
    val rightValue: String,
)

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
