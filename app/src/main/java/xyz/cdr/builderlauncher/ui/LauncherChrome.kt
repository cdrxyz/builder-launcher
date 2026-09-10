package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.HermesUrls
import xyz.cdr.builderlauncher.apps.AppList
import xyz.cdr.builderlauncher.commands.Calculator
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.StockInsert
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.data.AppIcons
import xyz.cdr.builderlauncher.data.ClockFace
import xyz.cdr.builderlauncher.data.UiTheme
import xyz.cdr.builderlauncher.R
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.Notes
import xyz.cdr.builderlauncher.hub.HubMessages
import xyz.cdr.builderlauncher.clock.Clock
import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.clock.ClockAlert
import xyz.cdr.builderlauncher.clock.ClockAlertKind
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.clock.WorldClock
import xyz.cdr.builderlauncher.backup.BackupFrequency
import xyz.cdr.builderlauncher.stocks.StockPoint
import xyz.cdr.builderlauncher.stocks.StockRange
import xyz.cdr.builderlauncher.stocks.StockStatLine
import xyz.cdr.builderlauncher.stocks.Stocks
import xyz.cdr.builderlauncher.podcasts.HomePodcastMark
import xyz.cdr.builderlauncher.podcasts.Podcasts
import xyz.cdr.builderlauncher.weather.WeatherCodes
import xyz.cdr.builderlauncher.weather.WeatherDay
import xyz.cdr.builderlauncher.weather.WeatherForecast
import xyz.cdr.builderlauncher.weather.WeatherHour
import xyz.cdr.builderlauncher.weather.WeatherKind
import xyz.cdr.builderlauncher.weather.WeatherNow
import xyz.cdr.builderlauncher.usage.PinUsageMark
import xyz.cdr.builderlauncher.usage.Usage
import xyz.cdr.builderlauncher.usage.UsageSnapshot
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Gain
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.ThemedBadge
import xyz.cdr.builderlauncher.ui.theme.ThemedList
import xyz.cdr.builderlauncher.ui.theme.ThemedPlayer
import xyz.cdr.builderlauncher.ui.theme.ThemedPlayWell
import xyz.cdr.builderlauncher.ui.theme.ThemedRow
import xyz.cdr.builderlauncher.ui.theme.ThemedSectionLabel
import xyz.cdr.builderlauncher.ui.theme.CommandBarRule
import xyz.cdr.builderlauncher.ui.theme.FieldRule
import xyz.cdr.builderlauncher.ui.theme.commandBarChrome
import xyz.cdr.builderlauncher.ui.theme.inputChrome

