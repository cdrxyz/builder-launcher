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
import xyz.cdr.builderlauncher.ai.AiFallback
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
import xyz.cdr.builderlauncher.commands.AppPickQuery
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
    nowPlayingRequests: StateFlow<Int> = MutableStateFlow(0),
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
    val podcastSkipSilence by podcasts.skipSilence.collectAsState()
    val playback by PodcastPlayer.state.collectAsState()
    val upcoming by calendar.current.collectAsState()
    val homePressCount by homePresses.collectAsState()
    val nowPlayingRequestCount by nowPlayingRequests.collectAsState()
    var page by remember { mutableStateOf(lastPage) }
    var prompt by remember { mutableStateOf(PrefixCommands.DEFAULT_PROMPT) }
    var actionMenuOpen by remember { mutableStateOf(false) }
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
    var clockTab by remember { mutableStateOf(ClockTab.Timer) }
    var zoneHits by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var tickerIndex by remember { mutableIntStateOf(0) }
    var usagePeriod by remember { mutableStateOf(UsagePeriod.W1) }
    var usageSnapshot by remember { mutableStateOf(Usage.build(emptyList(), emptyMap(), UsagePeriod.W1, false)) }
    var usageToday by remember { mutableStateOf(UsageToday(granted = false)) }
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
    CalendarHomeSync(calendar, settings, appsEpoch)
    DisposableEffect(page) {
        if (page != Page.Settings) ClockSoundPlayer.stopPreview()
        onDispose { ClockSoundPlayer.stopPreview() }
    }
    LaunchedEffect(page) {
        lastPage = page
        if (page != Page.Home) actionMenuOpen = false
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
    LaunchedEffect(page, appsEpoch, settings.pinUsage) {
        if (page == Page.Home && settings.pinUsage) {
            usageToday = usageReader.loadToday(usageStore)
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
        PodcastPlayer.attach(ctx)
        PodcastPlayer.setSpeed(podcasts.playbackSpeed.value)
        PodcastPlayer.setSkipSilence(podcasts.skipSilence.value)
    }
    BuilderLoops(
        lifecycleOwner = lifecycleOwner,
        settings = settings,
        page = page,
        watchSize = watch.size,
        playing = playback.playing,
        episodeId = playback.episodeId,
        weather = weather,
        backup = backup,
        stocks = stocks,
        podcasts = podcasts,
        tickerIndex = tickerIndex,
        onTickerIndex = { tickerIndex = it },
    )
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
        val start = podcasts.progress.value[episode.id]?.takeIf { !Podcasts.finished(it) }?.positionMs ?: 0L
        val file = podcasts.downloadedFile(episode.id)
        PodcastPlayer.attach(ctx)
        PodcastPlayer.setSpeed(podcasts.playbackSpeed.value)
        PodcastPlayer.setSkipSilence(podcasts.skipSilence.value)
        PodcastPlayer.play(episode, file, start)
        val show = podcasts.show(episode.showId)
        PodcastPlaybackService.start(ctx, show?.title ?: "Podcast", episode.title, show?.artworkUrl.orEmpty())
    }

    fun queueDownload(episode: PodcastEpisode) {
        if (!Podcasts.canQueueDownload(
                episode.id,
                episode.enclosureUrl,
                downloaded = podcasts.downloads.value.keys,
                queued = podcasts.downloadProgress.value.keys,
            )
        ) return
        podcasts.markDownloadQueued(episode.id)
        scope.launch {
            val file = podcasts.download(episode)
            Toast.makeText(
                ctx,
                if (file != null) "Downloaded" else "Download failed",
                Toast.LENGTH_SHORT,
            ).show()
        }
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

    DisposableEffect(Unit) {
        PodcastPlayer.onProgress = { id, pos, dur, done ->
            podcasts.saveProgress(id, pos, dur, done)
            if (done) {
                val current = podcasts.episode(id)
                val next = current?.let {
                    Podcasts.nextEpisode(
                        podcasts.episodesFor(it.showId),
                        id,
                        podcasts.progress.value,
                    )
                }
                if (next != null) {
                    Handler(Looper.getMainLooper()).post {
                        playEpisode(next)
                        if (page == Page.PodcastEpisode) openPodcastEpisode(next.id)
                    }
                }
            }
        }
        onDispose {
            PodcastPlayer.persist()
            PodcastPlayer.onProgress = null
        }
    }

    fun applyPodcastSpeed(speed: Float) {
        podcasts.setPlaybackSpeed(speed)
        PodcastPlayer.setSpeed(speed)
    }

    fun applyPodcastSkipSilence(on: Boolean) {
        podcasts.setSkipSilence(on)
        PodcastPlayer.setSkipSilence(on)
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
        scope.launch { weather.refresh(force = true) }
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
            pick = AppPick.Launch
            return
        }
        val first = line.first()
        if (first == '@' || first == '#') {
            // First token only — body after the name is the message, not a search.
            val needle = line.drop(1).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            people = if (needle.isEmpty()) emptyList() else contacts.search(needle)
            choices = emptyList()
            appQuery = false
            pick = AppPick.Launch
        } else if (PrefixCommands.isModePrompt(first)) {
            people = emptyList()
            choices = emptyList()
            appQuery = false
            pick = AppPick.Launch
        } else {
            people = emptyList()
            val parsed = AppPickQuery.parse(line)
            pick = parsed.pick
            choices = parsed.filter(apps.all(), pins.packages().toSet())
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
                val parsed = Clock.parseTimerInput(text) ?: return false
                clock.setTimer(Clock.setDuration(clock.snapshot().timer, parsed.durationMs, parsed.label))
                ClockScheduler.sync(ctx, clock.snapshot())
                clearBar()
                return true
            }
            ClockTab.Alarm -> {
                val parsed = Clock.parseAlarmInput(text) ?: return false
                clock.addAlarm(parsed.hour, parsed.minute, parsed.label, parsed.days)
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
    LaunchedEffect(nowPlayingRequestCount) {
        if (nowPlayingRequestCount > 0) {
            playback.episodeId?.let { openPodcastEpisode(it) }
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
                val previewTodos = HomeTodos.preview(HomeTodos.of(local), settings.homeTodoCount)
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
                    todosToday = HomeTodos.completedToday(HomeTodos.of(local)),
                    productiveShare = if (usageToday.granted) usageToday.productiveShare else null,
                    onOpenClock = { openClock() },
                    onOpenWeather = { openWeather() },
                    onOpenHub = { openHub() },
                    onOpenTicker = { openStocksList() },
                    onOpenTodos = {
                        taskMode()
                        page = Page.Todos
                    },
                    onOpenUsage = { openUsage() },
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
                val overlayMenus = actionMenuOpen || prompt == SlashCommands.PROMPT
                val filtering = people.isEmpty() && appQuery
                if (!overlayMenus) {
                if (!filtering) {
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
                        choices = emptyList()
                        appQuery = false
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
                                .clickable { openAppsList(keepQuery = true) }
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
                onActionMenuChange = { actionMenuOpen = it },
                )
                        }
                        Page.Hub -> {
                ScreenHeader(
                    title = HubMessages.TITLE,
                    leading = {
                        ScreenBack(HubMessages.BACK, onBack = { page = Page.Home })
                    },
                    trailing = {
                        DeleteIcon(
                            Modifier
                                .semantics { contentDescription = HubMessages.CLEAR_ALL }
                                .clickable { HubStore.clearAll() }
                                .padding(vertical = 6.dp),
                        )
                    },
                )
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
                ScreenHeader(
                    title = Podcasts.COMMAND,
                    leading = {
                        GearIcon(
                            Modifier
                                .semantics { contentDescription = "podcasts settings" }
                                .clickable { page = Page.PodcastSettings }
                                .padding(vertical = 6.dp),
                        )
                    },
                    trailing = {
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
                    },
                )
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
                                        PodcastEpisodeRowActions(
                                            downloaded = podcastDownloads.containsKey(ep.id),
                                            percent = episodeDownloadPercent(ep.id, podcastTransfer),
                                            onDownload = { queueDownload(ep) },
                                            dismissDescription = "dismiss episode",
                                            onDismiss = { skipEpisode(ep) },
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
                                        PodcastEpisodeRowActions(
                                            downloaded = podcastDownloads.containsKey(ep.id),
                                            percent = episodeDownloadPercent(ep.id, podcastTransfer),
                                            onDownload = { queueDownload(ep) },
                                            dismissDescription = "dismiss episode",
                                            onDismiss = { skipEpisode(ep) },
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
                ScreenHeader(
                    title = HomeTodos.TITLE,
                    leading = {
                        ScreenBack(HomeTodos.BACK, onBack = { openHomeDefault() })
                    },
                    trailing = {
                        CopyIcon(
                            Modifier
                                .clickable {
                                    copyText("todos", HomeTodos.shareMarkdown(todos))
                                }
                                .padding(vertical = 6.dp),
                        )
                    },
                )
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
                ScreenHeader(
                    title = Notes.COMMAND,
                    leading = {
                        ScreenBack(Notes.BACK, onBack = { page = Page.Home })
                    },
                )
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
                val notePaper = Paper
                BasicTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    visualTransformation = remember(noteAccent, notePaper) {
                        MarkdownVisualTransformation(noteAccent, notePaper)
                    },
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
                ScreenHeader(
                    title = "chat",
                    leading = {
                        ScreenBack(
                            Chats.BACK,
                            onBack = {
                                page = Page.Home
                                clearBar()
                            },
                        )
                    },
                    trailing = {
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
                    },
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
                            Text(
                                AiFallback.headline(msg.content),
                                color = Dim,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { copyText("fallback error", msg.content) },
                            )
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
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    prompt = '?',
                    value = input,
                    hardware = hardware,
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
                ScreenHeader(
                    title = "chats",
                    leading = {
                        ScreenBack(Chats.BACK, onBack = { page = Page.Chat })
                    },
                )
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
                    val q = AppPickQuery.parse(input).query
                    if (q.isBlank()) apps.all() else apps.search(q)
                }
                ScreenHeader(
                    title = AppList.COMMAND,
                    leading = {
                        ScreenBack(
                            AppList.BACK,
                            onBack = {
                                page = Page.Home
                                if (input.isNotBlank() || PrefixCommands.find(prompt) != null) applyMode(mode())
                            },
                        )
                    },
                )
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
                    todos = HomeTodos.of(local),
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
                    onOpenBackup = { page = Page.BackupSettings },
                    repo = settingsRepo,
                    weather = weather,
                    calendar = calendar,
                    resumeEpoch = appsEpoch,
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
            Page.BackupSettings -> {
                BackupSettingsPage(
                    settings = settings,
                    onBack = { page = Page.Settings },
                    repo = settingsRepo,
                    backup = backup,
                )
            }
            Page.Stocks -> {
                val searching = Stocks.queryFromInput(input).isNotEmpty()
                ScreenHeader(
                    title = Stocks.COMMAND,
                    leading = {
                        ScreenBack(
                            Stocks.BACK,
                            onBack = {
                                applyMode(
                                    PrefixCommands.type(
                                        PrefixCommands.Mode(),
                                        Stocks.leaveDraft(mode().line),
                                    ),
                                )
                                page = Page.Home
                            },
                        )
                    },
                    trailing = {
                        GearIcon(
                            Modifier
                                .semantics { contentDescription = "stocks settings" }
                                .clickable { page = Page.StockSettings }
                                .padding(vertical = 6.dp),
                        )
                    },
                )
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
                        val finished = Podcasts.finished(prog)
                        val inProgress = prog != null && !finished && !skipped
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
                                        Podcasts.episodeListDimmed(skipped, finished) -> Dim
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
                            PodcastEpisodeRowActions(
                                downloaded = podcastDownloads.containsKey(ep.id),
                                percent = episodeDownloadPercent(ep.id, podcastTransfer),
                                onDownload = { queueDownload(ep) },
                                dismissDescription = if (skipped) "restore episode" else "dismiss episode",
                                onDismiss = {
                                    if (skipped) podcasts.setSkipped(ep.id, false, ep.durationMs)
                                    else skipEpisode(ep)
                                },
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
                        val percent = Podcasts.downloadProgressLabel(ep.id, podcastTransfer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (percent.isNotBlank()) {
                                Text(
                                    percent,
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            DownloadIcon(
                                filled = downloaded,
                                modifier = Modifier
                                    .semantics { contentDescription = if (downloaded) "downloaded" else "download" }
                                    .clickable { queueDownload(ep) }
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
                    Text("Skip silence", color = Dim, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf(true, false).forEach { on ->
                            Text(
                                Podcasts.skipSilenceLabel(on),
                                color = if (podcastSkipSilence == on) Accent else Dim,
                                modifier = Modifier.clickable { applyPodcastSkipSilence(on) },
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
