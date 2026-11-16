package xyz.cdr.builderlauncher.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.cdr.builderlauncher.ai.AiFallback
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.AccessCheck
import xyz.cdr.builderlauncher.ai.HermesUrls
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.ai.OAuthSpec
import xyz.cdr.builderlauncher.ai.ProviderAccess
import xyz.cdr.builderlauncher.ai.ProviderHandoff
import xyz.cdr.builderlauncher.ai.oauth.CredentialResolver
import xyz.cdr.builderlauncher.ai.oauth.DevicePending
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.ai.oauth.PkceSession
import xyz.cdr.builderlauncher.apps.AppList
import xyz.cdr.builderlauncher.backup.AccountClient
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
import xyz.cdr.builderlauncher.clock.ParsedTimer
import xyz.cdr.builderlauncher.clock.TimerState
import xyz.cdr.builderlauncher.commands.AppPick
import xyz.cdr.builderlauncher.commands.AppPickQuery
import xyz.cdr.builderlauncher.commands.AppTaskFallback
import xyz.cdr.builderlauncher.commands.Calculator
import xyz.cdr.builderlauncher.commands.Command
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.commands.CommandParser
import xyz.cdr.builderlauncher.commands.ContactAction
import xyz.cdr.builderlauncher.commands.ExecResult
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.commands.PromptRouting
import xyz.cdr.builderlauncher.commands.SlashCommand
import xyz.cdr.builderlauncher.commands.SlashCommands
import xyz.cdr.builderlauncher.contacts.PhoneContact
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.data.ChatMessage
import xyz.cdr.builderlauncher.data.ChatStore
import xyz.cdr.builderlauncher.data.Chats
import xyz.cdr.builderlauncher.data.HabitScheduler
import xyz.cdr.builderlauncher.data.HabitStore
import xyz.cdr.builderlauncher.data.Habits
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
import xyz.cdr.builderlauncher.hub.HubAppSelection
import xyz.cdr.builderlauncher.hub.HubFilter
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
import xyz.cdr.builderlauncher.podcasts.PodcastArtwork
import xyz.cdr.builderlauncher.podcasts.PodcastHit
import xyz.cdr.builderlauncher.podcasts.PodcastHomeRow
import xyz.cdr.builderlauncher.podcasts.PodcastOpml
import xyz.cdr.builderlauncher.podcasts.PodcastPlaybackService
import xyz.cdr.builderlauncher.podcasts.PodcastPlayer
import xyz.cdr.builderlauncher.podcasts.PodcastShow
import xyz.cdr.builderlauncher.read.ArticleDownload
import xyz.cdr.builderlauncher.read.ArticlePlaybackService
import xyz.cdr.builderlauncher.read.ArticlePlayer
import xyz.cdr.builderlauncher.read.ChosenSpeaker
import xyz.cdr.builderlauncher.read.PreparedGrok
import xyz.cdr.builderlauncher.read.ReadArticle
import xyz.cdr.builderlauncher.read.ReadSpeaker
import xyz.cdr.builderlauncher.read.Reads
import xyz.cdr.builderlauncher.read.ReadsRepository
import xyz.cdr.builderlauncher.videos.VideoMedia
import xyz.cdr.builderlauncher.videos.Videos
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

import androidx.compose.foundation.pager.PagerState
import xyz.cdr.builderlauncher.data.HabitSnapshot
import xyz.cdr.builderlauncher.podcasts.PlaybackState
import xyz.cdr.builderlauncher.read.ArticleControls
import xyz.cdr.builderlauncher.read.ReadPlayback
import xyz.cdr.builderlauncher.weather.WeatherSnapshot