data class HubRow(
    val kind: String,
    val title: String,
    val body: String = "",
    val reply: String? = null,
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

@Composable
fun HomeTickerMark(
    symbol: String?,
    change: String? = null,
    up: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (symbol.isNullOrBlank()) return
    val unknown = change.isNullOrBlank() || change == "—"
    Column(
        modifier = modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(symbol, color = Paper, style = MaterialTheme.typography.bodyMedium)
        Text(
            change ?: "—",
            color = if (unknown) Dim else if (up) Gain else Loss,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun HomeWeatherMark(
    weather: String?,
    kind: WeatherKind? = null,
    isDay: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (weather.isNullOrBlank()) return
    val temperature = WeatherCodes.homeTemperature(weather)
    val resolved = kind ?: WeatherKind.ofCondition(weather)
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        resolved?.let { glyph ->
            WeatherGlyph(
                kind = glyph,
                isDay = isDay,
                color = Dim,
                size = 24.dp,
            )
        }
        Text(temperature, color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HomeChrome(
    time: String,
    date: String,
    input: String,
    weather: String = "",
    todos: List<String> = emptyList(),
    apps: List<String> = emptyList(),
    pins: List<String> = emptyList(),
    appIcons: Boolean = false,
    pinUsage: List<PinUsageMark> = emptyList(),
    hint: String = "Type to work. help for commands. Then put it down.",
    commandsOpen: Boolean = false,
    slashOpen: Boolean = false,
    prompt: String = ">",
    ticker: String? = null,
    tickerChange: String? = null,
    tickerUp: Boolean = true,
    analog: Boolean = true,
    event: String = "",
    podcastMark: HomePodcastMark = HomePodcastMark.HEADPHONES,
) {
    val (hour, minute) = parseHomeClock(time)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    HomePodcastMarkIcon(podcastMark, Modifier.padding(top = 10.dp, end = 4.dp, bottom = 6.dp))
                    if (weather.isNotBlank()) {
                        HomeWeatherMark(weather)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    HomeTickerMark(ticker, tickerChange, tickerUp)
                    MessagesIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                }
            }
            Column(
                Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (analog) {
                    AnalogClock(hour = hour, minute = minute, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(time, color = Paper, style = MaterialTheme.typography.bodyMedium)
                    Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(time, style = MaterialTheme.typography.headlineLarge, color = Paper)
                    Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
                if (event.isNotBlank()) {
                    Text(event, color = Dim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val overlayMenus = commandsOpen || slashOpen
        val filteringApps = apps.isNotEmpty()
        if (!overlayMenus) {
            if (!filteringApps) {
            Spacer(Modifier.height(8.dp))
            todos.take(HomeTodos.PREVIEW).forEach { text ->
                Text(text, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            CaretLink(
                HomeTodos.MORE_TASKS,
                modifier = Modifier.padding(vertical = 4.dp),
                color = Dim,
                caretColor = Dim,
            )
            Spacer(Modifier.height(8.dp))
            }
            if (appIcons && pins.isNotEmpty() && apps.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    pins.forEachIndexed { index, _ ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppMark(size = 48.dp)
                            pinUsage.getOrNull(index)?.let { ChromePinUsage(it) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (apps.isEmpty() && input.isBlank() && (pins.isEmpty() || appIcons)) {
                    Text(hint, color = Dim)
                } else {
                    if (!appIcons) {
                        pins.forEachIndexed { index, label ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = Paper)
                                pinUsage.getOrNull(index)?.let { PinUsageInline(it) }
                            }
                        }
                    }
                    apps.forEach { label ->
                        val shortcut = label == Notes.MORE || label == AppList.MORE || label == Stocks.MORE || label == Podcasts.MORE
                        if (appIcons && !shortcut) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppMark(Modifier.padding(end = 12.dp))
                                Text(label, color = Paper)
                            }
                        } else if (shortcut) {
                            CaretLink(
                                label,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                color = Dim,
                                caretColor = Dim,
                            )
                        } else {
                            Text(
                                label,
                                color = Paper,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(
            input,
            commandsOpen = commandsOpen,
            slashOpen = slashOpen,
            prompt = prompt,
            modifier = if (overlayMenus) Modifier.weight(1f) else Modifier,
        )
    }
}

@Composable
private fun ChromePinUsage(mark: PinUsageMark) {
    val color = if (mark.productive) Gain else Loss
    Column {
        Text(
            mark.duration,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            mark.share,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TodosChrome(
    todos: List<String> = emptyList(),
    doneTodos: List<String> = emptyList(),
    input: String = "",
    prompt: String = HomeTodos.TASK_PREFIX,
    confirm: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = HomeTodos.TITLE,
            leading = { ScreenBack(HomeTodos.BACK) },
            trailing = { CopyIcon(Modifier.padding(vertical = 6.dp)) },
        )
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
                    EditIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
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
                        EditIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                        DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, prompt = prompt, confirm = confirm)
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
        ScreenHeader(
            title = Notes.COMMAND,
            leading = { ScreenBack(Notes.BACK) },
        )
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
        ScreenHeader(
            title = AppList.COMMAND,
            leading = { ScreenBack(AppList.BACK) },
        )
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
    provider: LlmProvider = LlmProvider.XAI,
    providerMenu: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "chat",
            leading = { ScreenBack(Chats.BACK) },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderIcon(provider, Modifier.padding(vertical = 6.dp))
                    HistoryIcon(Modifier.padding(vertical = 6.dp))
                }
            },
        )
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
        if (providerMenu) {
            ProviderMenu(
                current = provider,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ProviderMenuBelowIcon, end = ProviderMenuEndInset),
            )
        }
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
        ScreenHeader(
            title = "chats",
            leading = { ScreenBack(Chats.BACK) },
        )
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
        ScreenHeader(
            title = Stocks.COMMAND,
            leading = { ScreenBack(Stocks.BACK) },
            trailing = { GearIcon(Modifier.padding(vertical = 6.dp)) },
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val shown = if (hits.isNotEmpty()) hits else rows
            if (shown.isEmpty()) {
                Text("Type \$AAPL to add a ticker.", color = Dim)
            } else {
                ThemedList {
                    shown.forEachIndexed { index, row ->
                        StockRowChrome(row, last = index == shown.lastIndex)
                    }
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

data class PodcastListRow(
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val highlight: Boolean = false,
    val art: Boolean = false,
    val maxTitleLines: Int = Int.MAX_VALUE,
    val maxSubtitleLines: Int = Int.MAX_VALUE,
    val metaBelow: Boolean = false,
    val deletable: Boolean = false,
    val downloadable: Boolean = false,
    val downloaded: Boolean = false,
    val dimmed: Boolean = false,
)

@Composable
fun PodcastsChrome(
    continueRows: List<PodcastListRow> = emptyList(),
    newRows: List<PodcastListRow> = emptyList(),
    shows: List<PodcastListRow> = emptyList(),
    hits: List<PodcastListRow> = emptyList(),
    input: String = "",
    nowPlayingTitle: String = "",
    nowPlayingShow: String = "",
    nowPlaying: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = Podcasts.COMMAND,
            leading = { GearIcon(Modifier.padding(vertical = 6.dp)) },
            trailing = { Text(Podcasts.HOME, color = Accent, modifier = Modifier.padding(vertical = 6.dp)) },
        )
        Spacer(Modifier.height(8.dp))
        if (nowPlayingTitle.isNotBlank()) {
            PodcastNowPlayingBar(title = nowPlayingTitle, show = nowPlayingShow, playing = nowPlaying)
            Spacer(Modifier.height(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (hits.isNotEmpty()) {
                ThemedList {
                    hits.forEachIndexed { index, row -> PodcastRowChrome(row, last = index == hits.lastIndex) }
                }
            } else if (continueRows.isEmpty() && newRows.isEmpty() && shows.isEmpty()) {
                Text("Type a show name, RSS URL, or paste Overcast OPML.", color = Dim)
            } else {
                if (continueRows.isNotEmpty()) {
                    PodcastSectionHeader(Podcasts.SECTION_RECENT)
                    ThemedList {
                        continueRows.forEachIndexed { index, row ->
                            PodcastRowChrome(row, last = index == continueRows.lastIndex)
                        }
                    }
                }
                if (newRows.isNotEmpty()) {
                    PodcastSectionHeader(Podcasts.SECTION_NEXT)
                    ThemedList {
                        newRows.forEachIndexed { index, row ->
                            PodcastRowChrome(row, last = index == newRows.lastIndex)
                        }
                    }
                }
                if (shows.isNotEmpty()) {
                    PodcastSectionHeader(Podcasts.SECTION_SHOWS)
                    ThemedList {
                        shows.forEachIndexed { index, row ->
                            PodcastRowChrome(row, last = index == shows.lastIndex)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, prompt = ">")
    }
}

@Composable
fun PodcastSectionHeader(title: String) {
    ThemedSectionLabel(title)
}

@Composable
fun PodcastNowPlayingBar(
    title: String,
    show: String = "",
    playing: Boolean = false,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
) {
    ThemedPlayer(modifier) {
        val openMod = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
        Column(Modifier.weight(1f).then(openMod)) {
            Text("now playing", color = Accent, style = MaterialTheme.typography.labelSmall)
            Text(title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (show.isNotBlank()) {
                Text(
                    show,
                    color = Dim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val toggleMod = if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
        ThemedPlayWell {
            val tokens = xyz.cdr.builderlauncher.ui.theme.LocalTokens.current
            PlayPauseIcon(
                playing = playing,
                color = if (tokens.chrome == xyz.cdr.builderlauncher.ui.theme.ThemeChrome.MATERIAL) tokens.ink else Accent,
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .then(toggleMod)
                    .semantics { contentDescription = if (playing) "pause" else "play" },
            )
        }
    }
}

@Composable
private fun PodcastRowChrome(row: PodcastListRow, last: Boolean = false) {
    ThemedRow(last = last) {
        if (row.art) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(Podcasts.ART_DP.dp)
                    .background(Line),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = when {
                    row.dimmed -> Dim
                    row.highlight -> Accent
                    else -> Paper
                },
                maxLines = row.maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.metaBelow) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.subtitle.isNotBlank()) {
                        Text(row.subtitle, color = Dim, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Spacer(Modifier)
                    }
                    if (row.meta.isNotBlank()) {
                        Text(row.meta, color = Dim, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (row.subtitle.isNotBlank()) {
                Text(
                    row.subtitle,
                    color = Dim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = row.maxSubtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!row.metaBelow && row.meta.isNotBlank()) {
            Text(row.meta, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        if (row.downloadable) {
            DownloadIcon(
                filled = row.downloaded,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (row.deletable) {
            DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
        }
    }
}

@Composable
fun PodcastsSettingsChrome(
    cache: String = "5 GB",
    used: String = "0 MB",
    count: Int = 2,
    speed: String = "1×",
    speedProgress: Float = 0f,
    skipSilence: Boolean = true,
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
            Text(Podcasts.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            Text("podcasts", color = Dim)
        }
        Spacer(Modifier.height(16.dp))
        Text("Playback speed", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(speed, color = Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        PodcastSpeedBar(progress = speedProgress)
        Text(
            "Applies to every show.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Skip silence", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(true, false).forEach { on ->
                Text(
                    Podcasts.skipSilenceLabel(on),
                    color = if (skipSilence == on) Accent else Dim,
                )
            }
        }
        Text(
            "Skips pauses while people think. Voices stay at the same speed.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Download cache", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("1 GB", "5 GB", "10 GB", "20 GB").forEach { label ->
                Text(label, color = if (label == cache) Accent else Dim)
            }
        }
        Text(
            "$used used of $cache. Oldest downloads delete first.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text("Overcast / OPML", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(
            "$count of ${Podcasts.MAX_SHOWS} shows",
            color = Paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Text("paste OPML", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "Overcast: Settings → Export OPML, copy the file, then paste here. RSS feed URLs also subscribe from the podcasts bar.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun PodcastShowChrome(
    show: String = "Accidental Tech Podcast",
    author: String = "Marco Arment",
    order: String = "oldest first",
    episodes: List<PodcastListRow> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(Podcasts.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Text(show, color = Paper, style = MaterialTheme.typography.headlineLarge)
        if (author.isNotBlank()) {
            Text(author, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text("Episodes", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("newest first", "oldest first").forEach { label ->
                Text(label, color = if (label == order) Accent else Dim)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            ThemedList {
                episodes.forEachIndexed { index, row ->
                    PodcastRowChrome(row, last = index == episodes.lastIndex)
                }
            }
        }
    }
}

@Composable
fun PodcastEpisodeChrome(
    show: String,
    title: String,
    position: String,
    playing: Boolean = false,
    downloaded: Boolean = false,
    downloadPercent: String = "",
    progress: Float = 0.2f,
    speed: String = "1×",
    notes: String = "",
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
            Text(Podcasts.BACK, color = Accent, modifier = Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloadPercent.isNotBlank()) {
                    Text(
                        downloadPercent,
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                DownloadIcon(filled = downloaded, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(show, color = Dim, style = MaterialTheme.typography.bodyMedium)
            Text(title, color = Paper, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(position, color = Dim)
                Text(speed, color = Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            PodcastScrubBar(progress = progress)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("−15", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
                PlayPauseIcon(playing = playing, modifier = Modifier.padding(vertical = 8.dp).semantics { contentDescription = if (playing) "pause" else "play" })
                Text("+15", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Show notes", color = Dim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                PodcastNotesText(notes)
            }
        }
    }
}

@Composable
fun PodcastSpeedMenu(
    speed: Float,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Text(
            Podcasts.formatSpeed(speed),
            color = Accent,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clickable { onExpanded(true) }
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "playback speed" },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpanded(false) },
            containerColor = Ink,
        ) {
            Podcasts.SPEED_STEPS.forEach { step ->
                Text(
                    Podcasts.formatSpeed(step),
                    color = if (Podcasts.snapSpeed(speed) == step) Accent else Paper,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(step)
                            onExpanded(false)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun PodcastNotesText(
    notes: String,
    modifier: Modifier = Modifier,
    onTimestamp: ((Long) -> Unit)? = null,
) {
    val accent = Accent
    val hits = remember(notes) { Podcasts.timestamps(notes) }
    val annotated = remember(notes, hits, accent) {
        buildAnnotatedString {
            var i = 0
            hits.forEach { hit ->
                if (hit.start > i) append(notes.substring(i, hit.start))
                withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) {
                    append(notes.substring(hit.start, hit.end))
                }
                i = hit.end
            }
            if (i < notes.length) append(notes.substring(i))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        color = Paper,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layout = it },
        modifier = modifier.then(
            if (onTimestamp == null) Modifier
            else Modifier.pointerInput(notes, onTimestamp) {
                detectTapGestures { offset ->
                    val i = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    val ms = Podcasts.timestampAt(notes, i) ?: return@detectTapGestures
                    onTimestamp(ms)
                }
            },
        ),
    )
}

@Composable
fun PodcastScrubBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onSeekFraction: ((Float) -> Unit)? = null,
) {
    val accent = Accent
    val dim = Dim
    val paper = Paper
    val t = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Podcasts.BAR_SIDE_DP.dp)
            .height(28.dp)
            .then(
                if (onSeekFraction == null) Modifier
                else Modifier.pointerInput(onSeekFraction) {
                    detectTapGestures { offset ->
                        onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }.pointerInput(onSeekFraction) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSeekFraction((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
            ),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        drawLine(dim, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(0f, y), Offset(size.width * t, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(paper, radius = 5.dp.toPx(), center = Offset(size.width * t, y))
    }
}

@Composable
fun PodcastSpeedBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onSpeedFraction: ((Float) -> Unit)? = null,
) {
    val accent = Accent
    val dim = Dim
    val t = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Podcasts.BAR_SIDE_DP.dp)
            .height(28.dp)
            .then(
                if (onSpeedFraction == null) Modifier
                else Modifier.pointerInput(onSpeedFraction) {
                    detectTapGestures { offset ->
                        onSpeedFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }.pointerInput(onSpeedFraction) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSpeedFraction((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
            ),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        drawLine(dim, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(0f, y), Offset(size.width * t, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(accent, radius = 5.dp.toPx(), center = Offset(size.width * t, y))
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
    stats: List<StockStatLine> = emptyList(),
    cagr: List<StockStatLine> = emptyList(),
    extendedLine: String = "",
    extendedUp: Boolean = true,
    dateLine: String = "",
    selectedIndex: Int? = null,
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
        if (dateLine.isNotBlank()) {
            Text(dateLine, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        if (extendedLine.isNotBlank()) {
            Text(
                extendedLine,
                color = if (extendedUp) Gain else Loss,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        StockChart(
            points = points,
            up = up,
            selectedIndex = selectedIndex,
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
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
            StockStatChrome(row)
        }
        if (cagr.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("CAGR", color = Dim, style = MaterialTheme.typography.labelSmall)
            cagr.forEach { row ->
                StockStatChrome(row)
            }
        }
    }
}

@Composable
private fun StockStatChrome(row: StockStatLine) {
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

@Composable
fun StockChart(
    points: List<StockPoint>,
    up: Boolean,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val tone = if (up) Gain else Loss
    val paper = Paper
    val select = rememberUpdatedState(onSelect)
    Canvas(
        modifier.pointerInput(points) {
            if (points.isEmpty()) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                select.value(Stocks.indexAt(down.position.x, size.width.toFloat(), points.size))
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) {
                        select.value(null)
                        break
                    }
                    change.consume()
                    select.value(Stocks.indexAt(change.position.x, size.width.toFloat(), points.size))
                }
            }
        },
    ) {
        if (points.size < 2) return@Canvas
        val ys = points.map { it.close }
        val min = ys.min()
        val max = ys.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val dx = size.width / (points.lastIndex)
        fun yOf(close: Double): Float =
            (size.height - ((close - min) / span * size.height).toFloat()).coerceIn(0f, size.height)
        val line = Path()
        points.forEachIndexed { i, point ->
            val x = i * dx
            val y = yOf(point.close)
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
        val mark = selectedIndex?.coerceIn(0, points.lastIndex)
        if (mark != null) {
            val x = mark * dx
            val y = yOf(points[mark].close)
            drawLine(paper, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(paper, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun StockRowChrome(row: StockListRow, last: Boolean = false) {
    val tone = if (row.up) Gain else Loss
    ThemedRow(last = last) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(row.symbol, color = Paper)
            Text(row.name, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(vertical = 2.dp).padding(end = 8.dp)) {
            Text(row.price, color = Paper)
            ThemedBadge(row.change, tone)
        }
        DeleteIcon(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
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
        ScreenHeader(
            title = HubMessages.TITLE,
            leading = { ScreenBack(HubMessages.BACK) },
            trailing = { DeleteIcon(Modifier.padding(vertical = 6.dp)) },
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) {
                Text(
                    "Grant notification access in settings to fill the hub with messages you can reply to.",
                    color = Dim,
                )
            } else {
                rows.forEach { row ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(row.kind, color = Dim, style = MaterialTheme.typography.labelSmall)
                                Text(row.title, color = Paper)
                                if (row.body.isNotBlank()) {
                                    Text(
                                        row.body,
                                        color = Dim,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            ReplyIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                            DeleteIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                        }
                        if (row.reply != null) {
                            HubReplyBar(row.reply)
                        }
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
        ScreenHeader(
            title = "settings",
            leading = {},
            trailing = { Text("home", color = Dim) },
        )
        Spacer(Modifier.height(16.dp))
        Text("Theme", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            UiTheme.entries.forEach { theme ->
                Text(
                    theme.label,
                    color = if (settings.uiTheme == theme) Accent else Dim,
                )
            }
        }
        Text(settings.uiTheme.blurb, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        AccentPicker(hex = settings.accentHex)
        Field("Hex", settings.accentHex, AccentColor.DEFAULT_HEX)
        Spacer(Modifier.height(16.dp))
        CaretLink("… AI providers >", modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text(AiPlatforms.of(settings.provider).label, color = Dim, style = MaterialTheme.typography.bodyMedium)
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
                "Slab mode — software keyboard stays open under the command bar."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text("Home apps", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            AppIcons.entries.forEach { style ->
                Text(
                    style.name.lowercase(),
                    color = if (settings.appIcons == style) Accent else Dim,
                )
            }
        }
        Text(
            if (settings.appIcons == AppIcons.ICONS) {
                "Pinned apps as grayscale icons. Home search shows grayscale icons."
            } else {
                "Pinned apps and home search as names."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text("Pin time", color = Dim, style = MaterialTheme.typography.bodyMedium)
            Text("off", color = if (!settings.pinUsage) Accent else Dim)
            Text("on", color = if (settings.pinUsage) Accent else Dim)
        }
        Text(
            "Minutes today. Names: 30m (17%) beside the pin. Icons: 30m then 17% under the icon. Green if productive, red if not.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text("Clock face", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ClockFace.entries.forEach { face ->
                Text(
                    face.name.lowercase(),
                    color = if (settings.clockFace == face) Accent else Dim,
                )
            }
        }
        Text(
            if (settings.clockFace == ClockFace.ANALOG) {
                "Analog clock in the center of home, with the time and date below."
            } else {
                "Digital time and date in the center of home."
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
        Spacer(Modifier.height(16.dp))
        Text("Clock sound", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ClockSound.entries.take(3).forEach { sound ->
                Text(sound.label, color = if (settings.clockSound == sound) Accent else Dim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ClockSound.entries.drop(3).forEach { sound ->
                Text(sound.label, color = if (settings.clockSound == sound) Accent else Dim)
            }
        }
        Text(
            "Tap a sound to hear it. Each tone loops 10 to 30 seconds. Alarms fade in over 4 seconds.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        CaretLink("… calendar >", modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text("Calendar access: granted", color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        CaretLink("… backup >", modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text("Last backup: never", color = Dim, style = MaterialTheme.typography.bodyMedium)
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
fun BackupChrome(
    endpoint: String = "https://ACCOUNT.r2.cloudflarestorage.com",
    bucket: String = "builder-launcher",
    accessKey: String = "••••",
    frequency: BackupFrequency = BackupFrequency.DAILY,
    lastBackup: String = "never",
    includeAi: Boolean = false,
    accessLine: String = "S3 access good — no backup yet",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "backup",
            leading = { ScreenBack() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "S3-compatible snapshot (R2, AWS, B2, MinIO). Encrypted on the phone before upload. Restore replaces local todos, notes, chats, pins, stocks, podcasts, alarms, and settings. Tokens stay off this file.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        Field("Endpoint", endpoint, "https://ACCOUNT.r2.cloudflarestorage.com")
        Field("Bucket", bucket, "bucket")
        Field("Access key", accessKey, "access key id")
        Field("Secret key", "", "secret access key")
        Text(accessLine, color = Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        Field("Encryption key", "", "passphrase")
        Text(
            if (includeAi) "[x] Include AI credentials" else "[ ] Include AI credentials",
            color = Paper,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            if (includeAi) {
                "On. Do not enable unless you use encrypted S3 backups or you understand the risk. Applies to S3 and the JSON share. OAuth tokens still stay on this phone."
            } else {
                "Off. API keys stay out of S3 backups and the JSON share."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text("Frequency", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            BackupFrequency.entries.forEach { item ->
                Text(
                    item.label,
                    color = if (item == frequency) Accent else Dim,
                )
            }
        }
        Text("Backup now", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Text("Restore from S3", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Text("Share unencrypted JSON", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        Text("Last backup: $lastBackup", color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
}

data class CalendarChromeRow(
    val name: String,
    val account: String,
    val checked: Boolean,
)

@Composable
fun CalendarChrome(
    accessGranted: Boolean = true,
    homeLine: String = "Home: dentist · 15:00",
    rows: List<CalendarChromeRow> = listOf(
        CalendarChromeRow("Personal", "alex@example.com", true),
        CalendarChromeRow("Work", "work@example.com", true),
        CalendarChromeRow("Holidays", "Holidays", false),
    ),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "calendar",
            leading = { ScreenBack() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Choose which calendars feed the next event under the home clock. Unchecked calendars stay off home even if they are on in the system calendar app.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (accessGranted) "Calendar access: granted" else "Calendar access: not granted",
            color = if (accessGranted) Accent else Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!accessGranted) {
            Text("Grant calendar access", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
            Text("Open Android settings", color = Paper, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(homeLine, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("Include on home", color = Dim, style = MaterialTheme.typography.labelSmall)
        if (!accessGranted) {
            Text(
                "Grant access to list calendars on this phone.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            rows.forEach { item ->
                Text(
                    if (item.checked) "[x] ${item.name}" else "[ ] ${item.name}",
                    color = Paper,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (item.account.isNotBlank() && item.account != item.name) {
                    Text(item.account, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AiProvidersChrome(
    settings: BuilderSettings = BuilderSettings(
        provider = LlmProvider.HERMES,
        hermesBaseUrl = "http://192.168.1.10:8642",
        hermesWebUrl = "http://192.168.1.10:9119",
    ),
) {
    val platform = AiPlatforms.of(settings.provider)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "ai",
            leading = { ScreenBack() },
        )
        Spacer(Modifier.height(16.dp))
        Text("Provider", color = Dim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        AiPlatforms.all.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                row.forEach { item ->
                    Text(
                        item.label,
                        color = if (settings.provider == item.provider) Accent else Dim,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (settings.provider == LlmProvider.HERMES) {
            Field("Web UI URL", settings.hermesWebUrl, HermesUrls.DEFAULT_WEBUI)
        } else if (platform.needsBaseUrl) {
            Field("Base URL", settings.hermesBaseUrl, platform.defaultLocalBase ?: HermesUrls.DEFAULT_API)
        }
        if (settings.provider == LlmProvider.HERMES) {
            Text("Open question in", color = Dim, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text("web ui", color = if (!settings.hermesOpenInHermex) Accent else Dim)
                Text("hermex", color = if (settings.hermesOpenInHermex) Accent else Dim)
            }
            Text(
                if (settings.hermesOpenInHermex) {
                    "The Hermes mark shares the question into Hermex. If Hermex is not installed it opens your Web UI."
                } else {
                    "The Hermes mark opens your Web UI in the browser."
                },
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Field(
            if (settings.provider == LlmProvider.HERMES) {
                "Web UI password (stored on device)"
            } else {
                "API key (stored on device)"
            },
            "",
            if (settings.provider == LlmProvider.HERMES) {
                "optional if the instance is open"
            } else if (platform.keyOptional) {
                "optional"
            } else {
                "optional if signed in"
            },
        )
        Field("Model", settings.model, platform.defaultModel)
        Spacer(Modifier.height(12.dp))
        Text("Web UI reachable.", color = Accent, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Text(
            "Each provider keeps its own sign-in. Tokens stay on the device and are sent only as a Bearer token.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ClockChrome(
    tab: String = "Timer",
    timer: String = "5:00",
    alarms: List<ClockAlarm> = emptyList(),
    zones: List<WorldClock> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = Clock.COMMAND,
            leading = { ScreenBack(Clock.BACK) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Timer", "Alarm", "Time Zones").forEach { label ->
                Text(label, color = if (label == tab) Accent else Dim, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f)) {
            when (tab) {
                "Alarm" -> {
                    if (alarms.isEmpty()) {
                        Text("Type 7:30am or Take out garbage Wednesdays 10:30pm.", color = Dim)
                    } else {
                        alarms.forEach { alarm ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Clock.formatAlarm(alarm.hour, alarm.minute), color = Paper)
                                    if (alarm.label.isNotBlank()) {
                                        Text(alarm.label, color = Dim)
                                    }
                                    Text(Clock.alarmStatus(alarm), color = if (alarm.enabled) Accent else Dim)
                                }
                                DeleteIcon()
                            }
                        }
                    }
                }
                "Time Zones" -> {
                    if (zones.isEmpty()) {
                        Text("Type a city, then Enter.", color = Dim)
                    } else {
                        zones.forEach { zone ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("11:42", color = Paper)
                                Text(zone.label, color = Dim)
                            }
                        }
                    }
                }
                else -> {
                    Text(timer, color = Paper, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Clock.PRESETS_MIN.forEach { min ->
                            Text(min.toString(), color = if (min == 5) Accent else Dim)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("start", color = Accent)
                        Text("reset", color = Dim)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow("")
    }
}

@Composable
fun ClockAlertChrome(
    kind: String = "timer",
    time: String = "5:00",
    label: String = "Time is up",
) {
    ClockAlertScreen(
        alert = if (kind == "alarm") {
            ClockAlert(kind = ClockAlertKind.ALARM, hour = 6, minute = 30, label = label)
        } else {
            ClockAlert(kind = ClockAlertKind.TIMER, durationMs = 5 * 60_000L)
        },
    )
}

fun sampleWeatherForecast(): WeatherForecast {
    val start = 1_778_000_000_000L
    return WeatherForecast(
        fetchedAt = start,
        latitude = 43.45,
        longitude = -80.49,
        timezone = "America/Toronto",
        current = WeatherNow(
            temperatureC = 18,
            feelsC = 16,
            code = 3,
            humidity = 64,
            precipProb = 40,
            windKmh = 12.4,
            windDir = 270,
            gustKmh = 22.0,
            pressureHpa = 1013.2,
            visibilityM = 24100.0,
            cloud = 80,
            dewC = 11,
            uv = 4.2,
        ),
        hourly = (0..7).map { i ->
            WeatherHour(
                epochMs = start + i * 3_600_000L,
                temperatureC = 18 - i,
                code = if (i < 3) 3 else 61,
                precipProb = 40 + i * 5,
                isDay = i < 6,
            )
        },
        daily = listOf(
            WeatherDay("2026-09-08", 3, 22, 11, 40, "06:42", "19:51", 5.4, 1.2),
            WeatherDay("2026-09-09", 61, 18, 10, 80, "06:43", "19:49", 3.1, 8.4),
            WeatherDay("2026-09-10", 0, 24, 12, 10, "06:44", "19:47", 8.2, 0.0),
            WeatherDay("2026-09-11", 2, 21, 13, 20, "06:45", "19:45", 5.8, 0.4),
            WeatherDay("2026-09-12", 45, 19, 12, 35, "06:46", "19:43", 4.2, 0.0),
            WeatherDay("2026-09-13", 95, 17, 11, 70, "06:47", "19:41", 2.8, 12.0),
            WeatherDay("2026-09-14", 1, 20, 10, 15, "06:48", "19:39", 5.1, 0.0),
        ),
        aqi = 42,
    )
}

@Composable
fun WeatherChrome(
    place: String = "Kitchener, Ontario, Canada",
    units: WeatherUnits = WeatherUnits.METRIC,
    forecast: WeatherForecast = sampleWeatherForecast(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "weather",
            leading = { ScreenBack() },
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f)) {
            WeatherBody(place = place, units = units, forecast = forecast)
        }
        Spacer(Modifier.height(8.dp))
        CommandRow("")
    }
}

@Composable
fun UsageChrome(snapshot: UsageSnapshot = Usage.sample(), selectedIndex: Int? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = Usage.COMMAND,
            leading = { ScreenBack(Usage.BACK) },
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f)) {
            UsageBody(snapshot = snapshot, selectedIndex = selectedIndex)
        }
        Spacer(Modifier.height(8.dp))
        CommandRow("")
    }
}

@Composable
private fun CommandRow(
    value: String,
    commandsOpen: Boolean = false,
    slashOpen: Boolean = false,
    prompt: String = ">",
    wrap: Boolean = false,
    confirm: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val todoWrap = prompt.singleOrNull()?.let { PrefixCommands.wrapsInput(it) } == true
    var wrapLines by remember { mutableIntStateOf(1) }
    val wrapExpanded = wrap || PrefixCommands.wrapExpanded(todoWrap, wrapLines)
    val overlay = slashOpen || commandsOpen
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val menuMax = commandMenuMaxHeight(maxHeight)
        val bounded = overlay && maxHeight < Dp.Infinity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (bounded) Modifier.fillMaxSize() else Modifier),
        ) {
        if (slashOpen) {
            if (bounded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    SlashCommandMenu(modifier = Modifier.heightIn(max = menuMax))
                }
            } else {
                SlashCommandMenu(modifier = Modifier.heightIn(max = menuMax))
            }
        } else if (commandsOpen) {
            if (bounded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    CommandMenu(modifier = Modifier.heightIn(max = menuMax))
                }
            } else {
                CommandMenu(modifier = Modifier.heightIn(max = menuMax))
            }
        }
        val calc = if ((prompt == ">" || prompt == "?") && !commandsOpen && !slashOpen) {
            Calculator.preview(value)
        } else {
            null
        }
        if (calc != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "= $calc",
                    color = Accent,
                    style = MaterialTheme.typography.bodyMedium,
                )
                CopyIcon(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp))
            }
        }
        Row(
            verticalAlignment = if (wrapExpanded) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().commandBarChrome(),
        ) {
            PromptGlyph(prompt = prompt, wrapField = wrapExpanded)
            Text(
                value.ifEmpty { "" },
                color = Paper,
                style = MaterialTheme.typography.bodyLarge,
                onTextLayout = { wrapLines = it.lineCount },
                modifier = Modifier.weight(1f),
            )
            if (confirm) {
                CheckIcon(Modifier.padding(start = 12.dp, top = if (wrapExpanded) 2.dp else 0.dp))
            } else if (wrap || prompt.singleOrNull()?.let { PrefixCommands.showsSend(it) } == true) {
                SendIcon(Modifier.padding(start = 12.dp, top = if (wrapExpanded) 2.dp else 6.dp, bottom = if (wrapExpanded) 0.dp else 6.dp))
            }
        }
        CommandBarRule()
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String) {
    Text(label, color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
    Text(
        value.ifEmpty { placeholder },
        color = if (value.isEmpty()) Dim else Paper,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .inputChrome()
            .padding(vertical = 6.dp),
    )
    FieldRule()
}

@Composable
fun HubReplyBar(value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .commandBarChrome(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { "reply" },
                color = if (value.isEmpty()) Dim else Paper,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            SendIcon(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
        }
        CommandBarRule()
    }
}

@Composable
fun SendIcon(modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = size.minDimension * 0.16f
        val midY = size.height / 2f
        val tip = Offset(size.width - pad, midY)
        drawLine(
            color = accent,
            start = Offset(pad, midY),
            end = tip,
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        val ah = size.minDimension * 0.28f
        drawLine(
            color = accent,
            start = tip,
            end = Offset(tip.x - ah, tip.y - ah * 0.9f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = tip,
            end = Offset(tip.x - ah, tip.y + ah * 0.9f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
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
    val color = Paper
    Canvas(modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val pad = size.minDimension * 0.08f
        val bodyH = size.height * 0.70f
        drawRoundRect(
            color = color,
            topLeft = Offset(pad, pad),
            size = Size(size.width - pad * 2f, bodyH),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        val tail = size.width * 0.30f
        drawLine(
            color = color,
            start = Offset(tail, pad + bodyH),
            end = Offset(tail - size.width * 0.14f, size.height - pad),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = color,
            start = Offset(tail + size.width * 0.20f, pad + bodyH),
            end = Offset(tail - size.width * 0.14f, size.height - pad),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun UsageIcon(modifier: Modifier = Modifier) {
    val color = Paper
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val gap = w * 0.14f
        val bar = (w - gap * 2f) / 3f
        val heights = listOf(h * 0.95f, h * 0.62f, h * 0.34f)
        heights.forEachIndexed { i, barH ->
            drawRect(
                color = color,
                topLeft = Offset(i * (bar + gap), h - barH),
                size = Size(bar, barH),
            )
        }
    }
}

@Composable
fun HomePodcastMarkIcon(mark: HomePodcastMark, modifier: Modifier = Modifier) {
    when (mark) {
        HomePodcastMark.PAUSE -> PlayPauseIcon(playing = true, modifier = modifier, color = Paper)
        HomePodcastMark.PLAY -> PlayPauseIcon(playing = false, modifier = modifier, color = Paper)
        HomePodcastMark.HEADPHONES -> HeadphonesIcon(modifier)
    }
}

@Composable
fun HeadphonesIcon(modifier: Modifier = Modifier) {
    val color = Paper
    Canvas(modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = size.minDimension * 0.08f
        val band = Path().apply {
            moveTo(pad + size.width * 0.12f, size.height * 0.55f)
            cubicTo(
                pad + size.width * 0.12f, pad,
                size.width - pad - size.width * 0.12f, pad,
                size.width - pad - size.width * 0.12f, size.height * 0.55f,
            )
        }
        drawPath(band, color = color, style = stroke)
        val cupW = size.width * 0.22f
        val cupH = size.height * 0.38f
        drawRoundRect(
            color = color,
            topLeft = Offset(pad, size.height * 0.48f),
            size = Size(cupW, cupH),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - pad - cupW, size.height * 0.48f),
            size = Size(cupW, cupH),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
    }
}

@Composable
fun AnalogClock(
    hour: Int,
    minute: Int,
    second: Int = 0,
    modifier: Modifier = Modifier,
    faceSize: Dp = 72.dp,
) {
    val paper = Paper
    val dim = Dim
    val accent = Accent
    Canvas(modifier.size(faceSize)) {
        val ring = 1.6.dp.toPx()
        val r = size.minDimension / 2f - ring / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = dim, radius = r, style = Stroke(width = ring))
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble() - 90.0)
            val inner = r * 0.82f
            val outer = r * 0.94f
            drawLine(
                color = dim,
                start = Offset(c.x + inner * kotlin.math.cos(a).toFloat(), c.y + inner * kotlin.math.sin(a).toFloat()),
                end = Offset(c.x + outer * kotlin.math.cos(a).toFloat(), c.y + outer * kotlin.math.sin(a).toFloat()),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        fun hand(degrees: Double, length: Float, width: Float, color: androidx.compose.ui.graphics.Color) {
            val a = Math.toRadians(degrees - 90.0)
            drawLine(
                color = color,
                start = c,
                end = Offset(c.x + length * kotlin.math.cos(a).toFloat(), c.y + length * kotlin.math.sin(a).toFloat()),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        val hourDeg = (hour % 12) * 30.0 + minute * 0.5 + second * (0.5 / 60.0)
        val minuteDeg = minute * 6.0 + second * 0.1
        val secondDeg = second * 6.0
        hand(hourDeg, r * 0.52f, 2.6.dp.toPx(), paper)
        hand(minuteDeg, r * 0.72f, 2.0.dp.toPx(), paper)
        hand(secondDeg, r * 0.78f, 1.2.dp.toPx(), accent)
        drawCircle(color = paper, radius = 2.2.dp.toPx(), center = c)
    }
}

fun parseHomeClock(time: String): Pair<Int, Int> {
    val parts = time.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    return hour to minute
}

@Composable
fun ProviderIcon(provider: LlmProvider, modifier: Modifier = Modifier) {
    val res = when (provider) {
        LlmProvider.XAI -> R.drawable.ic_logo_grok
        LlmProvider.OPENAI, LlmProvider.OPENROUTER, LlmProvider.GROQ, LlmProvider.DEEPSEEK,
        LlmProvider.MISTRAL, LlmProvider.LMSTUDIO, LlmProvider.OLLAMA, LlmProvider.GENERIC,
        -> R.drawable.ic_logo_openai
        LlmProvider.ANTHROPIC -> R.drawable.ic_logo_claude
        LlmProvider.HERMES -> R.drawable.ic_logo_hermes
        LlmProvider.GEMINI -> R.drawable.ic_logo_gemini
    }
    val tint = when (provider) {
        LlmProvider.XAI, LlmProvider.OPENAI -> ColorFilter.tint(Accent)
        else -> null
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = tint,
        modifier = modifier.size(22.dp),
    )
}

internal val ProviderMenuBelowIcon = 34.dp
internal val ProviderMenuEndInset = 32.dp

@Composable
fun ProviderMenu(
    current: LlmProvider,
    onPick: (LlmProvider) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .wrapContentWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .background(Ink)
            .border(1.dp, Line)
            .padding(vertical = 4.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AiPlatforms.all.forEach { item ->
            Row(
                modifier = Modifier
                    .clickable { onPick(item.provider) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    item.label,
                    color = if (item.provider == current) Accent else Paper,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                ProviderIcon(item.provider)
            }
        }
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
    val dim = Dim
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val inset = size.minDimension * 0.22f
        drawLine(
            color = dim,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = dim,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, modifier: Modifier = Modifier, color: Color = Accent) {
    if (playing) PauseIcon(modifier, color) else PlayIcon(modifier, color)
}

@Composable
fun PlayIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Canvas(modifier.size(22.dp)) {
        val pad = size.minDimension * 0.18f
        val path = Path().apply {
            moveTo(pad, pad)
            lineTo(size.width - pad, size.height / 2f)
            lineTo(pad, size.height - pad)
            close()
        }
        drawPath(path, color = color, style = Fill)
    }
}

@Composable
fun PauseIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width * 0.22f
        val gap = size.width * 0.16f
        val x1 = size.width / 2f - gap / 2f - w
        val x2 = size.width / 2f + gap / 2f
        val top = size.height * 0.16f
        val h = size.height * 0.68f
        drawRect(color = color, topLeft = Offset(x1, top), size = Size(w, h))
        drawRect(color = color, topLeft = Offset(x2, top), size = Size(w, h))
    }
}

@Composable
fun DownloadIcon(filled: Boolean, modifier: Modifier = Modifier) {
    val color = if (filled) Accent else Paper
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = size.minDimension * 0.14f
        val midX = size.width / 2f
        val arrowTop = pad
        val arrowBottom = size.height * 0.58f
        val trayY = size.height - pad
        val trayX = pad
        val trayW = size.width - pad * 2f
        val arrow = Path().apply {
            moveTo(midX, arrowTop)
            lineTo(midX, arrowBottom)
        }
        drawPath(arrow, color = color, style = stroke)
        val head = size.minDimension * 0.22f
        drawLine(color, Offset(midX, arrowBottom), Offset(midX - head, arrowBottom - head), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(midX, arrowBottom), Offset(midX + head, arrowBottom - head), stroke.width, StrokeCap.Round)
        val tray = Path().apply {
            moveTo(trayX, size.height * 0.62f)
            lineTo(trayX, trayY)
            lineTo(trayX + trayW, trayY)
            lineTo(trayX + trayW, size.height * 0.62f)
        }
        if (filled) {
            drawPath(tray, color = color, style = stroke)
            drawRect(
                color = color,
                topLeft = Offset(trayX, size.height * 0.78f),
                size = Size(trayW, trayY - size.height * 0.78f),
            )
        } else {
            drawPath(tray, color = color, style = stroke)
        }
    }
}

@Composable
fun EditIcon(modifier: Modifier = Modifier) {
    val dim = Dim
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val s = size.minDimension
        val body = Path().apply {
            moveTo(s * 0.18f, s * 0.82f)
            lineTo(s * 0.24f, s * 0.60f)
            lineTo(s * 0.60f, s * 0.24f)
            lineTo(s * 0.76f, s * 0.40f)
            lineTo(s * 0.40f, s * 0.76f)
            close()
        }
        drawPath(path = body, color = dim, style = stroke)
        drawLine(
            color = dim,
            start = Offset(s * 0.30f, s * 0.66f),
            end = Offset(s * 0.38f, s * 0.74f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dim,
            start = Offset(s * 0.56f, s * 0.18f),
            end = Offset(s * 0.82f, s * 0.44f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dim,
            start = Offset(s * 0.56f, s * 0.18f),
            end = Offset(s * 0.60f, s * 0.24f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dim,
            start = Offset(s * 0.82f, s * 0.44f),
            end = Offset(s * 0.76f, s * 0.40f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier) {
    val color = Accent
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.52f),
            end = Offset(size.width * 0.40f, size.height * 0.74f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.40f, size.height * 0.74f),
            end = Offset(size.width * 0.84f, size.height * 0.26f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun InfoIcon(modifier: Modifier = Modifier) {
    val dim = Dim
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val r = size.minDimension / 2f - stroke.width
        drawCircle(color = dim, radius = r, style = stroke)
        val cx = size.width / 2f
        drawCircle(color = dim, radius = 1.3.dp.toPx(), center = Offset(cx, size.height * 0.32f))
        drawLine(
            color = dim,
            start = Offset(cx, size.height * 0.46f),
            end = Offset(cx, size.height * 0.72f),
            strokeWidth = stroke.width,
        )
    }
}

@Composable
fun CaretLink(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Paper,
    caretColor: Color = Accent,
) {
    val caret = text.lastIndexOf('>')
    Text(
        text = if (caret >= 0) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = color)) { append(text.substring(0, caret)) }
                withStyle(SpanStyle(color = caretColor)) { append(">") }
            }
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(color = color)) { append(text) }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val line = Line
    Canvas(modifier.size(size)) {
        drawRoundRect(
            color = line,
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
    }
}
