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
import xyz.cdr.builderlauncher.data.UiTheme
import xyz.cdr.builderlauncher.data.UiTone
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
import xyz.cdr.builderlauncher.ui.theme.FieldRule
import xyz.cdr.builderlauncher.ui.theme.inputChrome
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
internal fun SettingsPage(
    settings: BuilderSettings,
    hardware: Boolean,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenBackup: () -> Unit,
    repo: SettingsRepository,
    weather: WeatherRepository,
    calendar: CalendarRepository,
    resumeEpoch: Int,
    onRequestHome: () -> Unit,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var placeQuery by remember { mutableStateOf(settings.weatherPlace) }
    var suggestions by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var accentDraft by remember { mutableStateOf(settings.accentHex) }
    LaunchedEffect(placeQuery, settings.weatherPlace, settings.weatherLat) {
        val q = placeQuery.trim()
        if (q.length < 2 || (q == settings.weatherPlace && settings.weatherLat != null)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(280)
        suggestions = weather.suggest(q)
    }

    if (calendarOpen) {
        BackHandler { calendarOpen = false }
        CalendarSettingsPage(
            settings = settings,
            onBack = { calendarOpen = false },
            repo = repo,
            calendar = calendar,
            resumeEpoch = resumeEpoch,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "settings",
            leading = {},
            trailing = {
                Text("home", color = Dim, modifier = Modifier.clickable { onBack() })
            },
        )
        Spacer(Modifier.height(16.dp))
        Text("Theme", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            UiTheme.entries.forEach { theme ->
                Text(
                    theme.label,
                    color = if (settings.uiTheme == theme) Accent else Dim,
                    modifier = Modifier.clickable {
                        val accent = theme.defaultAccentHex(settings.uiTone)
                        accentDraft = accent
                        repo.update { it.copy(uiTheme = theme, accentHex = accent) }
                    },
                )
            }
        }
        Text(
            settings.uiTheme.blurb,
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text("Tone", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            UiTone.entries.forEach { tone ->
                Text(
                    tone.label,
                    color = if (settings.uiTone == tone) Accent else Dim,
                    modifier = Modifier.clickable {
                        val accent = settings.uiTheme.defaultAccentHex(tone)
                        accentDraft = accent
                        repo.update { it.copy(uiTone = tone, accentHex = accent) }
                    },
                )
            }
        }
        Text(
            if (settings.uiTone == UiTone.LIGHT) "Light surfaces." else "Dark surfaces.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text("Pin time", color = Dim, style = MaterialTheme.typography.bodyMedium)
            listOf(false to "off", true to "on").forEach { (on, label) ->
                Text(
                    label,
                    color = if (settings.pinUsage == on) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(pinUsage = on) } },
                )
            }
        }
        Text(
            "Minutes today. Names: 30m (17%) beside the pin. Icons: 30m then 17% under the icon. Green if productive, red if not.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(HomeTodos.HOME_SETTING, color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            HomeTodos.previewChoices().forEach { count ->
                Text(
                    count.toString(),
                    color = if (settings.homeTodoCount == count) Accent else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(homeTodoCount = count) } },
                )
            }
        }
        Text(
            HomeTodos.HOME_SETTING_BLURB,
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
                scope.launch { weather.refresh(force = true) }
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
        Spacer(Modifier.height(16.dp))
        CaretLink(
            "… calendar >",
            modifier = Modifier
                .clickable { calendarOpen = true }
                .padding(vertical = 6.dp)
                .fillMaxWidth(),
        )
        Text(
            if (calendar.hasPermission()) "Calendar access: granted" else "Calendar access: not granted",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        CaretLink(
            "… backup >",
            modifier = Modifier
                .clickable { onOpenBackup() }
                .padding(vertical = 6.dp)
                .fillMaxWidth(),
        )
        Text(
            "Last backup: ${BackupService.lastBackupLabel(settings.lastBackupAtEpochMs)}",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
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
internal fun CalendarHomeSync(
    calendar: CalendarRepository,
    settings: BuilderSettings,
    appsEpoch: Int,
) {
    val scope = rememberCoroutineScope()
    val selection = CalendarSelection(settings.calendarRestrict, settings.calendarIds)
    val selectionState = rememberUpdatedState(selection)
    DisposableEffect(calendar) {
        var job: Job? = null
        val stop = calendar.observe {
            job?.cancel()
            job = scope.launch {
                delay(400)
                calendar.refresh(selection = selectionState.value)
            }
        }
        onDispose {
            job?.cancel()
            stop()
        }
    }
    LaunchedEffect(appsEpoch, settings.calendarRestrict, settings.calendarIds) {
        calendar.refresh(selection = CalendarSelection(settings.calendarRestrict, settings.calendarIds))
    }
}

@Composable
internal fun CalendarSettingsPage(
    settings: BuilderSettings,
    onBack: () -> Unit,
    repo: SettingsRepository,
    calendar: CalendarRepository,
    resumeEpoch: Int,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val upcoming by calendar.current.collectAsState()
    var granted by remember { mutableStateOf(calendar.hasPermission()) }
    var listed by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    val selection = CalendarSelection(settings.calendarRestrict, settings.calendarIds)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        granted = calendar.hasPermission()
        scope.launch {
            listed = calendar.calendars()
            calendar.refresh(selection = selection)
        }
    }
    LaunchedEffect(resumeEpoch, settings.calendarRestrict, settings.calendarIds) {
        granted = calendar.hasPermission()
        listed = calendar.calendars()
        calendar.refresh(selection = CalendarSelection(settings.calendarRestrict, settings.calendarIds))
    }
    val event = upcoming
    val homeLine = when {
        !granted -> "Home next event stays hidden until access is granted."
        event == null -> "Home: no upcoming event in the next 14 days."
        else -> "Home: ${UpcomingEvents.line(event, System.currentTimeMillis())}"
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "calendar",
            leading = { ScreenBack(onBack = onBack) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Choose which calendars feed the next event under the home clock. Unchecked calendars stay off home even if they are on in the system calendar app.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (granted) "Calendar access: granted" else "Calendar access: not granted",
            color = if (granted) Accent else Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) {
            Text(
                "Grant calendar access",
                color = Paper,
                modifier = Modifier
                    .clickable { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
                    .padding(vertical = 8.dp),
            )
            Text(
                "Open Android settings",
                color = Paper,
                modifier = Modifier
                    .clickable {
                        runCatching { ctx.startActivity(calendar.appSettingsIntent()) }
                    }
                    .padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(homeLine, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("Include on home", color = Dim, style = MaterialTheme.typography.labelSmall)
        if (!granted) {
            Text(
                "Grant access to list calendars on this phone.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else if (listed.isEmpty()) {
            Text(
                "No calendars on this phone.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            listed.forEach { item ->
                val on = selection.checked(item)
                Text(
                    if (on) "[x] ${item.name}" else "[ ] ${item.name}",
                    color = Paper,
                    modifier = Modifier
                        .clickable {
                            val next = selection.toggle(item.id, listed)
                            repo.update { it.copy(calendarRestrict = next.restrict, calendarIds = next.ids) }
                            scope.launch { calendar.refresh(selection = next) }
                        }
                        .padding(top = 8.dp),
                )
                if (item.account.isNotBlank() && item.account != item.name) {
                    Text(
                        item.account,
                        color = Dim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackupSettingsPage(
    settings: BuilderSettings,
    onBack: () -> Unit,
    repo: SettingsRepository,
    backup: BackupService,
) {
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "backup",
            leading = { ScreenBack(onBack = onBack) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "S3-compatible snapshot (R2, AWS, B2, MinIO). Encrypted on the phone before upload. Restore replaces todos, notes, chats, pins, stocks, podcasts, alarms, and settings. OAuth tokens stay on this phone.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
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
    }
}

@Composable
internal fun AiProvidersPage(
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
        ScreenHeader(
            title = "ai",
            leading = { ScreenBack(onBack = onBack) },
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
internal fun OauthBlock(
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
internal fun LabeledField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
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
            .inputChrome()
            .padding(vertical = 6.dp),
    )
    FieldRule()
}

@Composable
internal fun WeatherLocationField(
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
            .inputChrome()
            .padding(vertical = 6.dp),
    )
    FieldRule()
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