internal class HomePageArgs(
    val local: List<xyz.cdr.builderlauncher.data.LocalItem>,
    val settings: xyz.cdr.builderlauncher.data.BuilderSettings,
    val pendingComplete: Map<String, Int>,
    val watch: List<WatchItem>,
    val quotes: Map<String, StockQuote>,
    val tickerIndex: Int,
    val forecast: WeatherSnapshot?,
    val upcoming: xyz.cdr.builderlauncher.calendar.UpcomingEvent?,
    val clockState: xyz.cdr.builderlauncher.clock.ClockSnapshot,
    val usageToday: UsageToday,
    val clock: xyz.cdr.builderlauncher.clock.ClockStore,
    val calendar: xyz.cdr.builderlauncher.calendar.CalendarRepository,
    val page: Page,
    val setPage: (Page) -> Unit,
    val pagerState: PagerState,
    val index: Int,
    val grokPlayback: ReadPlayback,
    val playback: PlaybackState,
    val podcastProgress: Map<String, EpisodeProgress>,
    val readArticles: List<ReadArticle>,
    val readSpeed: Float,
    val articleControls: ArticleControls,
    val actionMenuOpen: Boolean,
    val setActionMenuOpen: (Boolean) -> Unit,
    val prompt: Char,
    val setPrompt: (Char) -> Unit,
    val input: String,
    val setInput: (String) -> Unit,
    val people: List<xyz.cdr.builderlauncher.contacts.PhoneContact>,
    val setPeople: (List<xyz.cdr.builderlauncher.contacts.PhoneContact>) -> Unit,
    val appQuery: Boolean,
    val setAppQuery: (Boolean) -> Unit,
    val leavingComplete: Map<String, Boolean>,
    val habitState: HabitSnapshot,
    val habits: xyz.cdr.builderlauncher.data.HabitStore,
    val ctx: android.content.Context,
    val smsDraft: xyz.cdr.builderlauncher.commands.ExecResult.SmsDraft?,
    val setSmsDraft: (xyz.cdr.builderlauncher.commands.ExecResult.SmsDraft?) -> Unit,
    val help: Boolean,
    val pinPkgs: List<String>,
    val appsEpoch: Int,
    val apps: xyz.cdr.builderlauncher.apps.InstalledApps,
    val choices: List<xyz.cdr.builderlauncher.apps.LaunchableApp>,
    val setChoices: (List<xyz.cdr.builderlauncher.apps.LaunchableApp>) -> Unit,
    val pick: xyz.cdr.builderlauncher.commands.AppPick,
    val executor: xyz.cdr.builderlauncher.commands.CommandExecutor,
    val pins: xyz.cdr.builderlauncher.data.PinnedApps,
    val contactAction: xyz.cdr.builderlauncher.commands.ContactAction?,
    val setContactAction: (xyz.cdr.builderlauncher.commands.ContactAction?) -> Unit,
    val contactBody: String,
    val hardware: Boolean,
    val onStrip: Boolean,
    val mode: () -> xyz.cdr.builderlauncher.commands.PrefixCommands.Mode,
    val applyMode: (xyz.cdr.builderlauncher.commands.PrefixCommands.Mode) -> Unit,
    val runCommand: () -> Unit,
    val pickSlash: (xyz.cdr.builderlauncher.commands.SlashCommand) -> Unit,
    val taskMode: () -> Unit,
    val openClock: (xyz.cdr.builderlauncher.clock.ClockTab?) -> Unit,
    val openWeather: () -> Unit,
    val openHub: () -> Unit,
    val openStocksList: () -> Unit,
    val openUsage: () -> Unit,
    val openPodcastsList: () -> Unit,
    val openNotesList: () -> Unit,
    val openAppsList: (Boolean) -> Unit,
    val togglePlayback: () -> Unit,
    val toggleTodoAnimated: (String) -> Unit,
    val clearBar: () -> Unit,
)

