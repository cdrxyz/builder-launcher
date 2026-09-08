package xyz.cdr.builderlauncher.stocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTickerTest {
    @Test
    fun emptyWatchlistHasNoLine() {
        assertNull(HomeTicker.line(emptyList(), emptyMap(), 0))
    }

    @Test
    fun prefersLiveQuotePercent() {
        val items = listOf(item("AAPL", -1.0), item("MSFT", 2.0))
        val quotes = mapOf("AAPL" to quote("AAPL", -2.51))
        val line = HomeTicker.line(items, quotes, 0)!!
        assertEquals("AAPL", line.symbol)
        assertEquals(-2.51, line.percent!!, 0.0)
        assertEquals("-2.51%", line.change)
        assertFalse(line.up)
    }

    @Test
    fun fallsBackToCachedPercent() {
        val items = listOf(item("MSFT", 1.24))
        val line = HomeTicker.line(items, emptyMap(), 0)!!
        assertEquals("MSFT", line.symbol)
        assertEquals("+1.24%", line.change)
        assertTrue(line.up)
    }

    @Test
    fun wrapsIndexAroundWatchlist() {
        val items = listOf(item("AAPL", -1.0), item("MSFT", 1.0), item("NVDA", 3.0))
        assertEquals("AAPL", HomeTicker.line(items, emptyMap(), 3)!!.symbol)
        assertEquals("MSFT", HomeTicker.line(items, emptyMap(), 4)!!.symbol)
        assertEquals(0, HomeTicker.nextIndex(3, 2))
        assertEquals(1, HomeTicker.nextIndex(3, 0))
        assertEquals(0, HomeTicker.nextIndex(0, 5))
    }

    @Test
    fun missingPercentStillShowsSymbol() {
        val line = HomeTicker.line(listOf(item("IBM", null)), emptyMap(), 0)!!
        assertEquals("IBM", line.symbol)
        assertNull(line.percent)
        assertEquals("—", line.change)
        assertTrue(line.up)
    }

    private fun item(symbol: String, percent: Double?) = WatchItem(
        symbol = symbol,
        name = symbol,
        changePercent = percent,
    )

    private fun quote(symbol: String, percent: Double) = StockQuote(
        symbol = symbol,
        name = symbol,
        price = 100.0,
        previousClose = 100.0,
        change = percent,
        changePercent = percent,
    )
}
