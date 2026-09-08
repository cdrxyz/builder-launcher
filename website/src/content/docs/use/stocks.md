---
title: Stocks
description: Watchlist with live quotes, ticker charts, and CSV import/export.
---

Prefix `$`. Type `stocks`, or tap `… all stocks >`.

![Typing stocks shows the all-stocks shortcut](../../../assets/screenshots/home-stocks.png)

## Watchlist

![Watchlist with ticker, name, price, and today's percent](../../../assets/screenshots/stocks.png)

Each row is ticker, company, last price, and today's percent (green up, red down). Cap is 20 names.

- Tap a row for the chart.
- Delete on the right removes it from the watchlist.
- Long-press and drag a row to reorder. Order is saved on the device.
- Command bar stays in `$` mode. Type a symbol or company name to search.
- Empty list: `Type $AAPL to add a ticker.`
- Gear (top right) opens stocks settings.
- `<` returns home.

## Add a ticker

| You type | Result |
| --- | --- |
| `$` or `stocks` | Open the list |
| `$AAPL` | Search that symbol and add when you pick it |
| `$ apple` | Search by name |
| `stock` / `/stocks` | Same as `stocks` |

App search that looks like "stocks" shows `… all stocks >`.

## Chart and stats

![Ticker detail with 1D chart, timeframes, and stats](../../../assets/screenshots/stock-detail.png)

Timeframes: **1D / 1W / 1M / 3M / 1Y / 5Y**. Stats box: open, high, low, volume, previous close, 52-week high/low, change.

Quotes, search, and charts come from Yahoo Finance. Nothing is sent until you open stocks or search a ticker.

## Stocks settings

Tap the gear on the watchlist.

![Stocks settings: new tickers top or bottom, copy, paste, replace](../../../assets/screenshots/stocks-settings.png)

**New stocks:** `top` (default) or `bottom` for `$AAPL` adds and paste-to-add.

**Import / export:**

| Action | Result |
| --- | --- |
| copy list | Writes `Exchange,Ticker,Name` CSV to the clipboard (no share sheet) |
| paste (add) | Adds tickers from the clipboard; skips ones already on the list |
| replace list | Swaps the whole list for the clipboard |

Accepted clipboard formats:

| Format | Example |
| --- | --- |
| Builder export | `Exchange,Ticker,Name` then rows |
| Apple Stocks | `Symbol,Name,…` |
| One ticker per line | `AAPL` |
| `Symbol,Name` | `AAPL,Apple Inc.` |

You can also Enter a CSV (or several lines) in the `$` bar on the watchlist. Duplicates collapse by ticker. Cap is 20.
