package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.apps.AppList
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.StockInsert
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.Notes
import xyz.cdr.builderlauncher.stocks.StockPoint
import xyz.cdr.builderlauncher.stocks.StockRange
import xyz.cdr.builderlauncher.stocks.Stocks
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Gain
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Accent

data class HubRow(
    val kind: String,
    val title: String,
    val body: String = "",
)

data class NoteListRow(
    val title: String,
    val edited: String,
)

data class AppListRow(
    val label: String,
)

data class StockListRow(
    val symbol: String,
    val name: String,
    val price: String,
    val change: String,
    val up: Boolean,
)

data class StockStatRow(
    val leftLabel: String,
    val leftValue: String,
    val rightLabel: String,
    val rightValue: String,
)

@Composable
fun HomeChrome(
    time: String,
    date: String,
    input: String,
    weather: String = "",
    todos: List<String> = emptyList(),
    apps: List<String> = emptyList(),
    hint: String = "Type to work. help for commands. Then put it down.",
    commandsOpen: Boolean = false,
    slashOpen: Boolean = false,
    prompt: String = ">",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.headlineLarge, color = Paper)
                Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                if (weather.isNotBlank()) {
                    Text(weather, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            MessagesIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        todos.take(HomeTodos.PREVIEW).forEach { text ->
            Text(text, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        Text(HomeTodos.MORE_TASKS, color = Accent, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (apps.isEmpty() && input.isBlank()) {
                Text(hint, color = Dim)
            } else {
                apps.forEach { label ->
                    Text(
                        label,
                        color = if (label == Notes.MORE || label == AppList.MORE || label == Stocks.MORE) Accent else Paper,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, commandsOpen = commandsOpen, slashOpen = slashOpen, prompt = prompt)
    }
}

@Composable
fun TodosChrome(
    todos: List<String> = emptyList(),
    doneTodos: List<String> = emptyList(),
    input: String = "",
    prompt: String = HomeTodos.TASK_PREFIX,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(HomeTodos.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            CopyIcon(Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            todos.forEach { text ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        color = Paper,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                    DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                }
            }
            if (doneTodos.isNotEmpty()) {
                Text(
                    "done",
                    color = Dim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                doneTodos.forEach { text ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text,
                            color = Dim,
                            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        )
                        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, prompt = prompt)
    }
}

@Composable
fun NotesChrome(rows: List<NoteListRow>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(Notes.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) {
                Text("Type + to write a note.", color = Dim)
            } else {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(row.title, color = Paper)
                            Text(row.edited, color = Dim, style = MaterialTheme.typography.bodyMedium)
                        }
                        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AllAppsChrome(
    rows: List<AppListRow>,
    input: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(AppList.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (rows.isEmpty()) {
                Text("No apps match.", color = Dim)
            } else {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppMark(Modifier.padding(end = 12.dp))
                        Text(
                            row.label,
                            color = Paper,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        InfoIcon(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                        DeleteIcon(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input)
    }
}

@Composable
fun NoteEditorChrome(body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Notes.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            CopyIcon(Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = Paper, style = MaterialTheme.typography.bodyLarge)
    }
}

data class ChatListRow(
    val title: String,
    val edited: String,
)

@Composable
fun ChatChrome(
    messages: List<ChatBubble>,
    input: String = "",
    busy: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Chats.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            HistoryIcon(Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (messages.isEmpty() && !busy) {
                Text("Ask a question.", color = Dim)
            }
            messages.forEach { msg ->
                if (msg.user) {
                    Text(msg.body, color = Accent, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownDocument(msg.body)
                }
            }
            if (busy) {
                Text("…", color = Dim, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, prompt = "?")
    }
}

@Composable
fun ChatHistoryChrome(rows: List<ChatListRow>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(Chats.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) {
                Text("No conversations yet.", color = Dim)
            } else {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(row.title, color = Paper)
                            Text(row.edited, color = Dim, style = MaterialTheme.typography.bodyMedium)
                        }
                        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
    }
}

data class ChatBubble(
    val user: Boolean,
    val body: String,
)

@Composable
fun StocksChrome(
    rows: List<StockListRow>,
    input: String = "",
    hits: List<StockListRow> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Stocks.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            GearIcon(Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val shown = if (hits.isNotEmpty()) hits else rows
            if (shown.isEmpty()) {
                Text("Type \$AAPL to add a ticker.", color = Dim)
            } else {
                shown.forEach { row ->
                    StockRowChrome(row)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, prompt = "$")
    }
}

@Composable
fun StocksSettingsChrome(
    insert: StockInsert = StockInsert.TOP,
    count: Int = 2,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Stocks.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            Text("stocks", color = Dim)
        }
        Spacer(Modifier.height(16.dp))
        Text("New stocks", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            StockInsert.entries.forEach { item ->
                Text(
                    item.name.lowercase(),
                    color = if (item == insert) Accent else Dim,
                )
            }
        }
        Text(
            if (insert == StockInsert.BOTTOM) {
                "New tickers go to the bottom of the list."
            } else {
                "New tickers go to the top of the list."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text("Import / export", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(
            "$count of ${Stocks.MAX} tickers",
            color = Paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Text("copy list", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Text("paste (add)", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Text("replace list", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy writes Exchange,Ticker,Name. Paste adds tickers from the clipboard and skips ones already on the list. Replace swaps the whole list for the clipboard. New tickers from paste follow the top/bottom setting.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Accepted: our CSV, an Apple Stocks Symbol,Name export, or one ticker per line. Cap is ${Stocks.MAX}.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun StockDetailChrome(
    symbol: String,
    name: String,
    price: String,
    changeLine: String,
    up: Boolean,
    points: List<StockPoint>,
    range: StockRange = StockRange.D1,
    stats: List<StockStatRow> = emptyList(),
) {
    val tone = if (up) Gain else Loss
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(Stocks.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Text(symbol, color = Paper, style = MaterialTheme.typography.headlineLarge)
        Text(name, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(price, color = Paper, style = MaterialTheme.typography.headlineLarge)
        Text(changeLine, color = tone, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        StockChart(points = points, up = up, modifier = Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StockRange.entries.forEach { item ->
                Text(
                    item.label,
                    color = if (item == range) Accent else Dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        stats.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.leftLabel, color = Dim, style = MaterialTheme.typography.labelSmall)
                    Text(row.leftValue, color = Paper, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(row.rightLabel, color = Dim, style = MaterialTheme.typography.labelSmall)
                    Text(row.rightValue, color = Paper, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun StockChart(
    points: List<StockPoint>,
    up: Boolean,
    modifier: Modifier = Modifier,
) {
    val tone = if (up) Gain else Loss
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val ys = points.map { it.close }
        val min = ys.min()
        val max = ys.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val dx = size.width / (points.lastIndex)
        val line = Path()
        points.forEachIndexed { i, point ->
            val x = i * dx
            val y = (size.height - ((point.close - min) / span * size.height).toFloat()).coerceIn(0f, size.height)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, tone.copy(alpha = 0.18f))
        drawPath(line, tone, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun StockRowChrome(row: StockListRow) {
    val tone = if (row.up) Gain else Loss
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(row.symbol, color = Paper)
            Text(row.name, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(row.price, color = Paper)
            Text(row.change, color = tone, style = MaterialTheme.typography.bodyMedium)
        }
        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
    }
}

@Composable
fun HubChrome(rows: List<HubRow>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("hub", color = Accent)
            Text("home", color = Dim)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) {
                Text(
                    "Grant notification access in settings to fill the hub with messages you can reply to.",
                    color = Dim,
                )
            } else {
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(row.kind, color = Dim, style = MaterialTheme.typography.labelSmall)
                            Text(row.title, color = Paper)
                            if (row.body.isNotBlank()) {
                                Text(row.body, color = Dim, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        ReplyIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsChrome(
    settings: BuilderSettings,
    hardware: Boolean,
    hermes: String,
    apiKey: String,
    model: String,
    weatherPlace: String = "",
    weatherSuggestions: List<String> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("settings", color = Accent)
            Text("home", color = Dim)
        }
        Spacer(Modifier.height(16.dp))
        AccentPicker(hex = settings.accentHex)
        Field("Hex", settings.accentHex, AccentColor.DEFAULT_HEX)
        Spacer(Modifier.height(16.dp))
        Text("AI provider", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Hermes", color = if (settings.provider == LlmProvider.HERMES) Accent else Dim)
            Text("xAI", color = if (settings.provider == LlmProvider.XAI) Accent else Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Text("OpenAI", color = if (settings.provider == LlmProvider.OPENAI) Accent else Dim)
            Text("Anthropic", color = if (settings.provider == LlmProvider.ANTHROPIC) Accent else Dim)
        }
        if (settings.provider == LlmProvider.HERMES) {
            Field("Hermes base URL", hermes, "http://192.168.1.10:8642")
        } else {
            Text(
                if (settings.provider == LlmProvider.XAI) {
                    "Sign in with SuperGrok, or paste an API key. Used only for ? questions."
                } else {
                    "Paste an API key. Used only for ? questions."
                },
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (settings.provider == LlmProvider.XAI) {
                Text("Sign in with SuperGrok", color = Paper)
                Spacer(Modifier.height(8.dp))
            }
        }
        Field("API key (stored on device)", apiKey, if (settings.provider == LlmProvider.HERMES) "optional for local Hermes" else "optional if signed in")
        Field("Model", model, when (settings.provider) {
            LlmProvider.XAI -> "grok-4.6"
            LlmProvider.OPENAI -> "gpt-4o"
            LlmProvider.ANTHROPIC -> "claude-sonnet-4-5"
            LlmProvider.HERMES -> "default"
        })
        Spacer(Modifier.height(16.dp))
        Text("Keyboard", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            KeyboardMode.entries.forEach { mode ->
                Text(
                    mode.name.lowercase(),
                    color = if (settings.keyboardMode == mode) Accent else Dim,
                )
            }
        }
        Text(
            if (hardware) {
                "Hardware keyboard detected — command bar sits at the bottom, above the keys."
            } else {
                "Slab mode — command bar sits at the bottom, just above the keyboard."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Field("Weather location", weatherPlace, "New York")
        Text(
            if (weatherPlace.isNotBlank() && weatherSuggestions.isEmpty()) {
                "Weather uses this city. No GPS."
            } else {
                "Type a city. Pick a match. No GPS required."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        weatherSuggestions.forEach { label ->
            Text(label, color = Paper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Weather units", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            WeatherUnits.entries.forEach { units ->
                Text(
                    units.name.lowercase(),
                    color = if (settings.weatherUnits == units) Accent else Dim,
                )
            }
        }
        Text(
            if (settings.weatherUnits == WeatherUnits.IMPERIAL) {
                "Home weather in Fahrenheit."
            } else {
                "Home weather in Celsius."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text("Notification access (hub)", color = Paper)
        Spacer(Modifier.height(12.dp))
        Text("Set as default home app", color = Paper)
        Spacer(Modifier.height(24.dp))
        Text(
            "Tokens stay on the device. They are sent only as a Bearer token to the provider you chose.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CommandRow(value: String, commandsOpen: Boolean = false, slashOpen: Boolean = false, prompt: String = ">") {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (slashOpen) {
            SlashCommandMenu()
        } else if (commandsOpen) {
            CommandMenu()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prompt, color = Accent, modifier = Modifier.padding(end = 10.dp))
            Text(
                value.ifEmpty { "" },
                color = Paper,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String) {
    Text(label, color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
    Text(
        value.ifEmpty { placeholder },
        color = if (value.isEmpty()) Dim else Paper,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    HorizontalDivider(color = Line)
}

@Composable
fun ReplyIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = size.minDimension * 0.16f
        val stemX = pad + size.width * 0.08f
        val topY = pad + size.height * 0.12f
        val tip = Offset(size.width - pad, topY)
        val shaft = Path().apply {
            moveTo(stemX, size.height - pad)
            lineTo(stemX, topY)
            lineTo(tip.x, tip.y)
        }
        drawPath(shaft, color = accent, style = stroke)
        val ah = size.minDimension * 0.28f
        drawLine(
            color = accent,
            start = tip,
            end = Offset(tip.x - ah, tip.y - ah * 0.55f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = tip,
            end = Offset(tip.x - ah, tip.y + ah * 0.55f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun MessagesIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val pad = size.minDimension * 0.08f
        val bodyH = size.height * 0.70f
        drawRoundRect(
            color = accent,
            topLeft = Offset(pad, pad),
            size = Size(size.width - pad * 2f, bodyH),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        val tail = size.width * 0.30f
        drawLine(
            color = accent,
            start = Offset(tail, pad + bodyH),
            end = Offset(tail - size.width * 0.14f, size.height - pad),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = accent,
            start = Offset(tail + size.width * 0.20f, pad + bodyH),
            end = Offset(tail - size.width * 0.14f, size.height - pad),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun HistoryIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val r = size.minDimension / 2f - stroke.width
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = accent, radius = r, style = stroke)
        drawLine(
            color = accent,
            start = c,
            end = Offset(c.x, c.y - r * 0.45f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = c,
            end = Offset(c.x + r * 0.38f, c.y + r * 0.18f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CopyIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val gap = size.minDimension * 0.28f
        val box = Size(size.width - gap, size.height - gap)
        drawRoundRect(
            color = accent,
            topLeft = Offset(gap, 0f),
            size = box,
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(0f, gap),
            size = box,
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
    }
}

@Composable
fun GearIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(
            width = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val cx = size.width / 2f
        val cy = size.height / 2f
        val hole = size.minDimension * 0.16f
        val valley = size.minDimension * 0.30f
        val tip = size.minDimension * 0.46f
        val teeth = 8
        val step = (Math.PI * 2.0 / teeth).toFloat()
        val half = step * 0.28f
        val path = Path()
        for (i in 0 until teeth) {
            val a = i * step - (Math.PI / 2.0).toFloat()
            val angles = floatArrayOf(
                a - step / 2f + half,
                a - half,
                a + half,
                a + step / 2f - half,
            )
            val radii = floatArrayOf(valley, tip, tip, valley)
            for (k in 0 until 4) {
                val x = cx + kotlin.math.cos(angles[k]) * radii[k]
                val y = cy + kotlin.math.sin(angles[k]) * radii[k]
                if (i == 0 && k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        drawPath(path, color = accent, style = stroke)
        drawCircle(color = accent, radius = hole, center = Offset(cx, cy), style = stroke)
    }
}

@Composable
fun DeleteIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val inset = size.minDimension * 0.22f
        drawLine(
            color = Dim,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = Dim,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun InfoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val r = size.minDimension / 2f - stroke.width
        drawCircle(color = Dim, radius = r, style = stroke)
        val cx = size.width / 2f
        drawCircle(color = Dim, radius = 1.3.dp.toPx(), center = Offset(cx, size.height * 0.32f))
        drawLine(
            color = Dim,
            start = Offset(cx, size.height * 0.46f),
            end = Offset(cx, size.height * 0.72f),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun AppMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) {
        drawRoundRect(
            color = Line,
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
    }
}
