package xyz.cdr.builderlauncher.stocks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class StockHit(
    val symbol: String,
    val name: String,
    val type: String = "",
    val exchange: String = "",
)

data class StockPoint(
    val time: Long,
    val close: Double,
)

data class StockQuote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val currency: String = "USD",
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long? = null,
    val week52High: Double? = null,
    val week52Low: Double? = null,
    val pe: Double? = null,
    val marketCap: Double? = null,
    val dividendYield: Double? = null,
    val eps: Double? = null,
    val beta: Double? = null,
    val avgVolume: Long? = null,
) {
    val up: Boolean get() = change >= 0
}

data class StockChartData(
    val quote: StockQuote,
    val points: List<StockPoint>,
    val volumes: List<Long> = emptyList(),
)

data class StockCagr(
    val y1: Double? = null,
    val y3: Double? = null,
    val y5: Double? = null,
    val y10: Double? = null,
)

data class StockDetails(
    val quote: StockQuote,
    val cagr: StockCagr = StockCagr(),
)

data class StockFundamentals(
    val pe: Double? = null,
    val marketCap: Double? = null,
    val dividendYield: Double? = null,
    val eps: Double? = null,
)

object YahooFinance {
    const val SEARCH_HOST = "https://query1.finance.yahoo.com/v1/finance/search"
    const val CHART_HOST = "https://query1.finance.yahoo.com/v8/finance/chart"
    const val TIMESERIES_HOST = "https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries"
    const val MARKET_SYMBOL = "SPY"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 BuilderLauncher"
    const val TIMESERIES_TYPES =
        "trailingPeRatio,trailingMarketCap,trailingDividendYield,trailingDilutedEPS"

    fun parseSearch(raw: String, limit: Int = 8): List<StockHit> {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val quotes = root["quotes"]?.jsonArray ?: return emptyList()
        return quotes.mapNotNull { el ->
            val obj = el.jsonObject
            val symbol = obj.str("symbol")?.uppercase().orEmpty()
            if (symbol.isBlank()) return@mapNotNull null
            val name = obj.str("shortname") ?: obj.str("longname") ?: obj.str("shortName") ?: symbol
            StockHit(
                symbol = symbol,
                name = name,
                type = obj.str("typeDisp") ?: obj.str("quoteType").orEmpty(),
                exchange = obj.str("exchDisp") ?: obj.str("exchange").orEmpty(),
            )
        }.distinctBy { it.symbol }.take(limit)
    }

    fun parseChart(raw: String): StockChartData? {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val chart = root["chart"]?.jsonObject ?: return null
        if (chart["error"] != null && chart["error"].toString() != "null") return null
        val result = chart["result"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val meta = result["meta"]?.jsonObject ?: return null
        val symbol = meta.str("symbol")?.uppercase().orEmpty()
        if (symbol.isBlank()) return null
        val price = meta.num("regularMarketPrice") ?: return null
        val previous = meta.num("chartPreviousClose") ?: meta.num("previousClose") ?: price
        val change = price - previous
        val percent = if (previous == 0.0) 0.0 else change / previous * 100.0
        val quoteArr = result["indicators"]?.jsonObject?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject
        val opens = quoteArr?.nums("open")
        val highs = quoteArr?.nums("high")
        val lows = quoteArr?.nums("low")
        val closes = quoteArr?.nums("close")
        val volumes = quoteArr?.longs("volume")
        val timestamps = result["timestamp"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }.orEmpty()
        val points = timestamps.zip(closes.orEmpty()) { t, c ->
            if (c == null) null else StockPoint(t, c)
        }.filterNotNull()
        val dayOpen = meta.num("regularMarketOpen") ?: opens?.firstOrNull { it != null }
        val dayHigh = meta.num("regularMarketDayHigh") ?: highs?.filterNotNull()?.maxOrNull()
        val dayLow = meta.num("regularMarketDayLow") ?: lows?.filterNotNull()?.minOrNull()
        val volume = meta.long("regularMarketVolume") ?: volumes?.filterNotNull()?.lastOrNull()
        return StockChartData(
            quote = StockQuote(
                symbol = symbol,
                name = meta.str("shortName") ?: meta.str("longName") ?: symbol,
                price = price,
                previousClose = previous,
                change = change,
                changePercent = percent,
                currency = meta.str("currency") ?: "USD",
                open = dayOpen,
                high = dayHigh,
                low = dayLow,
                volume = volume,
                week52High = meta.num("fiftyTwoWeekHigh"),
                week52Low = meta.num("fiftyTwoWeekLow"),
            ),
            points = points,
            volumes = volumes?.filterNotNull().orEmpty(),
        )
    }

    fun parseTimeseries(raw: String): StockFundamentals {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return StockFundamentals()
        val result = root["timeseries"]?.jsonObject?.get("result")?.jsonArray ?: return StockFundamentals()
        val values = mutableMapOf<String, Double>()
        result.forEach { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@forEach
            val type = obj["meta"]?.jsonObject?.get("type")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: obj.keys.firstOrNull { it != "meta" && it != "timestamp" }
                ?: return@forEach
            latestNumber(obj)?.let { values[type] = it }
        }
        return StockFundamentals(
            pe = values["trailingPeRatio"],
            marketCap = values["trailingMarketCap"],
            dividendYield = values["trailingDividendYield"],
            eps = values["trailingDilutedEPS"],
        )
    }

    private fun latestNumber(obj: JsonObject): Double? {
        val key = obj.keys.firstOrNull { it != "meta" && it != "timestamp" } ?: return null
        val arr = obj[key]?.jsonArray ?: return null
        val last = arr.lastOrNull()?.jsonObject ?: return null
        val reported = last["reportedValue"]?.jsonObject
        return reported?.num("raw") ?: last.num("dataValue") ?: last.num("raw")
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.num(key: String): Double? {
        val prim = this[key]?.jsonPrimitive ?: return null
        return prim.doubleOrNull ?: prim.contentOrNull?.toDoubleOrNull()
    }

    private fun JsonObject.long(key: String): Long? {
        val prim = this[key]?.jsonPrimitive ?: return null
        return prim.longOrNull ?: prim.doubleOrNull?.toLong() ?: prim.contentOrNull?.toDoubleOrNull()?.toLong()
    }

    private fun JsonObject.nums(key: String): List<Double?> {
        val arr = this[key]?.jsonArray ?: return emptyList()
        return arr.map { el ->
            val prim = runCatching { el.jsonPrimitive }.getOrNull() ?: return@map null
            if (prim.contentOrNull == "null") null
            else prim.doubleOrNull ?: prim.contentOrNull?.toDoubleOrNull()
        }
    }

    private fun JsonObject.longs(key: String): List<Long?> {
        val arr = this[key]?.jsonArray ?: return emptyList()
        return arr.map { el ->
            val prim = runCatching { el.jsonPrimitive }.getOrNull() ?: return@map null
            if (prim.contentOrNull == "null") null
            else prim.longOrNull ?: prim.doubleOrNull?.toLong() ?: prim.contentOrNull?.toDoubleOrNull()?.toLong()
        }
    }
}
