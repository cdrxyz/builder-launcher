package xyz.cdr.builderlauncher.stocks

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class WatchItem(
    val symbol: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis(),
    val price: Double? = null,
    val changePercent: Double? = null,
    val previousClose: Double? = null,
    val currency: String = "USD",
    val exchange: String = "",
)

class StocksRepository(
    context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val file = File(context.applicationContext.filesDir, "watchlist.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _watch = MutableStateFlow(load())
    val watch: StateFlow<List<WatchItem>> = _watch.asStateFlow()
    private val _quotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, StockQuote>> = _quotes.asStateFlow()

    suspend fun search(query: String): List<StockHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = YahooFinance.SEARCH_HOST.toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("quotesCount", "8")
            .addQueryParameter("newsCount", "0")
            .addQueryParameter("listsCount", "0")
            .build()
        runCatching {
            http.newCall(request(url)).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                YahooFinance.parseSearch(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun add(query: String): WatchItem? {
        val q = query.trim()
        if (q.isBlank()) return null
        val current = _watch.value
        val existing = current.firstOrNull { it.symbol.equals(q, ignoreCase = true) }
        if (existing != null) return existing
        if (current.size >= Stocks.MAX) return null
        val hits = search(q)
        val hit = hits.firstOrNull { it.symbol.equals(q, ignoreCase = true) }
            ?: hits.singleOrNull()
            ?: hits.firstOrNull()
        val symbol = (hit?.symbol ?: q.takeIf { Stocks.looksLikeSymbol(it) }?.uppercase()) ?: return null
        if (current.any { it.symbol.equals(symbol, ignoreCase = true) }) {
            return current.first { it.symbol.equals(symbol, ignoreCase = true) }
        }
        val item = WatchItem(symbol = symbol, name = hit?.name ?: symbol, exchange = hit?.exchange.orEmpty())
        persist(listOf(item) + current)
        refreshOne(symbol)
        return item
    }

    fun remove(symbol: String) {
        persist(_watch.value.filterNot { it.symbol.equals(symbol, ignoreCase = true) })
        _quotes.value = _quotes.value - symbol.uppercase()
    }

    suspend fun refreshQuotes() {
        val symbols = _watch.value.map { it.symbol }
        if (symbols.isEmpty()) return
        val fetched = mutableMapOf<String, StockQuote>()
        symbols.chunked(4).forEach { batch ->
            coroutineScope {
                batch.map { symbol ->
                    async { fetchChart(symbol, StockRange.D1)?.quote }
                }.awaitAll().forEach { quote ->
                    if (quote != null) fetched[quote.symbol] = quote
                }
            }
        }
        if (fetched.isEmpty()) return
        _quotes.value = _quotes.value + fetched
        persist(
            _watch.value.map { item ->
                val quote = fetched[item.symbol] ?: return@map item
                item.copy(
                    name = quote.name.ifBlank { item.name },
                    price = quote.price,
                    changePercent = quote.changePercent,
                    previousClose = quote.previousClose,
                    currency = quote.currency,
                )
            },
        )
    }

    suspend fun chart(symbol: String, range: StockRange): StockChartData? = fetchChart(symbol, range)

    private suspend fun refreshOne(symbol: String) {
        val quote = fetchChart(symbol, StockRange.D1)?.quote ?: return
        _quotes.value = _quotes.value + (quote.symbol to quote)
        persist(
            _watch.value.map { item ->
                if (!item.symbol.equals(quote.symbol, ignoreCase = true)) item
                else item.copy(
                    name = quote.name.ifBlank { item.name },
                    price = quote.price,
                    changePercent = quote.changePercent,
                    previousClose = quote.previousClose,
                    currency = quote.currency,
                )
            },
        )
    }

    private suspend fun fetchChart(symbol: String, range: StockRange): StockChartData? =
        withContext(Dispatchers.IO) {
            val url = YahooFinance.CHART_HOST.toHttpUrl().newBuilder()
                .addPathSegment(symbol)
                .addQueryParameter("range", range.yahooRange)
                .addQueryParameter("interval", range.interval)
                .build()
            runCatching {
                http.newCall(request(url)).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    YahooFinance.parseChart(resp.body?.string().orEmpty())
                }
            }.getOrNull()
        }

    private fun request(url: okhttp3.HttpUrl): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", YahooFinance.USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

    private fun persist(next: List<WatchItem>) {
        _watch.value = next
        file.writeText(json.encodeToString(next))
    }

    private fun load(): List<WatchItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<WatchItem>>(file.readText())
        }.getOrDefault(emptyList())
    }
}