@Composable
internal fun ColumnScope.HomePageContent(a: HomePageArgs) {
    val local = a.local
    val settings = a.settings
    val pendingComplete = a.pendingComplete
    val watch = a.watch
    val quotes = a.quotes
    val tickerIndex = a.tickerIndex
    val forecast = a.forecast
    val upcoming = a.upcoming
    val clockState = a.clockState
    val usageToday = a.usageToday
    val clock = a.clock
    val calendar = a.calendar
    val page = a.page
    val setPage = a.setPage
    val pagerState = a.pagerState
    val index = a.index
    val grokPlayback = a.grokPlayback
    val playback = a.playback
    val podcastProgress = a.podcastProgress
    val readArticles = a.readArticles
    val readSpeed = a.readSpeed
    val articleControls = a.articleControls
    val actionMenuOpen = a.actionMenuOpen
    val setActionMenuOpen = a.setActionMenuOpen
    val prompt = a.prompt
    val setPrompt = a.setPrompt
    val input = a.input
    val setInput = a.setInput
    val people = a.people
    val setPeople = a.setPeople
    val appQuery = a.appQuery
    val setAppQuery = a.setAppQuery
    val leavingComplete = a.leavingComplete
    val habitState = a.habitState
    val habits = a.habits
    val ctx = a.ctx
    val smsDraft = a.smsDraft
    val setSmsDraft = a.setSmsDraft
    val help = a.help
    val pinPkgs = a.pinPkgs
    val appsEpoch = a.appsEpoch
    val apps = a.apps
    val choices = a.choices
    val setChoices = a.setChoices
    val pick = a.pick
    val executor = a.executor
    val pins = a.pins
    val contactAction = a.contactAction
    val setContactAction = a.setContactAction
    val contactBody = a.contactBody
    val hardware = a.hardware
    val onStrip = a.onStrip
    val mode = a.mode
    val applyMode = a.applyMode
    val runCommand = a.runCommand
    val pickSlash = a.pickSlash
    val taskMode = a.taskMode
    val openClock = a.openClock
    val openWeather = a.openWeather
    val openHub = a.openHub
    val openStocksList = a.openStocksList
    val openUsage = a.openUsage
    val openPodcastsList = a.openPodcastsList
    val openNotesList = a.openNotesList
    val openAppsList = a.openAppsList
    val togglePlayback = a.togglePlayback
    val toggleTodoAnimated = a.toggleTodoAnimated
    val clearBar = a.clearBar
val previewTodos = HomeTodos.preview(HomeTodos.of(local), settings.homeTodoCount, pendingComplete)
val ticker = HomeTicker.line(watch, quotes, tickerIndex)
ClockHeader(
    weather = forecast?.line(settings.weatherUnits),
    weatherKind = forecast?.kind(),
    isDay = forecast?.isDay ?: true,
    ticker = ticker,
    event = upcoming,
    timer = clockState.timer,
    timers = clockState.timers,
    alarms = clockState.alarms,
    analog = settings.clockFace == ClockFace.ANALOG &&
        index == pagerState.currentPage &&
        !HomeStrip.coversPager(page),
    todosToday = HomeTodos.completedToday(HomeTodos.of(local)),
    productiveShare = if (usageToday.granted) usageToday.productiveShare else null,
    onOpenClock = {
        val snap = clock.snapshot()
        val tab = if (!Clock.anyTimerRunning(snap.timers.ifEmpty { listOf(snap.timer) }) && Clock.activeSnooze(snap.alarms, System.currentTimeMillis()) != null) {
            ClockTab.Alarm
        } else {
            null
        }
        openClock(tab)
    },
    onOpenWeather = { openWeather() },
    onOpenHub = { openHub() },
    onOpenTicker = { openStocksList() },
    onOpenTodos = {
        taskMode()
        setPage(Page.Todos)
    },
    onOpenUsage = { openUsage() },
    playing = if (Reads.homeControlsArticle(grokPlayback.playing, grokPlayback.articleId != null, playback.playing)) grokPlayback.playing else playback.playing,
    episodeLoaded = if (Reads.homeControlsArticle(grokPlayback.playing, grokPlayback.articleId != null, playback.playing)) {
        grokPlayback.articleId != null
    } else {
        Podcasts.nowPlayingBarVisible(
            playback.episodeId,
            Podcasts.finished(playback.episodeId?.let { podcastProgress[it] }) ||
                Podcasts.playbackEnded(playback.playing, playback.positionMs, playback.durationMs),
        )
    },
    onOpenPodcasts = { openPodcastsList() },
    onTogglePlayback = {
        if (Reads.homeControlsArticle(grokPlayback.playing, grokPlayback.articleId != null, playback.playing)) {
            val article = readArticles.find { it.id == grokPlayback.articleId } ?: return@ClockHeader
            articleControls.toggle(article, grokPlayback.positionMs, readSpeed)
        } else {
            togglePlayback()
        }
    },
    onOpenEvent = {
        val item = upcoming ?: return@ClockHeader
        try {
            ctx.startActivity(calendar.viewIntent(item))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No calendar app", Toast.LENGTH_SHORT).show()
        }
    },
)
val overlayMenus = actionMenuOpen || prompt == SlashCommands.PROMPT
val filtering = people.isEmpty() && appQuery
if (!overlayMenus) {
if (!filtering) {
Spacer(Modifier.height(8.dp))
HomeLists(
    previewTodos = previewTodos,
    leavingIds = leavingComplete.keys,
    habits = habitState.habits,
    marks = habitState.marks,
    onToggleTodo = { toggleTodoAnimated(it) },
    onOpenTodos = {
        taskMode()
        setPage(Page.Todos)
    },
    onToggleHabit = { id, slot ->
        habits.toggle(id, slot)
        HabitScheduler.reconcile(ctx, habits)
    },
    onOpenHabits = {
        taskMode()
        setPage(Page.Habits)
    },
)
Spacer(Modifier.height(8.dp))
}
smsDraft?.let { draft ->
    Text(
        "Send to ${draft.contact.name} (${draft.contact.number})",
        color = Accent,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(draft.body, color = Paper, style = MaterialTheme.typography.bodyMedium)
    Text("Enter to send. Type anything else to cancel.", color = Dim, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
}
if (help) {
    HelpBlock()
    Spacer(Modifier.height(12.dp))
}
}
val pinned = remember(pinPkgs, appsEpoch) {
    val all = apps.all()
    pinPkgs.mapNotNull { pkg -> all.find { it.packageName == pkg } }
}
val shown = if (filtering) {
    AppList.preview(choices)
} else {
    choices
}
fun pickApp(app: LaunchableApp) {
    if (choices.isNotEmpty() || filtering) {
        executor.applyPick(app, pick)
        clearBar()
        setChoices(emptyList())
        setAppQuery(false)
    } else {
        apps.launch(app)
    }
}
if (!overlayMenus) {
if (filtering) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pick == AppPick.Launch && Notes.matchesQuery(input)) {
            CaretLink(
                Notes.MORE,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openNotesList() }
                    .padding(vertical = 6.dp),
                color = Dim,
                caretColor = Dim,
            )
        }
        if (pick == AppPick.Launch && Stocks.matchesQuery(input)) {
            CaretLink(
                Stocks.MORE,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openStocksList() }
                    .padding(vertical = 6.dp),
                color = Dim,
                caretColor = Dim,
            )
        }
        if (pick == AppPick.Launch && Podcasts.matchesQuery(input)) {
            CaretLink(
                Podcasts.MORE,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openPodcastsList() }
                    .padding(vertical = 6.dp),
                color = Dim,
                caretColor = Dim,
            )
        }
        shown.forEach { app ->
            HomeAppRow(
                app = app,
                icons = settings.appIcons == AppIcons.ICONS,
                drawable = apps.icon(app),
                onClick = { pickApp(app) },
                onLongClick = {
                    if (pins.isPinned(app.packageName)) {
                        pins.unpin(app.packageName)
                    } else {
                        pins.pin(app.packageName)
                    }
                },
            )
        }
        CaretLink(
            AppList.MORE,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openAppsList(true) }
                .padding(vertical = 6.dp),
            color = Dim,
            caretColor = Dim,
        )
    }
} else {
    Column(modifier = Modifier.weight(1f)) {
    val showPins = people.isEmpty() && pinned.isNotEmpty() && shown.isEmpty()
    if (showPins) {
        if (settings.appIcons == AppIcons.ICONS) {
            PinnedAppsRow(
                apps = pinned,
                icon = { apps.icon(it) },
                usage = { if (settings.pinUsage) usageToday.mark(it.packageName) else null },
                onLaunch = { apps.launch(it) },
                onMove = { from, to -> pins.moveVisible(pinned.map { it.packageName }, from, to) },
            )
        } else {
            PinnedAppsTextList(
                apps = pinned,
                usage = { if (settings.pinUsage) usageToday.mark(it.packageName) else null },
                onLaunch = { apps.launch(it) },
                onMove = { from, to -> pins.moveVisible(pinned.map { it.packageName }, from, to) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (people.isNotEmpty()) {
            items(people, key = { it.name + it.number }) { person ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val pending = contactAction
                            if (pending != null) {
                                when (val result = executor.applyContact(person, contactBody, pending)) {
                                    is ExecResult.SmsDraft -> {
                                        setSmsDraft(result)
                                        clearBar()
                                    }
                                    else -> {
                                        clearBar()
                                    }
                                }
                                setPeople(emptyList())
                                setContactAction(null)
                            } else {
                                val glyph = if (prompt == '#') '#' else '@'
                                val rest = if (PrefixCommands.find(prompt) != null) input else input.drop(1)
                                val body = if (glyph == '@') {
                                    rest.trim().split(Regex("\\s+"), limit = 2)
                                        .getOrElse(1) { "" }
                                } else {
                                    ""
                                }
                                setPrompt(glyph)
                                setInput(if (body.isBlank()) {
                                    "${person.name} "
                                } else {
                                    "${person.name} $body"
                                })
                                setPeople(emptyList())
                            }
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(person.name, color = Paper)
                    Text(person.number, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(shown, key = { it.packageName + it.activityName }) { app ->
                HomeAppRow(
                    app = app,
                    icons = settings.appIcons == AppIcons.ICONS,
                    drawable = apps.icon(app),
                    onClick = { pickApp(app) },
                    onLongClick = {
                        if (pins.isPinned(app.packageName)) {
                            pins.unpin(app.packageName)
                        } else {
                            pins.pin(app.packageName)
                        }
                    },
                )
            }
            if (shown.isEmpty() && pinned.isEmpty() && input.isBlank()) {
                item {
                    Text("Type to work. help for commands. Then put it down.", color = Dim)
                }
            }
        }
    }
    }
}
}
Spacer(Modifier.height(8.dp))
CommandBar(
prompt = prompt,
value = input,
hardware = hardware,
grabFocus = onStrip && index == pagerState.currentPage,
onValue = { applyMode(PrefixCommands.type(mode(), it)) },
onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
onSubmit = { runCommand() },
onSlash = { pickSlash(it) },
onHub = { openHub() },
onLeft = { openPodcastsList() },
modifier = if (overlayMenus) Modifier.weight(1f) else Modifier,
actionMenuOpen = actionMenuOpen,
onActionMenuChange = { setActionMenuOpen(it) },
)

}
