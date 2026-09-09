@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package xyz.cdr.builderlauncher.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import xyz.cdr.builderlauncher.usage.Usage
import xyz.cdr.builderlauncher.usage.UsagePeriod
import xyz.cdr.builderlauncher.usage.UsageReader
import xyz.cdr.builderlauncher.usage.UsageStore
import xyz.cdr.builderlauncher.weather.WeatherKind
import xyz.cdr.builderlauncher.weather.WeatherPlace
import xyz.cdr.builderlauncher.weather.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Page { Home, Todos, Notes, NoteEditor, Hub, Settings, AiSettings, Apps, Stocks, StockDetail, StockSettings, Podcasts, PodcastShow, PodcastEpisode, PodcastSettings, Chat, ChatHistory, Clock, Weather, Usage }

private var lastPage: Page = Page.Home

@Composable
fun BuilderRoot(
    settingsRepo: SettingsRepository,
    apps: InstalledApps,
    lists: LocalLists,
    chats: ChatStore,
    pins: PinnedApps,
    contacts: PhoneContacts,
    llm: LlmClient,
    oauth: OAuthService,
    executor: CommandExecutor,
    weather: WeatherRepository,
    stocks: StocksRepository,
    podcasts: PodcastsRepository,
    calendar: CalendarRepository,
    clock: ClockStore,
    backup: BackupService,
    homePresses: StateFlow<Int> = MutableStateFlow(0),
    onRequestHome: () -> Unit = {},
) {
    val settings by settingsRepo.settings.collectAsState()
    val hub by HubStore.items.collectAsState()
    val local by lists.items.collectAsState()
    val chatThreads by chats.threads.collectAsState()
    val forecast by weather.current.collectAsState()
    val weatherForecast by weather.forecast.collectAsState()
    val clockState by clock.state.collectAsState()
    val watch by stocks.watch.collectAsState()
    val quotes by stocks.quotes.collectAsState()
    val podcastShows by podcasts.shows.collectAsState()
    val podcastEpisodes by podcasts.episodes.collectAsState()
    val podcastProgress by podcasts.progress.collectAsState()
    val podcastDownloads by podcasts.downloads.collectAsState()
    val podcastTransfer by podcasts.downloadProgress.collectAsState()
    val podcastCache by podcasts.cacheBytes.collectAsState()
    val podcastSpeed by podcasts.playbackSpeed.collectAsState()
    val playback by PodcastPlayer.state.collectAsState()
    val upcoming by calendar.current.collectAsState()
    val homePressCount by homePresses.collectAsState()
    var page by remember { mutableStateOf(lastPage) }
    var prompt by remember { mutableStateOf(PrefixCommands.DEFAULT_PROMPT) }
    var input by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    var chatId by remember { mutableStateOf<String?>(null) }
    var chatBusy by remember { mutableStateOf(false) }
    var streamDraft by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appQuery by remember { mutableStateOf(false) }
    var appsEpoch by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var contactAction by remember { mutableStateOf<ContactAction?>(null) }
    var contactBody by remember { mutableStateOf("") }
    var smsDraft by remember { mutableStateOf<ExecResult.SmsDraft?>(null) }
    var pick by remember { mutableStateOf(AppPick.Launch) }
    var noteId by remember { mutableStateOf<String?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var noteFromList by remember { mutableStateOf(false) }
    var replyKey by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var stockSymbol by remember { mutableStateOf<String?>(null) }
    var stockRange by remember { mutableStateOf(StockRange.default) }
    var stockHits by remember { mutableStateOf<List<StockHit>>(emptyList()) }
    var stockBusy by remember { mutableStateOf(false) }
    var stockChart by remember { mutableStateOf<StockChartData?>(null) }
    var stockDetails by remember { mutableStateOf<StockDetails?>(null) }
    var stockScrub by remember { mutableStateOf<Int?>(null) }
    var podcastHits by remember { mutableStateOf<List<PodcastHit>>(emptyList()) }
    var podcastBusy by remember { mutableStateOf(false) }
    var podcastShowUrl by remember { mutableStateOf<String?>(null) }
    var podcastEpisodeId by remember { mutableStateOf<String?>(null) }
    var podcastDownloadBusy by remember { mutableStateOf(false) }
    var clockTab by remember { mutableStateOf(ClockTab.Timer) }
    var zoneHits by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var tickerIndex by remember { mutableIntStateOf(0) }
    var usagePeriod by remember { mutableStateOf(UsagePeriod.W1) }
    var usageSnapshot by remember { mutableStateOf(Usage.build(emptyList(), emptyMap(), UsagePeriod.W1, false)) }
    var editingTodoId by remember { mutableStateOf<String?>(null) }
    var wipeBarOnHome by remember { mutableStateOf(false) }
    val pinPkgs by pins.packages.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val usageStore = remember { UsageStore(ctx) }
    val usageReader = remember { UsageReader(ctx) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appsEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(calendar) {
        val stop = calendar.observe { scope.launch { calendar.refresh() } }
        onDispose { stop() }
    }
    LaunchedEffect(appsEpoch) {
        calendar.refresh()
    }
    DisposableEffect(page) {
        if (page != Page.Settings) ClockSoundPlayer.stopPreview()
        onDispose { ClockSoundPlayer.stopPreview() }
    }
    LaunchedEffect(page) {
        lastPage = page
        if (page == Page.Home && wipeBarOnHome) {
            wipeBarOnHome = false
            prompt = PrefixCommands.DEFAULT_PROMPT
            input = ""
        }
        if (page != Page.Todos) editingTodoId = null
    }
    LaunchedEffect(page, usagePeriod, appsEpoch) {
        if (page == Page.Usage) {
            usageSnapshot = usageReader.load(usageStore, usagePeriod)
        }
    }

    BackHandler {
        when (
            BackPress.result(
                onHome = page == Page.Home,
                overlayOpen = help ||
                    choices.isNotEmpty() ||
                    people.isNotEmpty() ||
                    smsDraft != null,
                promptActive = PrefixCommands.isModePrompt(prompt) || input.isNotBlank(),
            )
        ) {
            BackResult.Stay -> Unit
            BackResult.ResetPrompt -> {
                prompt = PrefixCommands.DEFAULT_PROMPT
                input = ""
            }
            BackResult.DismissUi -> {
                help = false
                choices = emptyList()
                people = emptyList()
                contactAction = null
                smsDraft = null
                appQuery = false
            }
            BackResult.OpenHome -> {
                if (page == Page.Todos) {
                    editingTodoId = null
                    wipeBarOnHome = false
                    prompt = PrefixCommands.DEFAULT_PROMPT
                    input = ""
                    page = HomeStrip.homeAfterOverlay()
                } else {
                    page = Page.Home
                    if (wipeBarOnHome || prompt == SlashCommands.PROMPT) {
                        wipeBarOnHome = false
                        prompt = PrefixCommands.DEFAULT_PROMPT
                        input = ""
                    }
                }
            }
        }
    }
    val hardware = remember(settings.keyboardMode, ctx.resources.configuration.keyboard) {
        KeyboardPresence.usesHardwareKeys(
            settings.keyboardMode,
            ctx.resources.configuration.keyboard,
        )
    }
    val window = (ctx as? Activity)?.window
    SideEffect {
        window?.setSoftInputMode(KeyboardPresence.softInputMode(hardware))
    }
    LaunchedEffect(Unit) {
        PodcastPlayer.setSpeed(podcasts.playbackSpeed.value)
    }
    LaunchedEffect(settings.weatherLat, settings.weatherLon) {
        weather.refresh()
    }
    LaunchedEffect(lifecycleOwner, settings.backupFrequency, settings.s3Endpoint, settings.s3Bucket) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) { backup.maybeUpload() }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15 * 60 * 1000)
            weather.refresh()
        }
    }
    LaunchedEffect(page, watch.size) {
        val needQuotes = (page == Page.Home && watch.isNotEmpty()) ||
            page == Page.Stocks || page == Page.StockDetail
        if (!needQuotes) return@LaunchedEffect
        stocks.refreshQuotes()
        while (true) {
            kotlinx.coroutines.delay(60_000)
            stocks.refreshQuotes()
        }
    }
    LaunchedEffect(watch.size) {
        if (watch.isEmpty()) {
            tickerIndex = 0
            return@LaunchedEffect
        }
        tickerIndex = tickerIndex.mod(watch.size)
        while (true) {
            kotlinx.coroutines.delay(HomeTicker.ROTATE_MS)
            tickerIndex = HomeTicker.nextIndex(watch.size, tickerIndex)
        }
    }
    LaunchedEffect(page, input) {
        if (page != Page.Stocks) {
            stockHits = emptyList()
            return@LaunchedEffect
        }
        val q = Stocks.queryFromInput(input)
        if (q.isEmpty()) {
            stockHits = emptyList()
            stockBusy = false
            return@LaunchedEffect
        }
        stockBusy = true
        kotlinx.coroutines.delay(280)
        stockHits = stocks.search(q)
        stockBusy = false
    }
    LaunchedEffect(page, stockSymbol, stockRange) {
        stockScrub = null
        val symbol = stockSymbol
        if (page != Page.StockDetail || symbol.isNullOrBlank()) {
            stockChart = null
            return@LaunchedEffect
        }
        stockChart = stocks.chart(symbol, stockRange)
    }
    LaunchedEffect(page, stockSymbol) {
        val symbol = stockSymbol
        if (page != Page.StockDetail || symbol.isNullOrBlank()) return@LaunchedEffect
        if (!stockDetails?.quote?.symbol.equals(symbol, ignoreCase = true)) {
            stockDetails = null
        }
        stockDetails = stocks.details(symbol)
    }
    LaunchedEffect(page, input) {
        if (page != Page.Podcasts) {
            if (page != Page.PodcastShow && page != Page.PodcastEpisode && page != Page.PodcastSettings) {
                podcastHits = emptyList()
            }
            return@LaunchedEffect
        }
        val q = input.trim()
        if (q.isEmpty() || Podcasts.looksLikeFeedUrl(q) || PodcastOpml.looksLike(q)) {
            podcastHits = emptyList()
            podcastBusy = false
            return@LaunchedEffect
        }
        podcastBusy = true
        kotlinx.coroutines.delay(280)
        podcastHits = podcasts.search(q)
        podcastBusy = false
    }
    LaunchedEffect(page) {
        if (page == Page.Podcasts || page == Page.PodcastShow) {
            podcasts.refreshAll()
        }
    }
    LaunchedEffect(playback.playing, playback.episodeId) {
        if (!playback.playing) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(500)
            PodcastPlayer.poll()
        }
    }
    DisposableEffect(Unit) {
        PodcastPlayer.onProgress = { id, pos, dur, done ->
            podcasts.saveProgress(id, pos, dur, done)
        }
        onDispose {
            PodcastPlayer.persist()
            PodcastPlayer.onProgress = null
        }
    }

    fun openNoteEditor(id: String?, draft: String, fromList: Boolean) {
        noteId = id
        noteDraft = if (id == null) Notes.headingDraft(draft) else draft
        noteFromList = fromList
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        appQuery = false
        page = Page.NoteEditor
    }

    fun sendAsk(question: String) {
        val q = question.trim()
        if (q.isEmpty() || chatBusy) return
        val user = ChatMessage(role = "user", content = q)
        val thread = chats.addMessage(chatId, user)
        chatId = thread.id
        prompt = '?'
        input = ""
        chatBusy = true
        streamDraft = ""
        val snapshot = chats.get(thread.id) ?: thread
        scope.launch {
            try {
                val reply = llm.ask(snapshot.messages) { streamed ->
                    streamDraft = streamed
                }
                if (chats.get(thread.id) != null) {
                    reply.notice?.let { notice ->
                        chats.addMessage(thread.id, ChatMessage(role = "notice", content = notice))
                    }
                    chats.addMessage(
                        thread.id,
                        ChatMessage(role = "assistant", content = reply.text.ifBlank { "Empty reply from the model." }),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (chats.get(thread.id) != null) {
                    chats.addMessage(
                        thread.id,
                        ChatMessage(role = "assistant", content = "Could not reach the model."),
                    )
                }
            } finally {
                streamDraft = ""
                chatBusy = false
            }
        }
    }

    fun openAsk(question: String, id: String? = null) {
        chatId = id
        chatBusy = false
        streamDraft = ""
        prompt = '?'
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.Chat
        if (id == null && question.isNotBlank()) {
            sendAsk(question)
        }
    }

    fun openNotesList() {
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        appQuery = false
        page = Page.Notes
    }

    fun openAppsList(keepQuery: Boolean) {
        if (!keepQuery) {
            prompt = PrefixCommands.DEFAULT_PROMPT
            input = ""
        }
        choices = emptyList()
        people = emptyList()
        appQuery = false
        page = Page.Apps
    }

    fun openStocksList(draft: String = "") {
        prompt = '$'
        input = Stocks.queryFromInput(draft)
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.Stocks
    }

    fun openPodcastsList() {
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        podcastHits = emptyList()
        page = Page.Podcasts
    }

    fun openPodcastShow(feedUrl: String) {
        podcastShowUrl = feedUrl
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.PodcastShow
        scope.launch { podcasts.refreshShow(feedUrl) }
    }

    fun openPodcastEpisode(id: String) {
        podcastEpisodeId = id
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.PodcastEpisode
    }

    fun playEpisode(episode: PodcastEpisode) {
        podcasts.setSkipped(episode.id, false, episode.durationMs)
        val start = podcastProgress[episode.id]?.takeIf { !Podcasts.finished(it) }?.positionMs ?: 0L
        val file = podcasts.downloadedFile(episode.id)
        PodcastPlayer.setSpeed(podcasts.playbackSpeed.value)
        PodcastPlayer.play(episode, file, start)
        val show = podcasts.show(episode.showId)
        PodcastPlaybackService.start(ctx, show?.title ?: "Podcast", episode.title, show?.artworkUrl.orEmpty())
    }

    fun togglePlayback() {
        val ep = playback.episodeId?.let { id -> podcastEpisodes.find { it.id == id } } ?: return
        if (playback.playing) {
            PodcastPlayer.pause()
            PodcastPlaybackService.pause(ctx)
        } else {
            playEpisode(ep)
        }
    }

    fun skipEpisode(episode: PodcastEpisode) {
        if (playback.episodeId == episode.id) {
            PodcastPlayer.stop()
            PodcastPlaybackService.stop(ctx)
        }
        podcasts.setSkipped(episode.id, true, episode.durationMs)
    }

    fun applyPodcastSpeed(speed: Float) {
        podcasts.setPlaybackSpeed(speed)
        PodcastPlayer.setSpeed(speed)
    }

    fun unsubscribeShow(feedUrl: String) {
        val current = playback.episodeId?.let { id -> podcastEpisodes.find { it.id == id } }
        if (current != null && current.showId.equals(feedUrl, ignoreCase = true)) {
            PodcastPlayer.stop()
            PodcastPlaybackService.stop(ctx)
        }
        podcasts.unsubscribe(feedUrl)
        if (podcastShowUrl.equals(feedUrl, ignoreCase = true)) {
            podcastShowUrl = null
            if (page == Page.PodcastShow) page = Page.Podcasts
        }
    }

    fun seekEpisode(episode: PodcastEpisode, positionMs: Long) {
        val dur = if (playback.episodeId == episode.id && playback.durationMs > 0) {
            playback.durationMs
        } else {
            podcastProgress[episode.id]?.durationMs?.takeIf { it > 0 }
                ?: episode.durationMs
        }
        val next = positionMs.coerceIn(0L, dur.coerceAtLeast(0L))
        if (playback.episodeId == episode.id) PodcastPlayer.seek(next)
        else podcasts.saveProgress(episode.id, next, dur)
    }

    fun importOpml(raw: String) {
        val hits = PodcastOpml.parse(raw)
        if (hits.isEmpty()) {
            Toast.makeText(ctx, "No shows in OPML", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val count = podcasts.importHits(hits)
            Toast.makeText(
                ctx,
                if (count == 0) "Already subscribed" else "Added $count",
                Toast.LENGTH_SHORT,
            ).show()
        }
        input = ""
        podcastHits = emptyList()
    }

    fun subscribeHit(hit: PodcastHit) {
        scope.launch {
            val show = podcasts.subscribe(hit.feedUrl, hit.title, hit.author, hit.artworkUrl)
            if (show == null) {
                Toast.makeText(ctx, "Could not subscribe", Toast.LENGTH_SHORT).show()
            } else {
                openPodcastShow(hit.feedUrl)
            }
            podcastHits = emptyList()
        }
        input = ""
    }

    fun openClock() {
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        zoneHits = emptyList()
        page = Page.Clock
        ClockScheduler.sync(ctx, clock.snapshot())
    }

    fun openWeather() {
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.Weather
        scope.launch { weather.refresh() }
    }

    fun openUsage() {
        prompt = PrefixCommands.DEFAULT_PROMPT
        input = ""
        choices = emptyList()
        people = emptyList()
        help = false
        appQuery = false
        page = Page.Usage
    }

    fun openStockDetail(symbol: String) {
        stockSymbol = symbol
        stockRange = StockRange.default
        stockChart = null
        page = Page.StockDetail
    }

    fun addTicker(query: String) {
        scope.launch {
            val item = stocks.add(query, settings.stockInsert)
            if (item == null) {
                Toast.makeText(ctx, "No ticker matches", Toast.LENGTH_SHORT).show()
            }
            stockHits = emptyList()
        }
        prompt = '$'
        input = ""
    }

    fun clipboardText(): String {
        val clip = ctx.getSystemService(ClipboardManager::class.java)
        val data = clip?.primaryClip ?: return ""
        if (data.itemCount < 1) return ""
        return data.getItemAt(0).coerceToText(ctx).toString()
    }

    fun importTickers(raw: String, replace: Boolean = false) {
        val text = Stocks.queryFromInput(raw).ifBlank { raw }
        val hits = StocksCsv.parse(text)
        if (hits.isEmpty()) {
            Toast.makeText(ctx, "No tickers in clipboard", Toast.LENGTH_SHORT).show()
            return
        }
        val count = if (replace) stocks.replaceHits(hits) else stocks.importHits(hits, settings.stockInsert)
        Toast.makeText(
            ctx,
            when {
                replace -> "Loaded $count"
                count == 0 -> "Already on the list"
                else -> "Added $count"
            },
            Toast.LENGTH_SHORT,
        ).show()
        stockHits = emptyList()
        prompt = '$'
        input = ""
        if (count > 0) {
            scope.launch { stocks.refreshQuotes() }
        }
    }

    fun saveAndCloseNote() {
        val text = noteDraft.trim()
        if (text.isNotEmpty()) {
            val id = noteId
            if (id == null) lists.add("note", text) else lists.update(id, text)
        }
        noteId = null
        noteDraft = ""
        page = if (noteFromList) Page.Notes else Page.Home
        noteFromList = false
    }

    fun copyText(label: String, value: String) {
        val clip = ctx.getSystemService(ClipboardManager::class.java)
        clip?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun handoffToProvider() {
        val lastUser = chatId
            ?.let { id -> chatThreads.find { it.id == id } }
            ?.messages
            ?.lastOrNull { it.fromUser }
            ?.content
        val text = ProviderHandoff.prompt(input, lastUser)
        if (text.isNotBlank()) {
            ctx.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("prompt", text))
        }
        val openBase = if (settings.provider == LlmProvider.HERMES) {
            HermesUrls.openInBrowser(settings)?.trimEnd('/')
        } else {
            settings.hermesBaseUrl
        }
        val opened = ProviderHandoff.open(
            ctx,
            settings.provider,
            text,
            openBase,
            openHermex = settings.provider == LlmProvider.HERMES && settings.hermesOpenInHermex,
        )
        if (!opened) {
            val name = ProviderHandoff.label(settings.provider)
            Toast.makeText(
                ctx,
                if (text.isNotBlank()) "Copied — could not open $name" else "Could not open $name",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openHub() {
        replyKey = null
        replyText = ""
        page = Page.Hub
    }

    fun sendHubReply(key: String) {
        val text = replyText.trim()
        if (text.isEmpty()) return
        HubStore.reply(key, text)
        replyKey = null
        replyText = ""
    }

    fun mode() = PrefixCommands.Mode(prompt, input)

    fun applyMode(next: PrefixCommands.Mode) {
        prompt = next.prompt
        val line = next.line
        if (line.startsWith(Chats.PREFIX) && page != Page.Chat && page != Page.ChatHistory && page != Page.NoteEditor && page != Page.Apps) {
            openAsk(Chats.questionFromInput(line))
            return
        }
        if (line.startsWith(Notes.PREFIX) && page != Page.NoteEditor && page != Page.Apps && page != Page.Chat && page != Page.ChatHistory) {
            openNoteEditor(id = null, draft = Notes.draftFromInput(line), fromList = false)
            return
        }
        if (line.startsWith(Stocks.PREFIX) && page == Page.Home) {
            openStocksList(line)
            return
        }
        input = next.input
        contactAction = null
        if (next.cancelsDraft) {
            smsDraft = null
        }
        if (page == Page.Apps) {
            choices = emptyList()
            people = emptyList()
            appQuery = false
            return
        }
        if (page == Page.Clock || page == Page.Weather || page == Page.Usage ||
            page == Page.Podcasts || page == Page.PodcastShow || page == Page.PodcastEpisode || page == Page.PodcastSettings
        ) {
            choices = emptyList()
            people = emptyList()
            appQuery = false
            if (page == Page.Clock && clockTab == ClockTab.Zones) {
                val q = line.trim()
                if (q.length >= 2 && (line.isEmpty() || !PrefixCommands.isModePrompt(line.first()))) {
                    scope.launch { zoneHits = weather.suggest(q) }
                } else {
                    zoneHits = emptyList()
                }
            } else {
                zoneHits = emptyList()
            }
            return
        }
        if (line.isBlank()) {
            choices = emptyList()
            people = emptyList()
            appQuery = false
            return
        }
        val first = line.first()
        if (first == '@' || first == '#') {
            // First token only — body after the name is the message, not a search.
            val needle = line.drop(1).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            people = if (needle.isEmpty()) emptyList() else contacts.search(needle)
            choices = emptyList()
            appQuery = false
        } else if (PrefixCommands.isModePrompt(first)) {
            people = emptyList()
            choices = emptyList()
            appQuery = false
        } else {
            people = emptyList()
            choices = apps.search(line)
            appQuery = true
        }
    }

    fun clearBar() {
        applyMode(PrefixCommands.Mode())
    }

    fun taskMode() {
        applyMode(PrefixCommands.pick(PrefixCommands.Mode(), '-'))
    }

    fun openHomeDefault() {
        editingTodoId = null
        wipeBarOnHome = false
        clearBar()
        page = HomeStrip.homeAfterOverlay()
    }

    fun addWorldClock(place: WeatherPlace) {
        val zone = place.timezone
        if (zone.isNullOrBlank()) {
            Toast.makeText(ctx, "No timezone for ${place.name}", Toast.LENGTH_SHORT).show()
            return
        }
        clock.addZone(place.name, zone)
        zoneHits = emptyList()
        clearBar()
    }

    fun handleClockInput(line: String): Boolean {
        val parsed = CommandParser.parse(line)
        if (parsed !is Command.LaunchApp && parsed !is Command.Empty) return false
        val text = line.trim()
        if (text.isEmpty()) {
            clearBar()
            return true
        }
        when (clockTab) {
            ClockTab.Timer -> {
                val ms = Clock.parseTimer(text) ?: return false
                clock.setTimer(Clock.setDuration(clock.snapshot().timer, ms))
                ClockScheduler.sync(ctx, clock.snapshot())
                clearBar()
                return true
            }
            ClockTab.Alarm -> {
                val hm = Clock.parseAlarm(text) ?: return false
                clock.addAlarm(hm.first, hm.second)
                ClockScheduler.sync(ctx, clock.snapshot())
                clearBar()
                return true
            }
            ClockTab.Zones -> {
                val hit = zoneHits.singleOrNull()
                    ?: zoneHits.find { it.label.equals(text, true) || it.name.equals(text, true) }
                if (hit != null) {
                    addWorldClock(hit)
                    return true
                }
                scope.launch {
                    val hits = weather.suggest(text)
                    zoneHits = hits
                    val only = hits.singleOrNull()
                    if (only != null) addWorldClock(only)
                }
                return true
            }
        }
    }

    fun runCommand(line: String = mode().line) {
        if (page == Page.Todos) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == HomeTodos.TASK_PREFIX) {
                taskMode()
                return
            }
            val text = if (trimmed.startsWith(HomeTodos.TASK_PREFIX)) trimmed.drop(1).trim() else trimmed
            val editId = editingTodoId
            if (editId != null) {
                lists.update(editId, text)
                editingTodoId = null
                taskMode()
                return
            }
        }
        if (page == Page.Stocks) {
            val q = Stocks.queryFromInput(line)
            if (q.isEmpty()) {
                prompt = '$'
                input = ""
                return
            }
            if (StocksCsv.looksLikeList(line) || StocksCsv.looksLikeList(q)) {
                importTickers(line)
                return
            }
            addTicker(q)
            return
        }
        if (page == Page.Podcasts) {
            val q = line.trim()
            if (q.isEmpty()) {
                input = ""
                return
            }
            if (PodcastOpml.looksLike(q) || PodcastOpml.looksLike(line)) {
                importOpml(line)
                return
            }
            if (Podcasts.looksLikeFeedUrl(q)) {
                scope.launch {
                    val show = podcasts.subscribe(q)
                    if (show == null) Toast.makeText(ctx, "Could not subscribe", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(ctx, "Subscribed", Toast.LENGTH_SHORT).show()
                }
                input = ""
                podcastHits = emptyList()
                return
            }
            val hit = podcastHits.singleOrNull()
                ?: podcastHits.find { it.title.equals(q, true) }
            if (hit != null) {
                subscribeHit(hit)
                return
            }
            scope.launch {
                podcastBusy = true
                podcastHits = podcasts.search(q)
                podcastBusy = false
                val only = podcastHits.singleOrNull()
                if (only != null) subscribeHit(only)
            }
            return
        }
        if (page == Page.Chat) {
            val q = Chats.questionFromInput(line)
            if (q.isEmpty()) {
                prompt = '?'
                input = ""
                return
            }
            sendAsk(q)
            return
        }
        if (page == Page.Clock && handleClockInput(line)) {
            return
        }
        val draft = smsDraft
        if (draft != null) {
            if (line.isBlank() || line.equals("send", ignoreCase = true)) {
                executor.sendSms(draft.contact, draft.body)
            }
            smsDraft = null
            clearBar()
            people = emptyList()
            return
        }
        if (prompt == PrefixCommands.DEFAULT_PROMPT) {
            Calculator.commit(line)?.let { result ->
                applyMode(PrefixCommands.Mode(input = result))
                return
            }
        }
        val result = executor.execute(CommandParser.parse(line))
        when (result) {
            ExecResult.None -> {
                clearBar()
                choices = emptyList()
                people = emptyList()
                contactAction = null
                smsDraft = null
                help = false
                appQuery = false
            }
            ExecResult.ShowHelp -> {
                help = true
            }
            ExecResult.NavigateSettings -> page = Page.Settings
            ExecResult.NavigateHub -> openHub()
            ExecResult.NavigateNotes -> openNotesList()
            ExecResult.NavigateApps -> openAppsList(keepQuery = false)
            ExecResult.NavigateStocks -> openStocksList()
            ExecResult.NavigatePodcasts -> openPodcastsList()
            ExecResult.NavigateClock -> openClock()
            ExecResult.NavigateWeather -> openWeather()
            ExecResult.NavigateUsage -> openUsage()
            is ExecResult.AddStock -> {
                openStocksList()
                addTicker(result.query)
            }
            is ExecResult.Ask -> openAsk(result.question)
            is ExecResult.AppChoices -> {
                choices = result.apps
                pick = result.pick
                people = emptyList()
                help = false
                appQuery = result.pick == AppPick.Launch
            }
            is ExecResult.ContactChoices -> {
                people = result.contacts
                contactBody = result.body
                contactAction = result.action
                choices = emptyList()
                help = false
            }
            is ExecResult.SmsDraft -> {
                smsDraft = result
                clearBar()
                people = emptyList()
                contactAction = null
                help = false
            }
        }
        if (page == Page.Todos && input.isBlank()) {
            taskMode()
        }
    }

    fun pickSlash(cmd: SlashCommand) {
        wipeBarOnHome = true
        applyMode(PrefixCommands.Mode(SlashCommands.PROMPT, cmd.name))
        runCommand()
        if (page == Page.Home) {
            wipeBarOnHome = false
            clearBar()
        }
    }

    fun clearClockAlert() {
        clock.setAlert(null)
        ClockAlertService.stop(ctx)
    }

    val onStrip = HomeStrip.contains(page)
    val pagerState = rememberPagerState(
        initialPage = HomeStrip.indexOf(page) ?: HomeStrip.HOME,
        pageCount = { HomeStrip.COUNT },
    )
    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) {
            openHomeDefault()
            pagerState.scrollToPage(HomeStrip.HOME)
        }
    }
    LaunchedEffect(page) {
        val target = HomeStrip.indexOf(page) ?: return@LaunchedEffect
        if (pagerState.settledPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage, pagerState.currentPage, pagerState.isScrollInProgress, page) {
        val next = HomeStrip.followSettled(
            page = page,
            settledIndex = pagerState.settledPage,
            currentIndex = pagerState.currentPage,
            scrolling = pagerState.isScrollInProgress,
        ) ?: return@LaunchedEffect
        when (next) {
            Page.Podcasts -> openPodcastsList()
            Page.Hub -> openHub()
            Page.Home -> page = Page.Home
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                userScrollEnabled = onStrip,
                beyondViewportPageCount = 1,
            ) { index ->
                Column(Modifier.fillMaxSize().clipToBounds().background(Ink)) {
                    when (HomeStrip.pageAt(index)) {
                        Page.Home -> {
                val previewTodos = HomeTodos.preview(HomeTodos.of(local))
                val ticker = HomeTicker.line(watch, quotes, tickerIndex)
                ClockHeader(
                    weather = forecast?.line(settings.weatherUnits),
                    weatherKind = forecast?.kind(),
                    isDay = forecast?.isDay ?: true,
                    ticker = ticker,
                    event = upcoming,
                    timer = clockState.timer,
                    analog = settings.clockFace == ClockFace.ANALOG &&
                        index == pagerState.currentPage &&
                        !HomeStrip.coversPager(page),
                    onOpenClock = { openClock() },
                    onOpenWeather = { openWeather() },
                    onOpenHub = { openHub() },
                    onOpenTicker = { openStocksList() },
                    playing = playback.playing,
                    episodeLoaded = Podcasts.nowPlayingBarVisible(
                        playback.episodeId,
                        Podcasts.finished(playback.episodeId?.let { podcastProgress[it] }) ||
                            Podcasts.playbackEnded(playback.playing, playback.positionMs, playback.durationMs),
                    ),
                    onOpenPodcasts = { openPodcastsList() },
                    onTogglePlayback = { togglePlayback() },
                    onOpenEvent = {
                        val item = upcoming ?: return@ClockHeader
                        try {
                            ctx.startActivity(calendar.viewIntent(item))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(ctx, "No calendar app", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                TodoPreview(
                    open = previewTodos,
                    onToggle = { lists.toggleComplete(it) },
                    onMore = {
                        taskMode()
                        page = Page.Todos
                    },
                )
                Spacer(Modifier.height(8.dp))
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
                val pinned = remember(pinPkgs, appsEpoch) {
                    val all = apps.all()
                    pinPkgs.mapNotNull { pkg -> all.find { it.packageName == pkg } }
                }
                val filtering = people.isEmpty() && appQuery
                val shown = if (filtering) {
                    AppList.preview(choices)
                } else {
                    choices
                }
                fun pickApp(app: LaunchableApp) {
                    if (choices.isNotEmpty() || filtering) {
                        executor.applyPick(app, pick)
                        clearBar()
                        choices = emptyList()
                        appQuery = false
                    } else {
                        apps.launch(app)
                    }
                }
                if (filtering) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (Notes.matchesQuery(input)) {
                            CaretLink(
                                Notes.MORE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openNotesList() }
                                    .padding(vertical = 6.dp),
                            )
                        }
                        if (Stocks.matchesQuery(input)) {
                            CaretLink(
                                Stocks.MORE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openStocksList() }
                                    .padding(vertical = 6.dp),
                            )
                        }
                        if (Podcasts.matchesQuery(input)) {
                            CaretLink(
                                Podcasts.MORE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openPodcastsList() }
                                    .padding(vertical = 6.dp),
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
                                .clickable { openAppsList(keepQuery = true) }
                                .padding(vertical = 6.dp),
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
                                onLaunch = { apps.launch(it) },
                                onMove = { from, to -> pins.moveVisible(pinned.map { it.packageName }, from, to) },
                            )
                        } else {
                            PinnedAppsTextList(
                                apps = pinned,
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
                                                        smsDraft = result
                                                        clearBar()
                                                    }
                                                    else -> {
                                                        clearBar()
                                                    }
                                                }
                                                people = emptyList()
                                                contactAction = null
                                            } else {
                                                val glyph = if (prompt == '#') '#' else '@'
                                                val rest = if (PrefixCommands.find(prompt) != null) input else input.drop(1)
                                                val body = if (glyph == '@') {
                                                    rest.trim().split(Regex("\\s+"), limit = 2)
                                                        .getOrElse(1) { "" }
                                                } else {
                                                    ""
                                                }
                                                prompt = glyph
                                                input = if (body.isBlank()) {
                                                    "${person.name} "
                                                } else {
                                                    "${person.name} $body"
                                                }
                                                people = emptyList()
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
                )
                        }
                        Page.Hub -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        HubMessages.BACK,
                        color = Accent,
                        modifier = Modifier
                            .semantics { contentDescription = "back" }
                            .clickable { page = Page.Home }
                            .padding(vertical = 6.dp),
                    )
                    Text("hub", color = Accent)
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(hub, key = { it.key }) { item ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .semantics { contentDescription = "open message" }
                                        .clickable { HubStore.open(item.key) }
                                        .padding(vertical = 6.dp),
                                ) {
                                    Text(item.source, color = Dim, style = MaterialTheme.typography.labelSmall)
                                    Text(item.title, color = Paper)
                                    if (item.body.isNotBlank()) {
                                        Text(
                                            item.body,
                                            color = Dim,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                ReplyIcon(
                                    Modifier
                                        .semantics { contentDescription = "reply" }
                                        .clickable {
                                            if (item.canInlineReply) {
                                                replyKey = item.key
                                                replyText = ""
                                            } else {
                                                HubStore.open(item.key)
                                            }
                                        }
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                                DeleteIcon(
                                    Modifier
                                        .semantics { contentDescription = "dismiss" }
                                        .clickable {
                                            replyKey = null
                                            replyText = ""
                                            HubStore.dismiss(item.key)
                                        }
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                            if (replyKey == item.key && item.canInlineReply) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        if (replyText.isEmpty()) {
                                            Text(
                                                "reply",
                                                color = Dim,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        BasicTextField(
                                            value = replyText,
                                            onValueChange = { replyText = it },
                                            singleLine = true,
                                            cursorBrush = SolidColor(Accent),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.Sentences,
                                                imeAction = ImeAction.Send,
                                            ),
                                            keyboardActions = KeyboardActions(onSend = { sendHubReply(item.key) }),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    SendIcon(
                                        Modifier
                                            .semantics { contentDescription = "send" }
                                            .clickable { sendHubReply(item.key) }
                                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                    )
                                }
                                HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    if (hub.isEmpty()) {
                        item {
                            Text(
                                "Grant notification access in settings to fill the hub with messages you can reply to.",
                                color = Dim,
                            )
                        }
                    }
                }
                        }
                        Page.Podcasts -> {
                val searching = input.trim().isNotEmpty()
                val rows = remember(podcastShows, podcastEpisodes, podcastProgress, playback.episodeId) {
                    Podcasts.homeRows(podcastShows, podcastEpisodes, podcastProgress, playback.episodeId)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GearIcon(
                        Modifier
                            .semantics { contentDescription = "podcasts settings" }
                            .clickable { page = Page.PodcastSettings }
                            .padding(vertical = 6.dp),
                    )
                    Text(
                        Podcasts.HOME,
                        color = Accent,
                        modifier = Modifier
                            .semantics { contentDescription = "back" }
                            .clickable {
                                clearBar()
                                page = Page.Home
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val nowEpisode = playback.episodeId?.let { id -> podcastEpisodes.find { it.id == id } }
                val nowEnded = nowEpisode != null && (
                    Podcasts.finished(podcastProgress[nowEpisode.id]) ||
                        Podcasts.playbackEnded(playback.playing, playback.positionMs, playback.durationMs)
                    )
                if (Podcasts.nowPlayingBarVisible(playback.episodeId, nowEnded) && nowEpisode != null) {
                    PodcastNowPlayingBar(
                        title = nowEpisode.title,
                        show = podcasts.show(nowEpisode.showId)?.title.orEmpty(),
                        playing = playback.playing,
                        onOpen = { openPodcastEpisode(nowEpisode.id) },
                        onToggle = { togglePlayback() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searching) {
                        if (podcastHits.isEmpty()) {
                            item {
                                Text(if (podcastBusy) "Searching shows…" else "No show matches", color = Dim)
                            }
                        }
                        items(podcastHits, key = { "h" + it.feedUrl }) { hit ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { subscribeHit(hit) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PodcastSearchArt(hit.artworkUrl)
                                Column(Modifier.weight(1f)) {
                                    Text(hit.title, color = Paper)
                                    Text(
                                        hit.author.ifBlank { hit.feedUrl },
                                        color = Dim,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    } else {
                        if (rows.isEmpty()) {
                            item {
                                Text("Type a show name, RSS URL, or paste Overcast OPML.", color = Dim)
                            }
                        }
                        itemsIndexed(rows) { _, row ->
                            when (row) {
                                is PodcastHomeRow.Header -> {
                                    PodcastSectionHeader(row.title)
                                }
                                is PodcastHomeRow.Continue -> {
                                    val ep = row.episode
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clickable { playEpisode(ep) },
                                        ) {
                                            Text(
                                                ep.title,
                                                color = Accent,
                                                maxLines = Podcasts.titleMaxLines(home = true),
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                row.show.title,
                                                color = Dim,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        Text(
                                            Podcasts.formatPosition(row.progress.positionMs, row.progress.durationMs.takeIf { it > 0 } ?: ep.durationMs),
                                            color = Dim,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        DeleteIcon(
                                            Modifier
                                                .semantics { contentDescription = "dismiss episode" }
                                                .clickable { skipEpisode(ep) }
                                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                        )
                                    }
                                }
                                is PodcastHomeRow.Fresh -> {
                                    val ep = row.episode
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clickable { playEpisode(ep) },
                                        ) {
                                            Text(
                                                ep.title,
                                                color = Paper,
                                                maxLines = Podcasts.titleMaxLines(home = true),
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                row.show.title,
                                                color = Dim,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = Podcasts.showMaxLines(nextEpisodes = true),
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (ep.durationMs > 0) {
                                            Text(
                                                Podcasts.formatDuration(ep.durationMs),
                                                color = Dim,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        DeleteIcon(
                                            Modifier
                                                .semantics { contentDescription = "dismiss episode" }
                                                .clickable { skipEpisode(ep) }
                                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                        )
                                    }
                                }
                                is PodcastHomeRow.Subscription -> {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clickable { openPodcastShow(row.show.feedUrl) },
                                        ) {
                                            Text(row.show.title, color = Paper)
                                            if (row.show.author.isNotBlank()) {
                                                Text(
                                                    row.show.author,
                                                    color = Dim,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                        DeleteIcon(
                                            Modifier
                                                .semantics { contentDescription = "unsubscribe" }
                                                .clickable { unsubscribeShow(row.show.feedUrl) }
                                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                        )
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
                    onHub = { page = Page.Home },
                )
                        }
                        else -> Unit
                    }
                }
            }
            if (HomeStrip.coversPager(page)) {
            Column(
                Modifier
                    .zIndex(1f)
                    .fillMaxSize()
                    .background(Ink)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
            when (page) {
            Page.Todos -> {
                val todos = HomeTodos.of(local)
                val openTodos = HomeTodos.open(todos)
                val doneTodos = HomeTodos.completed(todos)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        HomeTodos.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable { openHomeDefault() }
                            .padding(vertical = 6.dp),
                    )
                    CopyIcon(
                        Modifier
                            .clickable {
                                copyText("todos", HomeTodos.shareMarkdown(todos))
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                var dragFrom by remember { mutableStateOf<Int?>(null) }
                var dragTo by remember { mutableStateOf<Int?>(null) }
                var dragY by remember { mutableFloatStateOf(0f) }
                var rowHeight by remember { mutableFloatStateOf(0f) }
                val gap = with(LocalDensity.current) { 6.dp.toPx() }
                val liveOpen = rememberUpdatedState(openTodos)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = dragFrom == null,
                ) {
                    itemsIndexed(openTodos, key = { _, it -> "t" + it.id }) { index, item ->
                        val lifting = dragFrom == index
                        val stepPx = (rowHeight + gap).takeIf { it > 1f } ?: 0f
                        val shift = when {
                            lifting -> dragY
                            dragFrom != null && dragTo != null && stepPx > 0f ->
                                ListReorder.neighborOffset(index, dragFrom!!, dragTo!!, stepPx)
                            else -> 0f
                        }
                        TodoLine(
                            item,
                            onToggle = { lists.toggleComplete(item.id) },
                            onDelete = { lists.remove(item.id) },
                            onEdit = {
                                editingTodoId = item.id
                                applyMode(PrefixCommands.Mode(prompt = '-', input = item.text))
                            },
                            modifier = Modifier
                                .zIndex(if (lifting) 1f else 0f)
                                .graphicsLayer { translationY = shift }
                                .onSizeChanged { rowHeight = it.height.toFloat() }
                                .then(if (dragFrom == null) Modifier.animateItem() else Modifier),
                            textModifier = Modifier.pointerInput(item.id) {
                                detectTapOrLongDrag(
                                    onTap = { lists.toggleComplete(item.id) },
                                    onDragStart = {
                                        val i = ListReorder.liveIndex(liveOpen.value) { it.id == item.id }
                                        if (i < 0) return@detectTapOrLongDrag
                                        dragFrom = i
                                        dragTo = i
                                        dragY = 0f
                                    },
                                    onDrag = { amount ->
                                        dragY += amount
                                        val from = dragFrom ?: return@detectTapOrLongDrag
                                        val step = (rowHeight + gap).takeIf { it > 1f }
                                            ?: return@detectTapOrLongDrag
                                        dragTo = ListReorder.targetIndex(from, dragY, step, liveOpen.value.lastIndex)
                                    },
                                    onDragEnd = {
                                        val from = dragFrom
                                        val to = dragTo
                                        dragFrom = null
                                        dragTo = null
                                        dragY = 0f
                                        if (from != null && to != null && from != to) lists.moveOpen(from, to)
                                    },
                                    onDragCancel = {
                                        dragFrom = null
                                        dragTo = null
                                        dragY = 0f
                                    },
                                )
                            },
                        )
                    }
                    if (doneTodos.isNotEmpty()) {
                        item {
                            Text(
                                "done",
                                color = Dim,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(doneTodos, key = { "d" + it.id }) { item ->
                        TodoLine(
                            item,
                            onToggle = { lists.toggleComplete(item.id) },
                            onDelete = { lists.remove(item.id) },
                            onEdit = {
                                editingTodoId = item.id
                                applyMode(PrefixCommands.Mode(prompt = '-', input = item.text))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                    showSubmit = editingTodoId != null,
                )
            }
            Page.Notes -> {
                val notes = Notes.of(local)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Notes.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable { page = Page.Home }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (notes.isEmpty()) {
                        item {
                            Text("Type + to write a note.", color = Dim)
                        }
                    }
                    items(notes, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { openNoteEditor(item.id, item.text, fromList = true) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(Notes.title(item.text), color = Paper)
                                Text(
                                    Notes.editedLabel(item.editedAt),
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            DeleteIcon(
                                Modifier
                                    .clickable { lists.remove(item.id) }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
            Page.NoteEditor -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Notes.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable { saveAndCloseNote() }
                            .padding(vertical = 6.dp),
                    )
                    CopyIcon(
                        Modifier
                            .clickable { copyText("note", noteDraft) }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val noteAccent = Accent
                BasicTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    visualTransformation = remember(noteAccent) { MarkdownVisualTransformation(noteAccent) },
                    cursorBrush = SolidColor(Accent),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            Page.Chat -> {
                val thread = chatId?.let { id -> chatThreads.find { it.id == id } }
                val messages = thread?.messages.orEmpty()
                val listState = rememberLazyListState()
                var providerMenu by remember { mutableStateOf(false) }
                LaunchedEffect(messages.size, chatBusy, streamDraft) {
                    val target = if (chatBusy) messages.size else messages.lastIndex
                    if (target >= 0) listState.scrollToItem(target)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Chats.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable {
                                page = Page.Home
                                clearBar()
                            }
                            .padding(vertical = 6.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderIcon(
                            settings.provider,
                            Modifier
                                .semantics { contentDescription = ProviderHandoff.contentDescription(settings.provider) }
                                .combinedClickable(
                                    onClick = { handoffToProvider() },
                                    onLongClick = { providerMenu = true },
                                    onLongClickLabel = "switch provider",
                                )
                                .padding(vertical = 6.dp),
                        )
                        HistoryIcon(
                            Modifier
                                .semantics { contentDescription = "history" }
                                .clickable { page = Page.ChatHistory }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = '?',
                    value = input,
                    hardware = hardware,
                    wrap = true,
                    onValue = { typed ->
                        input = if (typed.startsWith(Chats.PREFIX)) typed.drop(1) else typed
                    },
                    onPick = { glyph ->
                        page = Page.Home
                        applyMode(PrefixCommands.pick(PrefixCommands.Mode(), glyph))
                    },
                    onClearMode = {
                        page = Page.Home
                        clearBar()
                    },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (messages.isEmpty() && !chatBusy) {
                        item {
                            Text("Ask a question.", color = Dim)
                        }
                    }
                    items(messages, key = { "${it.role}-${it.createdAt}-${it.content.hashCode()}" }) { msg ->
                        if (msg.fromUser) {
                            Text(
                                msg.content,
                                color = Accent,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.clickable { copyText("question", msg.content) },
                            )
                        } else if (msg.isNotice) {
                            Text(msg.content, color = Dim, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            MarkdownDocument(
                                msg.content,
                                Modifier.clickable { copyText("reply", msg.content) },
                            )
                        }
                    }
                    if (chatBusy) {
                        item {
                            if (streamDraft.isNotEmpty()) {
                                MarkdownDocument(streamDraft)
                            } else {
                                ThinkingDots()
                            }
                        }
                    }
                }
                }
                if (providerMenu) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { providerMenu = false }
                            },
                    )
                    ProviderMenu(
                        current = settings.provider,
                        onPick = { next ->
                            settingsRepo.setProvider(next)
                            providerMenu = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = ProviderMenuBelowIcon, end = ProviderMenuEndInset),
                    )
                }
                }
            }
            Page.ChatHistory -> {
                val rows = Chats.of(chatThreads)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Chats.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable { page = Page.Chat }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rows.isEmpty()) {
                        item {
                            Text("No conversations yet.", color = Dim)
                        }
                    }
                    items(rows, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { openAsk("", id = item.id) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(Chats.title(item.messages), color = Paper)
                                Text(
                                    Chats.editedLabel(item.updatedAt),
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            DeleteIcon(
                                Modifier
                                    .semantics { contentDescription = "delete conversation" }
                                    .clickable {
                                        chats.remove(item.id)
                                        if (chatId == item.id) chatId = null
                                    }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
            Page.Apps -> {
                val listed = remember(input, appsEpoch) {
                    if (input.isBlank()) apps.all() else apps.search(input)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        AppList.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable {
                                page = Page.Home
                                if (input.isNotBlank() || PrefixCommands.find(prompt) != null) applyMode(mode())
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (listed.isEmpty()) {
                        item {
                            Text("No apps match.", color = Dim)
                        }
                    }
                    items(listed, key = { it.packageName + it.activityName }) { app ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        apps.launch(app)
                                        clearBar()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(
                                    drawable = apps.icon(app),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(28.dp),
                                )
                                Text(
                                    app.label,
                                    color = Paper,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp),
                                )
                            }
                            InfoIcon(
                                Modifier
                                    .semantics { contentDescription = "app settings" }
                                    .clickable { apps.openInfo(app) }
                                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                            )
                            DeleteIcon(
                                Modifier
                                    .semantics { contentDescription = "delete app" }
                                    .clickable { apps.uninstall(app) }
                                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
            }
            Page.Clock -> {
                ClockScreen(
                    snapshot = clockState,
                    tab = clockTab,
                    zoneHits = zoneHits,
                    modifier = Modifier.weight(1f),
                    onBack = { page = Page.Home },
                    onTab = { clockTab = it; zoneHits = emptyList() },
                    onPreset = { min ->
                        clock.setTimer(Clock.setDuration(clock.snapshot().timer, min * 60_000L))
                        ClockScheduler.sync(ctx, clock.snapshot())
                    },
                    onStartPause = {
                        val next = if (clock.snapshot().timer.running) {
                            Clock.pause(clock.snapshot().timer, System.currentTimeMillis())
                        } else {
                            Clock.start(clock.snapshot().timer, System.currentTimeMillis())
                        }
                        clock.setTimer(next)
                        ClockScheduler.sync(ctx, clock.snapshot())
                    },
                    onReset = {
                        clock.setTimer(Clock.reset(clock.snapshot().timer))
                        ClockScheduler.sync(ctx, clock.snapshot())
                    },
                    onToggleAlarm = { id ->
                        clock.toggleAlarm(id)
                        ClockScheduler.sync(ctx, clock.snapshot())
                    },
                    onRemoveAlarm = { id ->
                        clock.removeAlarm(id)
                        ClockScheduler.sync(ctx, clock.snapshot())
                    },
                    onPickZone = { addWorldClock(it) },
                    onRemoveZone = { clock.removeZone(it) },
                    onMoveZone = { from, to -> clock.moveZone(from, to) },
                )
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
            }
            Page.Weather -> {
                WeatherScreen(
                    place = settings.weatherPlace,
                    units = settings.weatherUnits,
                    forecast = weatherForecast,
                    modifier = Modifier.weight(1f),
                    onBack = { page = Page.Home },
                    onOpenSettings = { page = Page.Settings },
                )
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
            }
            Page.Usage -> {
                UsageScreen(
                    snapshot = usageSnapshot,
                    modifier = Modifier.weight(1f),
                    onBack = { page = Page.Home },
                    onPeriod = { next -> usagePeriod = next },
                    onGrant = {
                        runCatching { ctx.startActivity(usageReader.settingsIntent()) }
                    },
                    onCycleApp = { pkg ->
                        usageStore.cycle(pkg)
                        usageSnapshot = usageReader.load(usageStore, usagePeriod)
                    },
                )
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
            }
            Page.Settings -> {
                SettingsPage(
                    settings = settings,
                    hardware = hardware,
                    onBack = { page = Page.Home },
                    onOpenAi = { page = Page.AiSettings },
                    repo = settingsRepo,
                    weather = weather,
                    backup = backup,
                    onRequestHome = onRequestHome,
                )
            }
            Page.AiSettings -> {
                AiProvidersPage(
                    settings = settings,
                    onBack = { page = Page.Settings },
                    repo = settingsRepo,
                    oauth = oauth,
                )
            }
            Page.Stocks -> {
                val searching = Stocks.queryFromInput(input).isNotEmpty()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Stocks.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable {
                                applyMode(
                                    PrefixCommands.type(
                                        PrefixCommands.Mode(),
                                        Stocks.leaveDraft(mode().line),
                                    ),
                                )
                                page = Page.Home
                            }
                            .padding(vertical = 6.dp),
                    )
                    GearIcon(
                        Modifier
                            .semantics { contentDescription = "stocks settings" }
                            .clickable { page = Page.StockSettings }
                            .padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                var dragFrom by remember { mutableStateOf<Int?>(null) }
                var dragTo by remember { mutableStateOf<Int?>(null) }
                var dragY by remember { mutableFloatStateOf(0f) }
                var rowHeight by remember { mutableFloatStateOf(0f) }
                val gap = with(LocalDensity.current) { 10.dp.toPx() }
                val liveWatch = rememberUpdatedState(watch)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (searching) {
                        if (stockHits.isEmpty()) {
                            item {
                                Text(if (stockBusy) "Searching tickers…" else "No ticker matches", color = Dim)
                            }
                        }
                        items(stockHits, key = { "h" + it.symbol }) { hit ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { addTicker(hit.symbol) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(hit.symbol, color = Paper)
                                    Text(hit.name, color = Dim, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (hit.exchange.isNotBlank()) {
                                    Text(hit.exchange, color = Dim, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        if (watch.isEmpty()) {
                            item {
                                Text("Type \$AAPL to add a ticker.", color = Dim)
                            }
                        }
                        itemsIndexed(watch, key = { _, it -> it.symbol }) { index, item ->
                            val quote = quotes[item.symbol]
                            val price = quote?.price ?: item.price
                            val percent = quote?.changePercent ?: item.changePercent
                            val up = (percent ?: 0.0) >= 0.0
                            val tone = if (up) Gain else Loss
                            val lifting = dragFrom == index
                            val stepPx = (rowHeight + gap).takeIf { it > 1f } ?: 0f
                            val shift = when {
                                lifting -> dragY
                                dragFrom != null && dragTo != null && stepPx > 0f ->
                                    ListReorder.neighborOffset(index, dragFrom!!, dragTo!!, stepPx)
                                else -> 0f
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (lifting) 1f else 0f)
                                    .graphicsLayer { translationY = shift }
                                    .onSizeChanged { rowHeight = it.height.toFloat() }
                                    .then(if (dragFrom == null) Modifier.animateItem() else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .pointerInput(item.symbol) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    val i = ListReorder.liveIndex(liveWatch.value) { it.symbol == item.symbol }
                                                    if (i < 0) return@detectDragGesturesAfterLongPress
                                                    dragFrom = i
                                                    dragTo = i
                                                    dragY = 0f
                                                },
                                                onDragEnd = {
                                                    val from = dragFrom
                                                    val to = dragTo
                                                    dragFrom = null
                                                    dragTo = null
                                                    dragY = 0f
                                                    if (from != null && to != null) stocks.move(from, to)
                                                },
                                                onDragCancel = {
                                                    dragFrom = null
                                                    dragTo = null
                                                    dragY = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragY += amount.y
                                                    val from = dragFrom ?: return@detectDragGesturesAfterLongPress
                                                    val step = (rowHeight + gap).takeIf { it > 1f }
                                                        ?: return@detectDragGesturesAfterLongPress
                                                    dragTo = ListReorder.targetIndex(from, dragY, step, liveWatch.value.lastIndex)
                                                },
                                            )
                                        }
                                        .clickable { openStockDetail(item.symbol) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.symbol, color = Paper)
                                        Text(
                                            quote?.name ?: item.name,
                                            color = Dim,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (price != null) Stocks.formatPrice(price, quote?.currency ?: item.currency) else "—",
                                            color = Paper,
                                        )
                                        Text(
                                            if (percent != null) Stocks.formatPercent(percent) else "—",
                                            color = if (percent == null) Dim else tone,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                DeleteIcon(
                                    Modifier
                                        .semantics { contentDescription = "delete stock" }
                                        .clickable { stocks.remove(item.symbol) }
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = prompt,
                    value = input,
                    hardware = hardware,
                    onValue = { applyMode(PrefixCommands.type(mode(), it)) },
                    onPick = { applyMode(PrefixCommands.pick(mode(), it)) },
                    onClearMode = { applyMode(PrefixCommands.clearMode(mode())) },
                    onSubmit = { runCommand() },
                    onSlash = { pickSlash(it) },
                    onHub = { openHub() },
                )
            }
            Page.StockDetail -> {
                val symbol = stockSymbol.orEmpty()
                val day = stockChart?.quote ?: quotes[symbol]
                val extra = stockDetails?.quote
                val live = quotes[symbol]
                val quote = day?.copy(
                    pe = extra?.pe,
                    marketCap = extra?.marketCap,
                    dividendYield = extra?.dividendYield,
                    eps = extra?.eps,
                    beta = extra?.beta,
                    avgVolume = extra?.avgVolume,
                    extendedLabel = live?.extendedLabel ?: day.extendedLabel ?: extra?.extendedLabel,
                    extendedPrice = live?.extendedPrice ?: day.extendedPrice ?: extra?.extendedPrice,
                    extendedChange = live?.extendedChange ?: day.extendedChange ?: extra?.extendedChange,
                    extendedPercent = live?.extendedPercent ?: day.extendedPercent ?: extra?.extendedPercent,
                ) ?: extra
                val item = watch.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                val name = quote?.name ?: item?.name ?: symbol
                val points = stockChart?.points.orEmpty()
                val scrubPoint = stockScrub?.let { points.getOrNull(it) }
                val currency = quote?.currency ?: item?.currency ?: "USD"
                val livePrice = quote?.price ?: item?.price
                val liveChange = quote?.change
                val livePercent = quote?.changePercent ?: item?.changePercent
                val baseline = scrubPoint?.let { Stocks.scrubBaseline(points, stockRange, quote?.previousClose) }
                val price = scrubPoint?.close ?: livePrice
                val change = if (scrubPoint != null && baseline != null) scrubPoint.close - baseline else liveChange
                val percent = if (scrubPoint != null && baseline != null && baseline != 0.0) {
                    (scrubPoint.close - baseline) / baseline * 100.0
                } else {
                    livePercent
                }
                val up = (percent ?: 0.0) >= 0.0
                val tone = if (up) Gain else Loss
                val chartUp = (livePercent ?: 0.0) >= 0.0
                val changeLine = when {
                    change != null && percent != null ->
                        "${Stocks.formatChange(change)} (${Stocks.formatPercent(percent)})"
                    percent != null -> Stocks.formatPercent(percent)
                    else -> ""
                }
                val dateLine = scrubPoint?.let { Stocks.formatChartTime(it.time, stockRange) }.orEmpty()
                val extendedLine = if (scrubPoint == null) quote?.let { Stocks.formatExtended(it) }.orEmpty() else ""
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        Stocks.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable { page = Page.Stocks }
                            .padding(vertical = 6.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(symbol, style = MaterialTheme.typography.headlineLarge, color = Paper)
                    Text(name, color = Dim, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (price != null) Stocks.formatPrice(price, currency) else "—",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Paper,
                    )
                    if (changeLine.isNotBlank()) {
                        Text(changeLine, color = tone, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (dateLine.isNotBlank()) {
                        Text(dateLine, color = Dim, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (extendedLine.isNotBlank()) {
                        val extendedUp = (quote?.extendedChange ?: 0.0) >= 0.0
                        Text(
                            extendedLine,
                            color = if (extendedUp) Gain else Loss,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    StockChart(
                        points = points,
                        up = chartUp,
                        selectedIndex = stockScrub,
                        onSelect = { stockScrub = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StockRange.entries.forEach { range ->
                            Text(
                                range.label,
                                color = if (range == stockRange) Accent else Dim,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable { stockRange = range }
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val statsQuote = quote
                    if (statsQuote != null) {
                        Stocks.quoteStats(statsQuote).forEach { StockStatPair(it) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("CAGR", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Stocks.cagrStats(stockDetails?.cagr ?: StockCagr()).forEach { StockStatPair(it) }
                }
            }
            Page.StockSettings -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Stocks.BACK,
                            color = Accent,
                            modifier = Modifier
                                .clickable { page = Page.Stocks }
                                .padding(vertical = 6.dp),
                        )
                        Text("stocks", color = Dim)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("New stocks", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        StockInsert.entries.forEach { insert ->
                            Text(
                                insert.name.lowercase(),
                                color = if (settings.stockInsert == insert) Accent else Dim,
                                modifier = Modifier.clickable { settingsRepo.update { it.copy(stockInsert = insert) } },
                            )
                        }
                    }
                    Text(
                        if (settings.stockInsert == StockInsert.BOTTOM) {
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
                        "${watch.size} of ${Stocks.MAX} tickers",
                        color = Paper,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    Text(
                        "copy list",
                        color = Paper,
                        modifier = Modifier
                            .clickable { copyText("stocks", StocksCsv.export(watch)) }
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        "paste (add)",
                        color = Paper,
                        modifier = Modifier
                            .clickable { importTickers(clipboardText()) }
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        "replace list",
                        color = Paper,
                        modifier = Modifier
                            .clickable { importTickers(clipboardText(), replace = true) }
                            .padding(vertical = 8.dp),
                    )
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
            Page.PodcastShow -> {
                val feed = podcastShowUrl.orEmpty()
                val show = podcastShows.find { it.feedUrl == feed }
                val eps = podcasts.episodesFor(feed)
                val order = show?.episodeOrder ?: EpisodeOrder.NEWEST
                Text(
                    Podcasts.BACK,
                    color = Accent,
                    modifier = Modifier
                        .clickable { page = Page.Podcasts }
                        .padding(vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(show?.title ?: "Podcast", color = Paper, style = MaterialTheme.typography.headlineLarge)
                show?.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(author, color = Dim, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Text("Episodes", color = Dim, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf(EpisodeOrder.NEWEST, EpisodeOrder.OLDEST).forEach { option ->
                        Text(
                            Podcasts.episodeOrderLabel(option),
                            color = if (order == option) Accent else Dim,
                            modifier = Modifier.clickable { podcasts.setEpisodeOrder(feed, option) },
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(eps, key = { it.id }) { ep ->
                        val prog = podcastProgress[ep.id]
                        val skipped = Podcasts.skipped(prog)
                        val inProgress = prog != null && !Podcasts.finished(prog) && !skipped
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (skipped) podcasts.setSkipped(ep.id, false, ep.durationMs)
                                        else openPodcastEpisode(ep.id)
                                    },
                            ) {
                                Text(
                                    ep.title,
                                    color = when {
                                        skipped -> Dim
                                        inProgress -> Accent
                                        else -> Paper
                                    },
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        Podcasts.episodeLeftMeta(prog, ep.durationMs),
                                        color = Dim,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    val date = Podcasts.formatEpisodeDate(ep.pubDate)
                                    if (date.isNotBlank()) {
                                        Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            DeleteIcon(
                                Modifier
                                    .semantics { contentDescription = if (skipped) "restore episode" else "dismiss episode" }
                                    .clickable {
                                        if (skipped) podcasts.setSkipped(ep.id, false, ep.durationMs)
                                        else skipEpisode(ep)
                                    }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
            Page.PodcastEpisode -> {
                val ep = podcastEpisodes.find { it.id == podcastEpisodeId }
                val show = ep?.let { podcasts.show(it.showId) }
                val prog = ep?.let { podcastProgress[it.id] }
                val playingThis = playback.episodeId == ep?.id
                val pos = if (playingThis) playback.positionMs else prog?.positionMs ?: 0L
                val dur = if (playingThis && playback.durationMs > 0) playback.durationMs else (prog?.durationMs ?: ep?.durationMs ?: 0L)
                val downloaded = ep != null && podcastDownloads.containsKey(ep.id)
                var speedMenu by remember(podcastEpisodeId) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Podcasts.BACK,
                        color = Accent,
                        modifier = Modifier
                            .clickable {
                                page = if (podcastShowUrl != null) Page.PodcastShow else Page.Podcasts
                            }
                            .padding(vertical = 6.dp),
                    )
                    if (ep != null) {
                        val downloading = podcastDownloadBusy || podcastTransfer.episodeId == ep.id
                        val downloadKnown = podcastTransfer.episodeId == ep.id && podcastTransfer.totalBytes > 0L
                        val downloadPct = Podcasts.downloadPercent(podcastTransfer.receivedBytes, podcastTransfer.totalBytes)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (downloading) {
                                Text(
                                    if (downloadKnown) "$downloadPct%" else "…",
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            DownloadIcon(
                                filled = downloaded,
                                modifier = Modifier
                                    .semantics { contentDescription = if (downloaded) "downloaded" else "download" }
                                    .clickable {
                                        if (downloaded || downloading) return@clickable
                                        scope.launch {
                                            podcastDownloadBusy = true
                                            val file = podcasts.download(ep)
                                            podcastDownloadBusy = false
                                            Toast.makeText(
                                                ctx,
                                                if (file != null) "Downloaded" else "Download failed",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (ep == null) {
                    Text("Episode gone", color = Dim)
                } else {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(show?.title ?: "Podcast", color = Dim, style = MaterialTheme.typography.bodyMedium)
                    Text(ep.title, color = Paper, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (dur > 0) Podcasts.formatPosition(pos, dur) else "stream",
                            color = Dim,
                        )
                        PodcastSpeedMenu(
                            speed = playback.speed,
                            expanded = speedMenu,
                            onExpanded = { speedMenu = it },
                            onPick = { applyPodcastSpeed(it) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PodcastScrubBar(
                        progress = Podcasts.fraction(pos, dur),
                        onSeekFraction = { frac ->
                            val next = Podcasts.progressAt(frac, 1f, dur)
                            if (playingThis) PodcastPlayer.seek(next)
                            else podcasts.saveProgress(ep.id, next, dur)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "−15",
                            color = Paper,
                            modifier = Modifier
                                .clickable {
                                    if (playingThis) PodcastPlayer.skip(-Podcasts.SKIP_MS)
                                    else podcasts.saveProgress(ep.id, Podcasts.skip(pos, dur, -Podcasts.SKIP_MS), dur)
                                }
                                .padding(vertical = 8.dp),
                        )
                        PlayPauseIcon(
                            playing = playingThis && playback.playing,
                            modifier = Modifier
                                .semantics { contentDescription = if (playingThis && playback.playing) "pause" else "play" }
                                .clickable {
                                    if (playingThis && playback.playing) {
                                        PodcastPlayer.pause()
                                        PodcastPlaybackService.pause(ctx)
                                    } else if (playingThis) {
                                        PodcastPlayer.resume()
                                        PodcastPlaybackService.start(
                                            ctx,
                                            show?.title ?: "Podcast",
                                            ep.title,
                                            show?.artworkUrl.orEmpty(),
                                        )
                                    } else {
                                        playEpisode(ep)
                                    }
                                }
                                .padding(vertical = 8.dp),
                        )
                        Text(
                            "+15",
                            color = Paper,
                            modifier = Modifier
                                .clickable {
                                    if (playingThis) PodcastPlayer.skip(Podcasts.SKIP_MS)
                                    else podcasts.saveProgress(ep.id, Podcasts.skip(pos, dur, Podcasts.SKIP_MS), dur)
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                    if (ep.description.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Show notes", color = Dim, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        PodcastNotesText(
                            notes = ep.description,
                            onTimestamp = { ms -> seekEpisode(ep, ms) },
                        )
                    }
                    }
                }
            }
            Page.PodcastSettings -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Podcasts.BACK,
                            color = Accent,
                            modifier = Modifier
                                .clickable { page = Page.Podcasts }
                                .padding(vertical = 6.dp),
                        )
                        Text("podcasts", color = Dim)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Playback speed", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Text(
                        Podcasts.formatSpeed(podcastSpeed),
                        color = Accent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PodcastSpeedBar(
                        progress = Podcasts.speedProgress(podcastSpeed),
                        onSpeedFraction = { frac -> applyPodcastSpeed(Podcasts.speedAt(frac, 1f)) },
                    )
                    Text(
                        "Applies to every show.",
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Download cache", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        Podcasts.CACHE_PRESETS.forEach { bytes ->
                            Text(
                                Podcasts.cacheLabel(bytes),
                                color = if (podcastCache == bytes) Accent else Dim,
                                modifier = Modifier.clickable { podcasts.setCacheBytes(bytes) },
                            )
                        }
                    }
                    Text(
                        "${Podcasts.cacheLabel(podcasts.cacheUsedBytes())} used of ${Podcasts.cacheLabel(podcastCache)}. Oldest downloads delete first.",
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Overcast / OPML", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${podcastShows.size} of ${Podcasts.MAX_SHOWS} shows",
                        color = Paper,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    Text(
                        "paste OPML",
                        color = Paper,
                        modifier = Modifier
                            .clickable { importOpml(clipboardText()) }
                            .padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Overcast: Settings → Export OPML, copy the file, then paste here. RSS feed URLs also subscribe from the podcasts bar.",
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
                else -> Unit
            }
            }
            }
        }
    }
        clockState.alert?.let { alert ->
            ClockAlertScreen(
                alert = alert,
                sound = settings.clockSound,
                onStop = { clearClockAlert() },
                onRunAgain = {
                    clock.setTimer(Clock.runAgain(alert, System.currentTimeMillis()))
                    clearClockAlert()
                    ClockScheduler.sync(ctx, clock.snapshot())
                },
                onDismiss = {
                    clearClockAlert()
                    ClockScheduler.sync(ctx, clock.snapshot())
                },
                onSnooze = {
                    val alarm = clock.snapshot().alarms.find { it.id == alert.alarmId }
                    if (alarm != null) {
                        clock.replaceAlarm(Clock.snooze(alarm, System.currentTimeMillis()))
                    }
                    clearClockAlert()
                    ClockScheduler.sync(ctx, clock.snapshot())
                },
            )
        }
    }
}

@Composable
private fun ClockHeader(
    weather: String?,
    weatherKind: WeatherKind? = null,
    isDay: Boolean = true,
    ticker: HomeTickerLine?,
    event: UpcomingEvent?,
    timer: TimerState,
    analog: Boolean,
    onOpenClock: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenTicker: () -> Unit,
    onOpenEvent: () -> Unit,
    playing: Boolean = false,
    episodeLoaded: Boolean = false,
    onOpenPodcasts: () -> Unit = {},
    onTogglePlayback: () -> Unit = {},
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.running, timer.endsAt, analog) {
        now.value = System.currentTimeMillis()
        while (true) {
            now.value = System.currentTimeMillis()
            delay(if (timer.running || analog) 200 else 15_000)
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
                Modifier.clickable { onOpenClock() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (timer.running || !analog) {
                    Text(time, style = MaterialTheme.typography.headlineLarge)
                    Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    AnalogClock(
                        hour = cal.get(Calendar.HOUR),
                        minute = cal.get(Calendar.MINUTE),
                        second = cal.get(Calendar.SECOND),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(time, color = Paper, style = MaterialTheme.typography.bodyMedium)
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
private fun StockStatPair(row: StockStatLine) {
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
private fun TodoPreview(
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
        )
    }
}

@Composable
private fun TodoLine(
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
private fun CommandBar(
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
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    var slashSelected by remember { mutableStateOf(0) }
    val slashMode = prompt == SlashCommands.PROMPT
    val slashMatches = if (slashMode) SlashCommands.matches(value) else emptyList()
    val wrapField = wrap || PrefixCommands.wrapsInput(prompt)
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
        if (slashMode) menuOpen = false
    }
    LaunchedEffect(slashMatches.size, value) {
        if (slashSelected >= slashMatches.size) slashSelected = 0
    }
    fun pick(index: Int) {
        val cmd = PrefixCommands.all.getOrNull(index) ?: return
        menuOpen = false
        selected = 0
        onPick(cmd.glyph)
        focus.requestFocus()
    }
    fun pickSlash(index: Int) {
        val cmd = slashMatches.getOrNull(index) ?: return
        onSlash(cmd)
        focus.requestFocus()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (slashMode) {
            SlashCommandMenu(
                commands = slashMatches,
                selected = slashSelected,
                onSelect = { cmd ->
                    val index = slashMatches.indexOf(cmd)
                    if (index >= 0) pickSlash(index)
                },
            )
        } else if (menuOpen) {
            CommandMenu(
                selected = selected,
                onSelect = { cmd ->
                    val index = PrefixCommands.all.indexOf(cmd)
                    if (index >= 0) pick(index)
                },
            )
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
            verticalAlignment = if (wrapField) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                prompt.toString(),
                color = Accent,
                modifier = Modifier
                    .semantics { contentDescription = "commands" }
                    .clickable {
                        if (slashMode) {
                            onClearMode()
                        } else {
                            menuOpen = !menuOpen
                            if (menuOpen) selected = 0
                        }
                    }
                    .padding(end = 10.dp, top = if (wrapField) 2.dp else 0.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = {
                    if (menuOpen) {
                        menuOpen = false
                    } else {
                        onValue(it)
                    }
                },
                singleLine = !wrapField,
                maxLines = if (wrapField) 8 else 1,
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
                    .then(if (wrapField) Modifier.heightIn(max = 160.dp) else Modifier)
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
                                    menuOpen = false
                                    true
                                }
                                else -> {
                                    menuOpen = false
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
                                        menuOpen = true
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
                        .padding(start = 12.dp, top = if (wrapField) 2.dp else 0.dp),
                )
            }
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ThinkingDots() {
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
private fun HelpBlock() {
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

@Composable
private fun SettingsPage(
    settings: BuilderSettings,
    hardware: Boolean,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    repo: SettingsRepository,
    weather: WeatherRepository,
    backup: BackupService,
    onRequestHome: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var placeQuery by remember { mutableStateOf(settings.weatherPlace) }
    var suggestions by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var accentDraft by remember { mutableStateOf(settings.accentHex) }
    var s3Endpoint by remember { mutableStateOf(settings.s3Endpoint) }
    var s3Bucket by remember { mutableStateOf(settings.s3Bucket) }
    var s3Access by remember { mutableStateOf(settings.s3AccessKey) }
    var s3Secret by remember { mutableStateOf(settings.s3SecretKey) }
    var s3Encryption by remember { mutableStateOf(settings.s3EncryptionKey) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var s3Probe by remember { mutableStateOf<S3Access>(S3Access.Idle) }
    LaunchedEffect(settings.s3Endpoint, settings.s3Bucket, settings.s3AccessKey, settings.s3SecretKey, settings.s3EncryptionKey) {
        if (!S3Signer.credentialsReady(settings.s3Endpoint, settings.s3Bucket, settings.s3AccessKey, settings.s3SecretKey)) {
            s3Probe = S3Access.Idle
            return@LaunchedEffect
        }
        s3Probe = S3Access.Testing
        delay(700)
        s3Probe = withContext(Dispatchers.IO) { backup.probe() }
    }
    LaunchedEffect(placeQuery, settings.weatherPlace, settings.weatherLat) {
        val q = placeQuery.trim()
        if (q.length < 2 || (q == settings.weatherPlace && settings.weatherLat != null)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(280)
        suggestions = weather.suggest(q)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("settings", color = Accent)
            Text("home", color = Dim, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(16.dp))
        AccentPicker(
            hex = settings.accentHex,
            onPick = { next ->
                accentDraft = next
                repo.update { it.copy(accentHex = next) }
            },
        )
        LabeledField("Hex", accentDraft, AccentColor.DEFAULT_HEX) {
            accentDraft = it
            if (AccentColor.parse(it) != null) {
                repo.update { s -> s.copy(accentHex = AccentColor.normalize(it)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        CaretLink(
            "… AI providers >",
            modifier = Modifier
                .clickable { onOpenAi() }
                .padding(vertical = 6.dp)
                .fillMaxWidth(),
        )
        Text(
            AiPlatforms.of(settings.provider).label,
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text("Keyboard", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            KeyboardMode.entries.forEach { mode ->
                Text(
                    mode.name.lowercase(),
                    color = if (settings.keyboardMode == mode) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(keyboardMode = mode) } },
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
                    modifier = Modifier.clickable { repo.update { it.copy(appIcons = style) } },
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
        Spacer(Modifier.height(16.dp))
        Text("Clock face", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ClockFace.entries.forEach { face ->
                Text(
                    face.name.lowercase(),
                    color = if (settings.clockFace == face) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(clockFace = face) } },
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
        WeatherLocationField(
            query = placeQuery,
            locked = settings.weatherLat != null && placeQuery == settings.weatherPlace,
            suggestions = suggestions,
            onQuery = { next ->
                placeQuery = next
                if (next.isBlank()) {
                    repo.update { s -> s.copy(weatherPlace = "", weatherLat = null, weatherLon = null) }
                    suggestions = emptyList()
                } else if (settings.weatherLat != null && next != settings.weatherPlace) {
                    repo.update { s -> s.copy(weatherPlace = "", weatherLat = null, weatherLon = null) }
                }
            },
            onPick = { place ->
                placeQuery = place.label
                suggestions = emptyList()
                repo.update {
                    it.copy(
                        weatherPlace = place.label,
                        weatherLat = place.latitude,
                        weatherLon = place.longitude,
                    )
                }
                scope.launch { weather.refresh() }
            },
        )
        Spacer(Modifier.height(16.dp))
        Text("Weather units", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            WeatherUnits.entries.forEach { units ->
                Text(
                    units.name.lowercase(),
                    color = if (settings.weatherUnits == units) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(weatherUnits = units) } },
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
                Text(
                    sound.label,
                    color = if (settings.clockSound == sound) Accent else Dim,
                    modifier = Modifier.clickable {
                        repo.update { it.copy(clockSound = sound) }
                        ClockSoundPlayer.preview(ctx, sound)
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ClockSound.entries.drop(3).forEach { sound ->
                Text(
                    sound.label,
                    color = if (settings.clockSound == sound) Accent else Dim,
                    modifier = Modifier.clickable {
                        repo.update { it.copy(clockSound = sound) }
                        ClockSoundPlayer.preview(ctx, sound)
                    },
                )
            }
        }
        Text(
            "Tap a sound to hear it. Each tone loops 10 to 30 seconds. Alarms fade in over 4 seconds.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text("Backup", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(
            "S3-compatible snapshot (R2, AWS, B2, MinIO). Encrypted on the phone before upload. Restore replaces todos, notes, chats, pins, stocks, podcasts, alarms, and settings. OAuth tokens stay on this phone.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        LabeledField("Endpoint", s3Endpoint, "https://ACCOUNT.r2.cloudflarestorage.com") {
            s3Endpoint = it
            repo.update { s -> s.copy(s3Endpoint = it.trim()) }
        }
        LabeledField("Bucket", s3Bucket, "bucket") {
            s3Bucket = it
            repo.update { s -> s.copy(s3Bucket = it.trim()) }
        }
        LabeledField("Access key", s3Access, "access key id") {
            s3Access = it
            repo.update { s -> s.copy(s3AccessKey = it.trim()) }
        }
        LabeledField("Secret key", s3Secret, "secret access key") {
            s3Secret = it
            repo.update { s -> s.copy(s3SecretKey = it) }
        }
        Spacer(Modifier.height(8.dp))
        when (val result = s3Probe) {
            S3Access.Idle -> Text(
                "Set endpoint, bucket, and keys to test S3.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
            S3Access.Testing -> Text("Testing S3 access…", color = Dim, style = MaterialTheme.typography.bodyMedium)
            is S3Access.Done -> Text(
                result.line,
                color = if (result.ok) Accent else Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LabeledField("Encryption key", s3Encryption, "passphrase") {
            s3Encryption = it
            repo.update { s -> s.copy(s3EncryptionKey = it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (settings.backupIncludeAiCredentials) "[x] Include AI credentials" else "[ ] Include AI credentials",
            color = Paper,
            modifier = Modifier
                .clickable {
                    repo.update { it.copy(backupIncludeAiCredentials = !it.backupIncludeAiCredentials) }
                }
                .padding(vertical = 8.dp),
        )
        Text(
            if (settings.backupIncludeAiCredentials) {
                "On. Do not enable unless you use encrypted S3 backups or you understand the risk. Applies to S3 and the JSON share. OAuth tokens still stay on this phone."
            } else {
                "Off. API keys stay out of S3 backups and the JSON share."
            },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("Frequency", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            BackupFrequency.entries.forEach { item ->
                Text(
                    item.label,
                    color = if (settings.backupFrequency == item) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(backupFrequency = item) } },
                )
            }
        }
        Text(
            "Backup now",
            color = if (backupBusy) Dim else Paper,
            modifier = Modifier
                .clickable(enabled = !backupBusy) {
                    backupBusy = true
                    confirmRestore = false
                    backupMsg = "Uploading…"
                    scope.launch {
                        backupMsg = runCatching { withContext(Dispatchers.IO) { backup.upload() } }
                            .getOrElse { it.message ?: "Backup failed" }
                        backupBusy = false
                    }
                }
                .padding(vertical = 8.dp),
        )
        Text(
            if (confirmRestore) "Tap again to replace local data" else "Restore from S3",
            color = if (backupBusy) Dim else Paper,
            modifier = Modifier
                .clickable(enabled = !backupBusy) {
                    if (!confirmRestore) {
                        confirmRestore = true
                        backupMsg = "Restore replaces todos, notes, chats, pins, stocks, podcasts, alarms, and settings."
                        return@clickable
                    }
                    backupBusy = true
                    confirmRestore = false
                    backupMsg = "Restoring…"
                    scope.launch {
                        backupMsg = runCatching { withContext(Dispatchers.IO) { backup.restore() } }
                            .getOrElse { it.message ?: "Restore failed" }
                        backupBusy = false
                    }
                }
                .padding(vertical = 8.dp),
        )
        Text(
            "Share unencrypted JSON",
            color = Paper,
            modifier = Modifier
                .clickable {
                    confirmRestore = false
                    runCatching { backup.shareUnencrypted() }
                        .onFailure { backupMsg = it.message ?: "Share failed" }
                }
                .padding(vertical = 8.dp),
        )
        Text(
            "Last backup: ${BackupService.lastBackupLabel(settings.lastBackupAtEpochMs)}",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        backupMsg?.let {
            Text(it, color = Dim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Notification access (hub)",
            color = Paper,
            modifier = Modifier.clickable {
                ctx.startActivity(
                    android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Usage access",
            color = Paper,
            modifier = Modifier.clickable {
                ctx.startActivity(
                    android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Set as default home app",
            color = Paper,
            modifier = Modifier.clickable { onRequestHome() },
        )
        Spacer(Modifier.height(24.dp))
        Text("Tokens stay on the device. They are sent only as a Bearer token to the provider you chose.", color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AiProvidersPage(
    settings: BuilderSettings,
    onBack: () -> Unit,
    repo: SettingsRepository,
    oauth: OAuthService,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = AiPlatforms.of(settings.provider)
    val accessClient = remember { ProviderAccess(repo, oauth) }
    var baseUrl by remember { mutableStateOf(settings.hermesBaseUrl) }
    var webUrl by remember { mutableStateOf(settings.hermesWebUrl) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var paste by remember { mutableStateOf("") }
    var oauthMsg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DevicePending?>(null) }
    var pkce by remember { mutableStateOf<PkceSession?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var access by remember { mutableStateOf<AccessCheck>(AccessCheck.Testing) }
    LaunchedEffect(settings.provider, settings.hermesBaseUrl, settings.hermesWebUrl, settings.apiKey, settings.model) {
        baseUrl = settings.hermesBaseUrl
        webUrl = settings.hermesWebUrl
        key = settings.apiKey
        model = settings.model
    }
    LaunchedEffect(
        settings.provider,
        settings.hermesBaseUrl,
        settings.hermesWebUrl,
        settings.apiKey,
        settings.model,
        settings.oauthAccess,
        settings.oauthRefresh,
    ) {
        access = AccessCheck.Testing
        delay(700)
        access = accessClient.check(settings)
    }

    fun cancelAuth() {
        pollJob?.cancel()
        pollJob = null
        pending = null
        pkce = null
        oauthMsg = null
    }

    fun openHttps(url: String) {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "<",
                color = Accent,
                modifier = Modifier.clickable { onBack() }.padding(vertical = 6.dp),
            )
            Text("AI", color = Accent)
        }
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
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                cancelAuth()
                                repo.setProvider(item.provider)
                            },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (settings.provider == LlmProvider.HERMES) {
            LabeledField(
                "Web UI URL",
                webUrl,
                HermesUrls.DEFAULT_WEBUI,
            ) {
                webUrl = it
                repo.update { s -> s.copy(hermesWebUrl = it) }
            }
        } else if (platform.needsBaseUrl) {
            LabeledField(
                "Base URL",
                baseUrl,
                platform.defaultLocalBase ?: HermesUrls.DEFAULT_API,
            ) {
                baseUrl = it
                repo.update { s -> s.copy(hermesBaseUrl = it) }
            }
        }
        if (settings.provider == LlmProvider.HERMES) {
            Text("Open question in", color = Dim, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "web ui",
                    color = if (!settings.hermesOpenInHermex) Accent else Dim,
                    modifier = Modifier.clickable {
                        repo.update { it.copy(hermesOpenInHermex = false) }
                    },
                )
                Text(
                    "hermex",
                    color = if (settings.hermesOpenInHermex) Accent else Dim,
                    modifier = Modifier.clickable {
                        repo.update { it.copy(hermesOpenInHermex = true) }
                    },
                )
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
            Spacer(Modifier.height(8.dp))
        }
        if (!platform.needsBaseUrl) {
            Text(
                "${platform.apiBase} — sign in or paste an API key. Used only for ? questions.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        OauthBlock(
            settings = settings,
            platformLabel = platform.label,
            spec = platform.oauth,
            pending = pending,
            pkce = pkce,
            paste = paste,
            message = oauthMsg,
            onPaste = { paste = it },
            onSignIn = {
                val spec = platform.oauth ?: return@OauthBlock
                oauthMsg = null
                pollJob?.cancel()
                pollJob = scope.launch {
                    try {
                        when (spec) {
                            is OAuthSpec.Device -> {
                                val existing = pending
                                val next = if (existing != null) {
                                    existing
                                } else {
                                    oauth.beginDevice(settings.provider).also { started ->
                                        pending = started
                                        pkce = null
                                        oauth.browserUrl(started, settings.provider)?.let { openHttps(it) }
                                    }
                                }
                                oauth.pollUntilAuthorized(settings.provider, next)
                                pending = null
                                oauthMsg = "Signed in"
                            }
                            is OAuthSpec.PkcePaste -> {
                                val session = oauth.beginPkce(settings.provider)
                                pkce = session
                                pending = null
                                openHttps(session.authorizeUrl)
                                oauthMsg = "Authorize, then paste the code or callback URL."
                            }
                        }
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        oauthMsg = e.message ?: "Sign-in failed"
                        pending = null
                    }
                }
            },
            onCompletePaste = {
                val session = pkce ?: return@OauthBlock
                scope.launch {
                    try {
                        oauth.completePkce(settings.provider, session, paste)
                        pkce = null
                        paste = ""
                        oauthMsg = "Signed in"
                    } catch (e: Exception) {
                        oauthMsg = e.message ?: "Could not finish sign-in"
                    }
                }
            },
            onSignOut = {
                cancelAuth()
                oauth.signOut()
                oauthMsg = "Signed out"
            },
        )
        LabeledField(
            if (settings.provider == LlmProvider.HERMES) {
                "Web UI password (stored on device)"
            } else {
                "API key (stored on device)"
            },
            key,
            if (settings.provider == LlmProvider.HERMES) {
                "optional if the instance is open"
            } else if (platform.keyOptional) {
                "optional"
            } else {
                "optional if signed in"
            },
        ) {
            key = it
            repo.update { s -> s.copy(apiKey = it) }
        }
        LabeledField("Model", model, platform.defaultModel) {
            model = it
            repo.update { s -> s.copy(model = it) }
        }
        Spacer(Modifier.height(12.dp))
        when (val result = access) {
            AccessCheck.Testing -> Text("Testing access…", color = Dim, style = MaterialTheme.typography.bodyMedium)
            is AccessCheck.Done -> Text(
                result.line,
                color = if (result.ok) Accent else Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Each provider keeps its own sign-in. Tokens stay on the device and are sent only as a Bearer token.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OauthBlock(
    settings: BuilderSettings,
    platformLabel: String,
    spec: OAuthSpec?,
    pending: DevicePending?,
    pkce: PkceSession?,
    paste: String,
    message: String?,
    onPaste: (String) -> Unit,
    onSignIn: () -> Unit,
    onCompletePaste: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (spec == null) return
    val signInLabel = when {
        pending != null -> "Finish sign-in"
        settings.provider == LlmProvider.XAI -> "Sign in with SuperGrok"
        settings.provider == LlmProvider.OPENAI -> "Sign in with ChatGPT"
        settings.provider == LlmProvider.ANTHROPIC -> "Sign in with Claude"
        else -> "Sign in"
    }
    if (settings.signedIn) {
        Text(
            if (settings.oauthAccount.isNotBlank()) "Signed in as ${settings.oauthAccount}" else "Signed in with $platformLabel",
            color = Paper,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("Sign out", color = Accent, modifier = Modifier.clickable { onSignOut() })
    } else {
        Text(signInLabel, color = Paper, modifier = Modifier.clickable { onSignIn() })
    }
    pending?.let {
        Spacer(Modifier.height(8.dp))
        Text("Enter this code in the browser", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(it.userCode, color = Accent, style = MaterialTheme.typography.headlineSmall)
        Text("Waiting for approval…", color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
    if (pkce != null) {
        LabeledField("Paste code or callback URL", paste, "code from the page") { onPaste(it) }
        Text("Finish sign-in", color = Accent, modifier = Modifier.clickable { onCompletePaste() }.padding(vertical = 8.dp))
    }
    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LabeledField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        cursorBrush = SolidColor(Accent),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = Dim, style = MaterialTheme.typography.bodyMedium)
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
    HorizontalDivider(color = Line)
}

@Composable
private fun WeatherLocationField(
    query: String,
    locked: Boolean,
    suggestions: List<WeatherPlace>,
    onQuery: (String) -> Unit,
    onPick: (WeatherPlace) -> Unit,
) {
    Text("Weather location", color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        cursorBrush = SolidColor(Accent),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
        decorationBox = { inner ->
            if (query.isEmpty()) Text("New York", color = Dim, style = MaterialTheme.typography.bodyMedium)
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
    HorizontalDivider(color = Line)
    Text(
        if (locked) {
            "Weather uses this city. No GPS."
        } else {
            "Type a city. Pick a match. No GPS required."
        },
        color = Dim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp),
    )
    suggestions.forEach { place ->
        Text(
            place.label,
            color = Paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(place) }
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun HomeAppRow(
    app: LaunchableApp,
    icons: Boolean,
    drawable: Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icons) {
            AppIcon(
                drawable = drawable,
                grayscale = true,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(28.dp),
            )
        }
        Text(app.label, color = Paper)
    }
}

@Composable
private fun PinnedAppsTextList(
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 6.dp.toPx() }
    val liveApps = rememberUpdatedState(apps)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEachIndexed { index, app ->
            val lifting = dragFrom == index
            val stepPx = (rowHeight + gap).takeIf { it > 1f } ?: 0f
            val shift = when {
                lifting -> dragY
                dragFrom != null && dragTo != null && stepPx > 0f ->
                    ListReorder.neighborOffset(index, dragFrom!!, dragTo!!, stepPx)
                else -> 0f
            }
            Text(
                app.label,
                color = Paper,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (lifting) 1f else 0f)
                    .graphicsLayer { translationY = shift }
                    .onSizeChanged { rowHeight = it.height.toFloat() }
                    .pointerInput(app.packageName, app.activityName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val i = liveApps.value.indexOfFirst {
                                    it.packageName == app.packageName && it.activityName == app.activityName
                                }
                                if (i < 0) return@detectDragGesturesAfterLongPress
                                dragFrom = i
                                dragTo = i
                                dragY = 0f
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val to = dragTo
                                dragFrom = null
                                dragTo = null
                                dragY = 0f
                                if (from != null && to != null) onMove(from, to)
                            },
                            onDragCancel = {
                                dragFrom = null
                                dragTo = null
                                dragY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val from = dragFrom ?: return@detectDragGesturesAfterLongPress
                                val step = (rowHeight + gap).takeIf { it > 1f }
                                    ?: return@detectDragGesturesAfterLongPress
                                dragTo = ListReorder.targetIndex(from, dragY, step, liveApps.value.lastIndex)
                            },
                        )
                    }
                    .clickable { onLaunch(app) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PinnedAppsRow(
    apps: List<LaunchableApp>,
    icon: (LaunchableApp) -> Drawable?,
    onLaunch: (LaunchableApp) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var cellWidth by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    val liveApps = rememberUpdatedState(apps)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(apps, key = { _, it -> it.packageName + it.activityName }) { index, app ->
            val lifting = dragFrom == index
            val stepPx = (cellWidth + gap).takeIf { it > 1f } ?: 0f
            val shift = when {
                lifting -> dragX
                dragFrom != null && dragTo != null && stepPx > 0f ->
                    ListReorder.neighborOffset(index, dragFrom!!, dragTo!!, stepPx)
                else -> 0f
            }
            AppIcon(
                drawable = icon(app),
                grayscale = true,
                modifier = Modifier
                    .size(48.dp)
                    .onSizeChanged { cellWidth = it.width.toFloat() }
                    .zIndex(if (lifting) 1f else 0f)
                    .graphicsLayer { translationX = shift }
                    .semantics { contentDescription = app.label }
                    .pointerInput(app.packageName, app.activityName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val i = liveApps.value.indexOfFirst {
                                    it.packageName == app.packageName && it.activityName == app.activityName
                                }
                                if (i < 0) return@detectDragGesturesAfterLongPress
                                dragFrom = i
                                dragTo = i
                                dragX = 0f
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val to = dragTo
                                dragFrom = null
                                dragTo = null
                                dragX = 0f
                                if (from != null && to != null) onMove(from, to)
                            },
                            onDragCancel = {
                                dragFrom = null
                                dragTo = null
                                dragX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                                val from = dragFrom ?: return@detectDragGesturesAfterLongPress
                                val step = (cellWidth + gap).takeIf { it > 1f }
                                    ?: return@detectDragGesturesAfterLongPress
                                dragTo = ListReorder.targetIndex(from, dragX, step, liveApps.value.lastIndex)
                            },
                        )
                    }
                    .clickable { onLaunch(app) },
            )
        }
    }
}

private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier, grayscale: Boolean = false) {
    val bmp = remember(drawable) {
        runCatching { drawable?.toBitmap(width = 84, height = 84)?.asImageBitmap() }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = if (grayscale) GrayscaleFilter else null,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Line))
    }
}

@Composable
private fun PodcastSearchArt(url: String) {
    var bmp by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (!Podcasts.searchRowShowsArt(url)) {
            bmp = null
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            PodcastArtwork.get(url)?.asImageBitmap()
        }
    }
    val mod = Modifier
        .padding(end = 10.dp)
        .size(Podcasts.ART_DP.dp)
    if (bmp != null) {
        Image(
            bitmap = bmp!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
    } else {
        Box(mod.background(Line))
    }
}
