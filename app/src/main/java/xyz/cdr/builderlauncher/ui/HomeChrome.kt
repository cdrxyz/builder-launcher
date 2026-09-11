@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package xyz.cdr.builderlauncher.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.AccessCheck
import xyz.cdr.builderlauncher.ai.HermesUrls
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.ai.OAuthSpec
import xyz.cdr.builderlauncher.ai.ProviderAccess
import xyz.cdr.builderlauncher.ai.ProviderHandoff
import xyz.cdr.builderlauncher.ai.oauth.DevicePending
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.ai.oauth.PkceSession
import xyz.cdr.builderlauncher.apps.AppList
import xyz.cdr.builderlauncher.backup.BackupFrequency
import xyz.cdr.builderlauncher.backup.BackupService
import xyz.cdr.builderlauncher.backup.S3Access
import xyz.cdr.builderlauncher.backup.S3Signer
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.apps.LaunchableApp
import xyz.cdr.builderlauncher.calendar.CalendarRepository
import xyz.cdr.builderlauncher.calendar.CalendarSelection
import xyz.cdr.builderlauncher.calendar.DeviceCalendar
import xyz.cdr.builderlauncher.calendar.UpcomingEvent
import xyz.cdr.builderlauncher.calendar.UpcomingEvents
import xyz.cdr.builderlauncher.clock.Clock
import xyz.cdr.builderlauncher.clock.ClockAlertService
import xyz.cdr.builderlauncher.clock.ClockScheduler
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.clock.ClockSoundPlayer
import xyz.cdr.builderlauncher.clock.ClockStore
import xyz.cdr.builderlauncher.clock.ClockTab
import xyz.cdr.builderlauncher.clock.TimerState
import xyz.cdr.builderlauncher.commands.AppPick
import xyz.cdr.builderlauncher.commands.Calculator
import xyz.cdr.builderlauncher.commands.Command
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.commands.CommandParser
import xyz.cdr.builderlauncher.commands.ContactAction
import xyz.cdr.builderlauncher.commands.ExecResult
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.commands.SlashCommand
import xyz.cdr.builderlauncher.commands.SlashCommands
import xyz.cdr.builderlauncher.contacts.PhoneContact
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.ChatStore
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.StockInsert
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.data.AppIcons
import xyz.cdr.builderlauncher.data.ClockFace
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.data.ListReorder
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.Notes
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.home.BackPress
import xyz.cdr.builderlauncher.home.BackResult
import xyz.cdr.builderlauncher.hub.HubMessages
import xyz.cdr.builderlauncher.hub.HubStore
import xyz.cdr.builderlauncher.stocks.HomeTicker
import xyz.cdr.builderlauncher.stocks.HomeTickerLine
import xyz.cdr.builderlauncher.stocks.StockChartData
import xyz.cdr.builderlauncher.stocks.StockDetails
import xyz.cdr.builderlauncher.stocks.StockHit
import xyz.cdr.builderlauncher.stocks.StockQuote
import xyz.cdr.builderlauncher.stocks.StockRange
import xyz.cdr.builderlauncher.stocks.StockCagr
import xyz.cdr.builderlauncher.stocks.StockStatLine
import xyz.cdr.builderlauncher.stocks.Stocks
import xyz.cdr.builderlauncher.stocks.StocksCsv
import xyz.cdr.builderlauncher.stocks.StocksRepository
import xyz.cdr.builderlauncher.stocks.WatchItem
import xyz.cdr.builderlauncher.podcasts.HomePodcastMark
import xyz.cdr.builderlauncher.podcasts.EpisodeOrder
import xyz.cdr.builderlauncher.podcasts.EpisodeProgress
import xyz.cdr.builderlauncher.podcasts.DownloadProgress
import xyz.cdr.builderlauncher.podcasts.PodcastArtwork
import xyz.cdr.builderlauncher.podcasts.PodcastHit
import xyz.cdr.builderlauncher.podcasts.PodcastHomeRow
import xyz.cdr.builderlauncher.podcasts.PodcastOpml
import xyz.cdr.builderlauncher.podcasts.PodcastPlaybackService
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import xyz.cdr.builderlauncher.podcasts.PodcastEpisode
import xyz.cdr.builderlauncher.podcasts.Podcasts
import xyz.cdr.builderlauncher.podcasts.PodcastsRepository
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Gain
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.CommandBarRule
import xyz.cdr.builderlauncher.ui.theme.commandBarChrome
import xyz.cdr.builderlauncher.usage.PinUsageMark
import xyz.cdr.builderlauncher.usage.Usage
import xyz.cdr.builderlauncher.usage.UsagePeriod
import xyz.cdr.builderlauncher.usage.UsageReader
import xyz.cdr.builderlauncher.usage.UsageStore
import xyz.cdr.builderlauncher.usage.UsageToday
import xyz.cdr.builderlauncher.weather.WeatherKind
import xyz.cdr.builderlauncher.weather.WeatherPlace
import xyz.cdr.builderlauncher.weather.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Composable
internal fun ClockHeader(
    weather: String?,
    weatherKind: WeatherKind? = null,
    isDay: Boolean = true,
    ticker: HomeTickerLine?,
    event: UpcomingEvent?,
    timer: TimerState,
    analog: Boolean,
    todosToday: Int,
    productiveShare: Int?,
    onOpenClock: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenTicker: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenUsage: () -> Unit,
    playing: Boolean = false,
    episodeLoaded: Boolean = false,
    onOpenPodcasts: () -> Unit = {},
    onTogglePlayback: () -> Unit = {},
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(timer.running, timer.endsAt, analog, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now.value = System.currentTimeMillis()
            while (true) {
                now.value = System.currentTimeMillis()
                delay(Clock.homeTickMs(timer.running, analog))
            }
        }
    }
    val clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now.value))
    val time = Clock.homeClockLabel(timer, now.value, clockText)
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now.value))
    val cal = Calendar.getInstance().apply { timeInMillis = now.value }
    val eventLine = event?.let { UpcomingEvents.line(it, now.value) }
    var pausedForMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playing, episodeLoaded) {
        if (playing && episodeLoaded) {
            pausedForMs = null
        } else if (episodeLoaded) {
            val start = System.currentTimeMillis()
            pausedForMs = 0L
            while (true) {
                delay(200)
                val elapsed = System.currentTimeMillis() - start
                pausedForMs = elapsed
                if (elapsed >= Podcasts.HOME_MARK_IDLE_MS) break
            }
        } else {
            pausedForMs = null
        }
    }
    val mark = Podcasts.homePodcastMark(playing, episodeLoaded, pausedForMs)
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                HomePodcastMarkIcon(
                    mark,
                    Modifier
                        .semantics {
                            contentDescription = when (mark) {
                                HomePodcastMark.PAUSE -> "pause"
                                HomePodcastMark.PLAY -> "play"
                                HomePodcastMark.HEADPHONES -> "podcasts"
                            }
                        }
                        .clickable {
                            when (mark) {
                                HomePodcastMark.HEADPHONES -> onOpenPodcasts()
                                HomePodcastMark.PLAY, HomePodcastMark.PAUSE -> onTogglePlayback()
                            }
                        }
                        .padding(top = 10.dp, end = 4.dp, bottom = 6.dp),
                )
                if (!weather.isNullOrBlank()) {
                    HomeWeatherMark(
                        weather = weather,
                        kind = weatherKind,
                        isDay = isDay,
                        modifier = Modifier
                            .semantics { contentDescription = weather }
                            .clickable { onOpenWeather() },
                    )
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                if (ticker != null) {
                    HomeTickerMark(
                        symbol = ticker.symbol,
                        change = ticker.change,
                        up = ticker.up,
                        modifier = Modifier
                            .semantics { contentDescription = "${ticker.symbol} ${ticker.change}" }
                            .clickable { onOpenTicker() },
                    )
                }
                MessagesIcon(
                    Modifier
                        .semantics { contentDescription = "messages" }
                        .clickable { onOpenHub() }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 56.dp)
                .clipToBounds(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val analogFace = analog && !timer.running
                ClockFaceRow(
                    analog = analogFace,
                    time = time,
                    hour = cal.get(Calendar.HOUR),
                    minute = cal.get(Calendar.MINUTE),
                    second = cal.get(Calendar.SECOND),
                    todosToday = todosToday,
                    productiveShare = productiveShare,
                    onOpenClock = onOpenClock,
                    onOpenTodos = onOpenTodos,
                    onOpenUsage = onOpenUsage,
                )
                Column(
                    Modifier.clickable { onOpenClock() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (analogFace) {
                        Spacer(Modifier.height(16.dp))
                        Text(time, color = Paper, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!eventLine.isNullOrBlank()) {
                Text(
                    eventLine,
                    color = Dim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .semantics { contentDescription = eventLine }
                        .clickable { onOpenEvent() },
                )
            }
        }
    }
}

@Composable
internal fun StockStatPair(row: StockStatLine) {
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
internal fun TodoPreview(
    open: List<LocalItem>,
    onToggle: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        open.forEach { item ->
            TodoLine(item, onToggle = { onToggle(item.id) }, compact = true)
        }
        CaretLink(
            HomeTodos.MORE_TASKS,
            modifier = Modifier
                .clickable { onMore() }
                .padding(vertical = 4.dp),
            color = Dim,
            caretColor = Dim,
        )
    }
}

@Composable
internal fun TodoLine(
    item: LocalItem,
    onToggle: () -> Unit,
    compact: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.text,
            color = if (item.done) Dim else Paper,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            modifier = textModifier
                .weight(1f)
                .then(if (item.done || onEdit == null) Modifier.clickable { onToggle() } else Modifier)
                .padding(vertical = if (compact) 4.dp else 6.dp),
        )
        if (onEdit != null) {
            EditIcon(
                Modifier
                    .semantics { contentDescription = "edit task" }
                    .clickable { onEdit() }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (onDelete != null) {
            DeleteIcon(
                Modifier
                    .semantics { contentDescription = "delete task" }
                    .clickable { onDelete() }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
internal fun CommandBar(
    prompt: Char,
    value: String,
    hardware: Boolean,
    wrap: Boolean = false,
    grabFocus: Boolean = true,
    onValue: (String) -> Unit,
    onPick: (Char) -> Unit,
    onClearMode: () -> Unit,
    onSubmit: () -> Unit,
    onSlash: (SlashCommand) -> Unit,
    onHub: () -> Unit,
    onLeft: (() -> Unit)? = null,
    showSubmit: Boolean = false,
    modifier: Modifier = Modifier,
    actionMenuOpen: Boolean? = null,
    onActionMenuChange: (Boolean) -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = LocalContext.current
    val reportActionMenu = rememberUpdatedState(onActionMenuChange)
    var localMenuOpen by remember { mutableStateOf(false) }
    val menuOpen = actionMenuOpen ?: localMenuOpen
    fun setMenuOpen(value: Boolean) {
        if (actionMenuOpen == null) localMenuOpen = value
        reportActionMenu.value(value)
    }
    var selected by remember { mutableStateOf(0) }
    var slashSelected by remember { mutableStateOf(0) }
    val slashMode = prompt == SlashCommands.PROMPT
    val slashMatches = if (slashMode) SlashCommands.matches(value) else emptyList()
    val wrapAllowed = wrap || PrefixCommands.wrapsInput(prompt)
    var wrapLines by remember { mutableIntStateOf(1) }
    val wrapExpanded = wrap || PrefixCommands.wrapExpanded(PrefixCommands.wrapsInput(prompt), wrapLines)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hardware, grabFocus, resumeTick) {
        if (!grabFocus) return@LaunchedEffect
        focus.requestFocus()
        if (hardware) keyboard?.hide() else keyboard?.show()
    }
    LaunchedEffect(prompt) {
        slashSelected = 0
        if (slashMode) setMenuOpen(false)
    }
    LaunchedEffect(slashMatches.size, value) {
        if (slashSelected >= slashMatches.size) slashSelected = 0
    }
    fun pick(index: Int) {
        val cmd = PrefixCommands.all.getOrNull(index) ?: return
        setMenuOpen(false)
        selected = 0
        onPick(cmd.glyph)
        focus.requestFocus()
    }
    fun pickSlash(index: Int) {
        val cmd = slashMatches.getOrNull(index) ?: return
        onSlash(cmd)
        focus.requestFocus()
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val menuMax = commandMenuMaxHeight(maxHeight)
        val overlay = slashMode || menuOpen
        val bounded = overlay && maxHeight < Dp.Infinity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (bounded) Modifier.fillMaxSize() else Modifier),
        ) {
        if (slashMode) {
            val menu: @Composable (Modifier) -> Unit = { menuMod ->
                SlashCommandMenu(
                    commands = slashMatches,
                    selected = slashSelected,
                    onSelect = { cmd ->
                        val index = slashMatches.indexOf(cmd)
                        if (index >= 0) pickSlash(index)
                    },
                    modifier = menuMod,
                )
            }
            if (bounded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    menu(Modifier.heightIn(max = menuMax))
                }
            } else {
                menu(Modifier.heightIn(max = menuMax))
            }
        } else if (menuOpen) {
            val menu: @Composable (Modifier) -> Unit = { menuMod ->
                CommandMenu(
                    selected = selected,
                    onSelect = { cmd ->
                        val index = PrefixCommands.all.indexOf(cmd)
                        if (index >= 0) pick(index)
                    },
                    modifier = menuMod,
                )
            }
            if (bounded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    menu(Modifier.heightIn(max = menuMax))
                }
            } else {
                menu(Modifier.heightIn(max = menuMax))
            }
        }
        val calc = if (
            (prompt == PrefixCommands.DEFAULT_PROMPT || prompt == '?') &&
            !menuOpen &&
            !slashMode
        ) {
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
                CopyIcon(
                    Modifier
                        .semantics { contentDescription = "copy result" }
                        .clickable {
                            ctx.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("result", calc))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }
        Row(
            verticalAlignment = if (wrapExpanded) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().commandBarChrome(),
        ) {
            PromptGlyph(
                prompt = prompt.toString(),
                wrapField = wrapExpanded,
                onClick = {
                    if (slashMode) {
                        onClearMode()
                    } else {
                        val next = !menuOpen
                        if (next) selected = 0
                        setMenuOpen(next)
                    }
                },
            )
            BasicTextField(
                value = value,
                onValueChange = {
                    if (menuOpen) {
                        if (it != value) setMenuOpen(false)
                    } else {
                        onValue(it)
                    }
                },
                singleLine = !wrapAllowed,
                maxLines = if (wrapAllowed) 8 else 1,
                onTextLayout = { wrapLines = it.lineCount },
                cursorBrush = SolidColor(Accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
                keyboardOptions = if (PrefixCommands.usesRawSymbolKeyboard(prompt)) {
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    )
                } else {
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = PrefixCommands.usesAutocorrect(prompt),
                        imeAction = ImeAction.Go,
                    )
                },
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (slashMode && slashMatches.isNotEmpty()) {
                            pickSlash(slashSelected)
                        } else {
                            onSubmit()
                        }
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(if (wrapAllowed) Modifier.heightIn(max = 160.dp) else Modifier)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                        val code = event.nativeKeyEvent.keyCode
                        val ch = event.nativeKeyEvent.unicodeChar.toChar()
                        if (slashMode) {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (slashMatches.isNotEmpty()) {
                                        slashSelected = (slashSelected - 1).mod(slashMatches.size)
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (slashMatches.isNotEmpty()) {
                                        slashSelected = (slashSelected + 1).mod(slashMatches.size)
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (slashMatches.isNotEmpty()) pickSlash(slashSelected) else onSubmit()
                                    true
                                }
                                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                                    if (value.isEmpty()) {
                                        onClearMode()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else if (menuOpen) {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    selected = (selected - 1).mod(PrefixCommands.all.size)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    selected = (selected + 1).mod(PrefixCommands.all.size)
                                    true
                                }
                                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    pick(selected)
                                    true
                                }
                                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                                    setMenuOpen(false)
                                    true
                                }
                                else -> {
                                    setMenuOpen(false)
                                    true
                                }
                            }
                        } else {
                            when (code) {
                                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    onSubmit()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    onHub()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (onLeft != null) {
                                        onLeft()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                                    if (value.isEmpty() && PrefixCommands.isModePrompt(prompt)) {
                                        onClearMode()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> {
                                    if (value.isEmpty() && ch == '>') {
                                        setMenuOpen(true)
                                        selected = 0
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                        }
                    },
            )
            if (showSubmit) {
                CheckIcon(
                    Modifier
                        .semantics { contentDescription = "save task" }
                        .clickable { onSubmit() }
                        .padding(start = 12.dp, top = if (wrapExpanded) 2.dp else 0.dp),
                )
            } else if (wrap || PrefixCommands.showsSend(prompt)) {
                SendIcon(
                    Modifier
                        .semantics { contentDescription = "send" }
                        .clickable { onSubmit() }
                        .padding(start = 12.dp, top = if (wrapExpanded) 2.dp else 6.dp, bottom = if (wrapExpanded) 0.dp else 6.dp),
                )
            }
        }
        CommandBarRule()
        }
    }
}

@Composable
internal fun ThinkingDots() {
    var n by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(420)
            n = n % 3 + 1
        }
    }
    Text(".".repeat(n), color = Dim, style = MaterialTheme.typography.bodyLarge)
}

@Composable
internal fun HelpBlock() {
    val lines = listOf(
        "@name message   text",
        "#name           call",
        "*title when     calendar",
        "-todo           save todo",
        "+               write a note",
        "notes           all notes",
        "apps            all apps",
        "\$ticker         add a stock",
        "stocks          all stocks",
        "podcasts        all podcasts",
        "?               ask AI",
        "/               slash commands",
        "pin Termux      pin an app",
        "unpin Termux    unpin",
        "hub / notes / apps / stocks / podcasts / clock / weather / settings",
        "2+2             calculator",
        "type a name     launch app",
        "hold an app     pin or unpin",
        "hold a pin      drag to reorder",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, color = Dim, style = MaterialTheme.typography.bodyMedium) }
    }
}
