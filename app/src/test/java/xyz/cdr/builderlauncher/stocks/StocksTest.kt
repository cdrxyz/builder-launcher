package xyz.cdr.builderlauncher.stocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StocksTest {
    @Test
    fun moreCopyMatchesNotesLink() {
        assertEquals("… all stocks >", Stocks.MORE)
    }

    @Test
    fun queryMatchesStockPrefixes() {
        assertTrue(Stocks.matchesQuery("st"))
        assertTrue(Stocks.matchesQuery("stock"))
        assertTrue(Stocks.matchesQuery("stocks"))
        assertTrue(Stocks.matchesQuery("STOCKS"))
        assertFalse(Stocks.matchesQuery(""))
        assertFalse(Stocks.matchesQuery("s"))
        assertFalse(Stocks.matchesQuery("hub"))
        assertFalse(Stocks.matchesQuery("stocksapp"))
    }

    @Test
    fun queryStripsDollarPrefix() {
        assertEquals("AAPL", Stocks.queryFromInput("\$AAPL"))
        assertEquals("apple", Stocks.queryFromInput("\$ apple"))
        assertEquals("", Stocks.queryFromInput("$"))
        assertEquals("", Stocks.queryFromInput("$ "))
    }

    @Test
    fun stocksScreenOpensInTickerMode() {
        assertEquals("$ ", Stocks.enterDraft())
        assertEquals("$ ", Stocks.keepDraft(""))
        assertEquals("\$AAPL", Stocks.keepDraft("\$AAPL"))
    }

    @Test
    fun leavingStocksClearsBarePrefix() {
        assertEquals("", Stocks.leaveDraft("$"))
        assertEquals("", Stocks.leaveDraft("  "))
        assertEquals("\$AAPL", Stocks.leaveDraft("\$AAPL"))
    }

    @Test
    fun looksLikeSymbol() {
        assertTrue(Stocks.looksLikeSymbol("AAPL"))
        assertTrue(Stocks.looksLikeSymbol("brk.b"))
        assertTrue(Stocks.looksLikeSymbol("BTC-USD"))
        assertFalse(Stocks.looksLikeSymbol("apple computer"))
        assertFalse(Stocks.looksLikeSymbol(""))
    }

    @Test
    fun formatsPriceChangeAndVolume() {
        assertEquals("$319.97", Stocks.formatPrice(319.97))
        assertEquals("$1,234.50", Stocks.formatPrice(1234.5))
        assertEquals("12.50 EUR", Stocks.formatPrice(12.5, "EUR"))
        assertEquals("+1.24%", Stocks.formatPercent(1.24))
        assertEquals("-2.50%", Stocks.formatPercent(-2.5))
        assertEquals("+8.24", Stocks.formatChange(8.24))
        assertEquals("39.6M", Stocks.formatVolume(39_600_000))
        assertEquals("—", Stocks.formatNumber(null))
        assertEquals("328.21", Stocks.formatNumber(328.21))
    }
}

class YahooFinanceTest {
    @Test
    fun parsesSearchHits() {
        val raw = """
            {"quotes":[
              {"symbol":"AAPL","shortname":"Apple Inc.","quoteType":"EQUITY","typeDisp":"Equity","exchDisp":"NASDAQ"},
              {"symbol":"AAPW","shortname":"Roundhill AAPL WeeklyPay ETF","quoteType":"ETF","typeDisp":"ETF","exchDisp":"BATS Trading"},
              {"shortname":"missing symbol"}
            ]}
        """.trimIndent()
        val hits = YahooFinance.parseSearch(raw)
        assertEquals(listOf("AAPL", "AAPW"), hits.map { it.symbol })
        assertEquals("Apple Inc.", hits[0].name)
        assertEquals("NASDAQ", hits[0].exchange)
    }

    @Test
    fun searchEmptyOnJunk() {
        assertTrue(YahooFinance.parseSearch("{}").isEmpty())
        assertTrue(YahooFinance.parseSearch("not-json").isEmpty())
    }

    @Test
    fun parsesChartQuoteAndPoints() {
        val raw = """
            {"chart":{"result":[{
              "meta":{
                "currency":"USD",
                "symbol":"AAPL",
                "shortName":"Apple Inc.",
                "regularMarketPrice":319.97,
                "chartPreviousClose":328.21,
                "regularMarketDayHigh":328.93,
                "regularMarketDayLow":317.86,
                "regularMarketVolume":39606884,
                "fiftyTwoWeekHigh":344.57,
                "fiftyTwoWeekLow":225.95,
                "regularMarketOpen":328.00
              },
              "timestamp":[1,2,3,4],
              "indicators":{"quote":[{
                "close":[328.0, 322.5, null, 319.97],
                "open":[328.0, 327.0, null, 320.0],
                "high":[329.0, 328.0, null, 321.0],
                "low":[327.0, 321.0, null, 317.86],
                "volume":[100, 200, null, 300]
              }]}
            }],"error":null}}
        """.trimIndent()
        val chart = YahooFinance.parseChart(raw)!!
        assertEquals("AAPL", chart.quote.symbol)
        assertEquals("Apple Inc.", chart.quote.name)
        assertEquals(319.97, chart.quote.price, 0.001)
        assertEquals(328.21, chart.quote.previousClose, 0.001)
        assertTrue(chart.quote.change < 0)
        assertEquals(328.0, chart.quote.open!!, 0.001)
        assertEquals(39606884L, chart.quote.volume)
        assertEquals(listOf(328.0, 322.5, 319.97), chart.points.map { it.close })
        assertEquals(listOf(1L, 2L, 4L), chart.points.map { it.time })
    }

    @Test
    fun chartNullOnError() {
        assertNull(YahooFinance.parseChart("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        assertNull(YahooFinance.parseChart("not-json"))
    }
}
