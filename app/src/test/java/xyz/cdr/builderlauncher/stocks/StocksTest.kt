package xyz.cdr.builderlauncher.stocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.cdr.builderlauncher.data.StockInsert

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
        assertEquals("36.61", Stocks.formatRatio(36.61))
        assertEquals("$4.7T", Stocks.formatMarketCap(4.67e12))
        assertEquals("0.34%", Stocks.formatYield(0.0034))
        assertEquals("—", Stocks.formatYield(null))
        assertEquals("53.8M", Stocks.formatVolume(Stocks.avgVolume(listOf(50_000_000, 57_600_000))!!))
    }

    @Test
    fun chartScrubMapsXDateAndBaseline() {
        val points = listOf(
            StockPoint(1_700_000_000L, 100.0),
            StockPoint(1_700_000_300L, 110.0),
            StockPoint(1_700_000_600L, 90.0),
        )
        assertEquals(0, Stocks.indexAt(0f, 100f, 3))
        assertEquals(1, Stocks.indexAt(50f, 100f, 3))
        assertEquals(2, Stocks.indexAt(100f, 100f, 3))
        assertEquals(0, Stocks.indexAt(-10f, 100f, 3))
        assertEquals(2, Stocks.indexAt(999f, 100f, 3))
        assertEquals(0, Stocks.indexAt(0f, 0f, 3))
        assertEquals(100.0, Stocks.scrubBaseline(points, StockRange.M1, previousClose = 95.0))
        assertEquals(95.0, Stocks.scrubBaseline(points, StockRange.D1, previousClose = 95.0))
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("10:13 PM", Stocks.formatChartTime(1_700_000_000L, StockRange.D1, zone = utc))
        assertEquals("Tue 10:13 PM", Stocks.formatChartTime(1_700_000_000L, StockRange.W1, zone = utc))
        assertEquals("Nov 14", Stocks.formatChartTime(1_700_000_000L, StockRange.M1, zone = utc))
        assertEquals("Nov 14, 2023", Stocks.formatChartTime(1_700_000_000L, StockRange.Y1, zone = utc))
    }

    @Test
    fun cagrAndPerformance() {
        assertEquals(0.10, Stocks.cagr(100.0, 121.0, 2.0)!!, 0.0001)
        assertNull(Stocks.cagr(0.0, 121.0, 2.0))
        val year = (365.25 * 86_400).toLong()
        val now = 1_800_000_000L
        val points = listOf(
            StockPoint(now - 10 * year, 46.65),
            StockPoint(now - 5 * year, 75.13),
            StockPoint(now - 3 * year, 90.96),
            StockPoint(now - year, 110.0),
            StockPoint(now, 121.0),
        )
        val cagr = Stocks.performance(points, 121.0, now)
        assertEquals(10.0, cagr.y1!!, 0.2)
        assertEquals(10.0, cagr.y10!!, 0.2)
        val stats = Stocks.quoteStats(
            StockQuote(
                symbol = "AAPL",
                name = "Apple",
                price = 121.0,
                previousClose = 110.0,
                change = 11.0,
                changePercent = 10.0,
                pe = 36.61,
                marketCap = 4.67e12,
                dividendYield = 0.0034,
                eps = 8.74,
                beta = 1.09,
                avgVolume = 53_800_000,
            ),
        )
        assertEquals("P/E", stats[2].leftLabel)
        assertEquals("36.61", stats[2].leftValue)
        assertEquals("$4.7T", stats[2].rightValue)
        assertEquals("0.34%", stats[3].rightValue)
        assertEquals(listOf("1Y", "3Y"), Stocks.cagrStats(cagr).first().let { listOf(it.leftLabel, it.rightLabel) })
    }

    @Test
    fun betaFollowsScaledMarket() {
        val week = 7L * 86_400L
        val now = 1_800_000_000L
        val market = (0..40).map { i ->
            StockPoint(now - (40 - i) * week, 100.0 + i)
        }
        val stock = market.map { StockPoint(it.time, it.close * 2) }
        val beta = Stocks.beta(stock, market, now)!!
        assertEquals(1.0, beta, 0.05)
    }

    @Test
    fun placePutsNewAtTopOrBottom() {
        val a = WatchItem("AAPL", "Apple")
        val b = WatchItem("MSFT", "Microsoft")
        val c = WatchItem("GOOG", "Alphabet")
        assertEquals(listOf("GOOG", "AAPL", "MSFT"), Stocks.place(listOf(a, b), c, StockInsert.TOP).map { it.symbol })
        assertEquals(listOf("AAPL", "MSFT", "GOOG"), Stocks.place(listOf(a, b), c, StockInsert.BOTTOM).map { it.symbol })
        assertEquals(
            listOf("NVDA", "AMD", "AAPL"),
            Stocks.placeAll(listOf(a), listOf(WatchItem("NVDA", "n"), WatchItem("AMD", "a")), StockInsert.TOP).map { it.symbol },
        )
        assertEquals(
            listOf("AAPL", "NVDA", "AMD"),
            Stocks.placeAll(listOf(a), listOf(WatchItem("NVDA", "n"), WatchItem("AMD", "a")), StockInsert.BOTTOM).map { it.symbol },
        )
    }

    @Test
    fun moveReordersAndIgnoresOutOfRange() {
        val items = listOf(WatchItem("A", "a"), WatchItem("B", "b"), WatchItem("C", "c"))
        assertEquals(listOf("B", "A", "C"), Stocks.move(items, 0, 1).map { it.symbol })
        assertEquals(listOf("C", "A", "B"), Stocks.move(items, 2, 0).map { it.symbol })
        assertEquals(listOf("A", "B", "C"), Stocks.move(items, 1, 1).map { it.symbol })
        assertEquals(listOf("A", "B", "C"), Stocks.move(items, -1, 0).map { it.symbol })
        assertEquals(listOf("A", "B", "C"), Stocks.move(items, 0, 9).map { it.symbol })
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
        assertEquals(listOf(100L, 200L, 300L), chart.volumes)
        assertNull(chart.quote.extendedLabel)
        assertNull(chart.quote.extendedPrice)
    }

    @Test
    fun parsesPreMarketWhenAvailable() {
        val raw = """
            {"chart":{"result":[{
              "meta":{
                "currency":"USD",
                "symbol":"AAPL",
                "shortName":"Apple Inc.",
                "regularMarketPrice":319.97,
                "chartPreviousClose":328.21,
                "hasPrePostMarketData":true,
                "fulldayPrice":318.55,
                "fulldayChange":-1.42,
                "fulldayChangePercent":-0.444,
                "currentTradingPeriod":{
                  "pre":{"start":100,"end":200},
                  "regular":{"start":200,"end":300},
                  "post":{"start":300,"end":400}
                }
              },
              "timestamp":[1,2],
              "indicators":{"quote":[{"close":[319.97, 318.55]}]}
            }],"error":null}}
        """.trimIndent()
        val pre = YahooFinance.parseChart(raw, nowSec = 150)!!.quote
        assertEquals("Pre-Market", pre.extendedLabel)
        assertEquals(318.55, pre.extendedPrice!!, 0.001)
        assertEquals(-1.42, pre.extendedChange!!, 0.001)
        assertEquals("Pre-Market $318.55 -1.42 (-0.44%)", Stocks.formatExtended(pre))
        val after = YahooFinance.parseChart(raw, nowSec = 350)!!.quote
        assertEquals("After Hours", after.extendedLabel)
        assertEquals("After Hours $318.55 -1.42 (-0.44%)", Stocks.formatExtended(after))
        val regular = YahooFinance.parseChart(raw, nowSec = 250)!!.quote
        assertNull(regular.extendedLabel)
        assertNull(Stocks.formatExtended(regular))
    }

    @Test
    fun skipsExtendedWhenFeedHasNone() {
        val raw = """
            {"chart":{"result":[{
              "meta":{
                "currency":"GBp",
                "symbol":"SHEL.L",
                "shortName":"Shell PLC",
                "regularMarketPrice":3494.5,
                "chartPreviousClose":3483.0,
                "hasPrePostMarketData":false,
                "fulldayPrice":3494.5,
                "fulldayChange":11.5,
                "fulldayChangePercent":0.33,
                "currentTradingPeriod":{
                  "pre":{"start":100,"end":200},
                  "regular":{"start":200,"end":300},
                  "post":{"start":300,"end":400}
                }
              },
              "timestamp":[1],
              "indicators":{"quote":[{"close":[3494.5]}]}
            }],"error":null}}
        """.trimIndent()
        val quote = YahooFinance.parseChart(raw, nowSec = 150)!!.quote
        assertNull(quote.extendedLabel)
        assertNull(Stocks.formatExtended(quote))
    }

    @Test
    fun parsesTimeseriesFundamentals() {
        val raw = """
            {"timeseries":{"result":[
              {"meta":{"symbol":["AAPL"],"type":["trailingDividendYield"]},"trailingDividendYield":[{"dataValue":0.0033}]},
              {"meta":{"symbol":["AAPL"],"type":["trailingMarketCap"]},"trailingMarketCap":[{"reportedValue":{"raw":4669700046848.0,"fmt":"4.67T"}}]},
              {"meta":{"symbol":["AAPL"],"type":["trailingPeRatio"]},"trailingPeRatio":[{"reportedValue":{"raw":36.61,"fmt":"36.61"}}]},
              {"meta":{"symbol":["AAPL"],"type":["trailingDilutedEPS"]},"trailingDilutedEPS":[{"reportedValue":{"raw":8.74,"fmt":"8.74"}}]}
            ],"error":null}}
        """.trimIndent()
        val stats = YahooFinance.parseTimeseries(raw)
        assertEquals(36.61, stats.pe!!, 0.001)
        assertEquals(4669700046848.0, stats.marketCap!!, 1.0)
        assertEquals(0.0033, stats.dividendYield!!, 0.00001)
        assertEquals(8.74, stats.eps!!, 0.001)
    }

    @Test
    fun timeseriesEmptyOnJunk() {
        val empty = YahooFinance.parseTimeseries("not-json")
        assertNull(empty.pe)
        assertNull(empty.marketCap)
    }

    @Test
    fun chartNullOnError() {
        assertNull(YahooFinance.parseChart("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        assertNull(YahooFinance.parseChart("not-json"))
    }
}

class StocksCsvTest {
    @Test
    fun exportsExchangeTickerName() {
        val csv = StocksCsv.export(
            listOf(
                WatchItem("AAPL", "Apple Inc.", exchange = "NASDAQ"),
                WatchItem("MSFT", "Microsoft Corporation", exchange = "NASDAQ"),
            ),
        )
        assertEquals(
            """
            Exchange,Ticker,Name
            NASDAQ,AAPL,Apple Inc.
            NASDAQ,MSFT,Microsoft Corporation
            """.trimIndent(),
            csv,
        )
    }

    @Test
    fun parsesOwnExport() {
        val hits = StocksCsv.parse(
            """
            Exchange,Ticker,Name
            NASDAQ,AAPL,Apple Inc.
            NASDAQ,MSFT,Microsoft Corporation
            """.trimIndent(),
        )
        assertEquals(listOf("AAPL", "MSFT"), hits.map { it.symbol })
        assertEquals("NASDAQ", hits[0].exchange)
        assertEquals("Apple Inc.", hits[0].name)
    }

    @Test
    fun parsesAppleStocksExport() {
        val hits = StocksCsv.parse(
            """
            Symbol,Name,Price,Change,Change%
            AAPL,Apple Inc.,203.96,+1.23,+0.61%
            MSFT,Microsoft Corporation,378.91,-1.45,-0.38%
            """.trimIndent(),
        )
        assertEquals(listOf("AAPL", "MSFT"), hits.map { it.symbol })
        assertEquals("Apple Inc.", hits[0].name)
        assertEquals("", hits[0].exchange)
    }

    @Test
    fun parsesTickerPerLine() {
        val hits = StocksCsv.parse("aapl\nmsft\nnot a ticker")
        assertEquals(listOf("AAPL", "MSFT"), hits.map { it.symbol })
    }

    @Test
    fun quotesCompanyNamesWithCommas() {
        val csv = StocksCsv.export(listOf(WatchItem("BRK.B", "Berkshire Hathaway, Inc.", exchange = "NYSE")))
        assertTrue(csv.contains("\"Berkshire Hathaway, Inc.\""))
        val hits = StocksCsv.parse(csv)
        assertEquals("Berkshire Hathaway, Inc.", hits.single().name)
        assertEquals("BRK.B", hits.single().symbol)
    }
}

