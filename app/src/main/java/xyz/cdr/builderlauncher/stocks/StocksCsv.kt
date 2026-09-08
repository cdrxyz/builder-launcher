package xyz.cdr.builderlauncher.stocks

object StocksCsv {
    const val HEADER = "Exchange,Ticker,Name"

    fun export(items: List<WatchItem>): String {
        val rows = buildList {
            add(HEADER)
            items.forEach { item ->
                add(
                    listOf(item.exchange, item.symbol, item.name).joinToString(",") { escape(it) },
                )
            }
        }
        return rows.joinToString("\n")
    }

    fun parse(raw: String): List<StockHit> {
        val lines = raw.lineSequence()
            .map { it.trim().trimStart('\uFEFF') }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()
        val header = split(lines.first())
        val mapped = columnMap(header)
        val body = if (mapped != null) lines.drop(1) else lines
        return body.mapNotNull { line ->
            val cols = split(line)
            if (cols.isEmpty()) return@mapNotNull null
            val hit = if (mapped != null) {
                val symbol = mapped.ticker?.let { cols.getOrNull(it) }.orEmpty()
                val name = mapped.name?.let { cols.getOrNull(it) }.orEmpty()
                val exchange = mapped.exchange?.let { cols.getOrNull(it) }.orEmpty()
                StockHit(symbol = symbol, name = name, exchange = exchange)
            } else {
                unheadered(cols)
            }
            val symbol = hit.symbol.trim().uppercase()
            if (!Stocks.looksLikeSymbol(symbol)) return@mapNotNull null
            hit.copy(
                symbol = symbol,
                name = hit.name.trim().ifBlank { symbol },
                exchange = hit.exchange.trim(),
            )
        }.distinctBy { it.symbol }
    }

    fun looksLikeList(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        if (text.contains('\n')) return true
        val first = split(text.lineSequence().first())
        return columnMap(first) != null || first.size >= 2
    }

    private data class Columns(val exchange: Int?, val ticker: Int?, val name: Int?)

    private fun columnMap(header: List<String>): Columns? {
        if (header.isEmpty()) return null
        fun idx(vararg names: String): Int? {
            val want = names.map { it.lowercase() }.toSet()
            return header.indexOfFirst { it.trim().lowercase() in want }.takeIf { it >= 0 }
        }
        val ticker = idx("ticker", "symbol", "sym", "ticker symbol")
        val name = idx("name", "company", "company name", "shortname")
        val exchange = idx("exchange", "exch", "exchdisp", "market")
        if (ticker == null && name == null && exchange == null) return null
        if (ticker == null && header.size == 1) return null
        return Columns(exchange = exchange, ticker = ticker ?: 0, name = name)
    }

    private fun unheadered(cols: List<String>): StockHit {
        return when (cols.size) {
            1 -> StockHit(symbol = cols[0], name = cols[0])
            2 -> {
                val a = cols[0]
                val b = cols[1]
                if (Stocks.looksLikeSymbol(a) && !Stocks.looksLikeSymbol(b)) {
                    StockHit(symbol = a, name = b)
                } else if (Stocks.looksLikeSymbol(b)) {
                    StockHit(symbol = b, name = b, exchange = a)
                } else {
                    StockHit(symbol = a, name = b)
                }
            }
            else -> StockHit(symbol = cols[1], name = cols.getOrElse(2) { cols[1] }, exchange = cols[0])
        }
    }

    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }
                (c == ',' || c == ';' || c == '\t') && !quoted -> {
                    out += buf.toString().trim()
                    buf.clear()
                }
                else -> buf.append(c)
            }
            i++
        }
        out += buf.toString().trim()
        return out
    }

    private fun escape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
