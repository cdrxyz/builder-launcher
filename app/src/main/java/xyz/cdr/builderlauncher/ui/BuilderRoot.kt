@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package xyz.cdr.builderlauncher.ui

import android.content.res.Configuration
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.apps.LaunchableApp
import xyz.cdr.builderlauncher.commands.AppPick
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.commands.CommandParser
import xyz.cdr.builderlauncher.commands.ContactAction
import xyz.cdr.builderlauncher.commands.ExecResult
import xyz.cdr.builderlauncher.contacts.PhoneContact
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.PinnedApps
import xyz.cdr.builderlauncher.data.SettingsRepository
import xyz.cdr.builderlauncher.hub.HubStore
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Prompt
import xyz.cdr.builderlauncher.weather.WeatherPlace
import xyz.cdr.builderlauncher.weather.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Page { Home, Hub, Settings }

@Composable
fun BuilderRoot(
    settingsRepo: SettingsRepository,
    apps: InstalledApps,
    lists: LocalLists,
    pins: PinnedApps,
    contacts: PhoneContacts,
    llm: LlmClient,
    executor: CommandExecutor,
    weather: WeatherRepository,
    onRequestHome: () -> Unit = {},
) {
    val settings by settingsRepo.settings.collectAsState()
    val hub by HubStore.items.collectAsState()
    val local by lists.items.collectAsState()
    val forecast by weather.current.collectAsState()
    var page by remember { mutableStateOf(Page.Home) }
    var input by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var todosExpanded by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var contactAction by remember { mutableStateOf<ContactAction?>(null) }
    var contactBody by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(AppPick.Launch) }
    val pinPkgs by pins.packages.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val hardware = remember(settings.keyboardMode) {
        when (settings.keyboardMode) {
            KeyboardMode.HARDWARE -> true
            KeyboardMode.SOFTWARE -> false
            KeyboardMode.AUTO ->
                ctx.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
        }
    }
    LaunchedEffect(settings.weatherLat, settings.weatherLon) {
        weather.refresh()
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15 * 60 * 1000)
            weather.refresh()
        }
    }

    fun onInput(value: String) {
        input = value
        contactAction = null
        if (value.isBlank()) {
            choices = emptyList()
            people = emptyList()
            return
        }
        val first = value.first()
        if (first == '@' || first == '#') {
            // First token only — body after the name is the message, not a search.
            val needle = value.drop(1).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            people = if (needle.isEmpty()) emptyList() else contacts.search(needle)
            choices = emptyList()
        } else if (first in "*-+?") {
            people = emptyList()
            choices = emptyList()
        } else {
            people = emptyList()
            choices = apps.search(value).take(8)
        }
    }

    fun runCommand(line: String) {
        val result = executor.execute(CommandParser.parse(line))
        when (result) {
            ExecResult.None -> {
                input = ""
                choices = emptyList()
                people = emptyList()
                contactAction = null
                help = false
            }
            ExecResult.ShowHelp -> {
                help = true
                aiText = null
            }
            ExecResult.NavigateSettings -> page = Page.Settings
            ExecResult.NavigateHub -> page = Page.Hub
            is ExecResult.Ask -> {
                help = false
                aiBusy = true
                aiText = "…"
                input = ""
                scope.launch {
                    aiText = llm.ask(result.question)
                    aiBusy = false
                }
            }
            is ExecResult.AppChoices -> {
                choices = result.apps
                pick = result.pick
                people = emptyList()
                help = false
            }
            is ExecResult.ContactChoices -> {
                people = result.contacts
                contactBody = result.body
                contactAction = result.action
                choices = emptyList()
                help = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        when (page) {
            Page.Home -> {
                val todos = HomeTodos.of(local)
                val openTodos = HomeTodos.open(todos)
                val doneTodos = HomeTodos.completed(todos)
                LaunchedEffect(openTodos.size, doneTodos.size, todosExpanded) {
                    if (todosExpanded && openTodos.isEmpty() && doneTodos.isEmpty()) todosExpanded = false
                }
                ClockHeader(
                    weather = forecast?.line,
                    onOpenSettings = { page = Page.Settings },
                )
                Spacer(Modifier.height(8.dp))
                if (!todosExpanded && (openTodos.isNotEmpty() || doneTodos.isNotEmpty())) {
                    TodoPreview(
                        open = HomeTodos.visibleOpen(openTodos, expanded = false),
                        done = HomeTodos.visibleDone(doneTodos, expanded = false),
                        hasMore = HomeTodos.hasMore(openTodos) || HomeTodos.hasMoreDone(doneTodos),
                        onToggle = { lists.toggleComplete(it) },
                        onMore = { todosExpanded = true },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (aiText != null) {
                    Text(if (aiBusy) "…" else aiText!!, color = Prompt, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                if (help) {
                    HelpBlock()
                    Spacer(Modifier.height(12.dp))
                }
                val pinned = remember(pinPkgs, apps.all()) {
                    val all = apps.all()
                    pinPkgs.mapNotNull { pkg -> all.find { it.packageName == pkg } }
                }
                val shown = if (choices.isNotEmpty()) choices else pinned
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (people.isNotEmpty()) {
                        items(people, key = { it.name + it.number }) { person ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val pending = contactAction
                                        if (pending != null) {
                                            executor.applyContact(person, contactBody, pending)
                                            input = ""
                                            people = emptyList()
                                            contactAction = null
                                        } else {
                                            val prefix = if (input.startsWith("#")) "#" else "@"
                                            val body = if (prefix == "@") {
                                                input.drop(1).trim().split(Regex("\\s+"), limit = 2)
                                                    .getOrElse(1) { "" }
                                            } else {
                                                ""
                                            }
                                            input = if (body.isBlank()) {
                                                "$prefix${person.name} "
                                            } else {
                                                "$prefix${person.name} $body"
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
                    } else if (todosExpanded) {
                        items(openTodos, key = { "t" + it.id }) { item ->
                            TodoLine(item, onToggle = { lists.toggleComplete(item.id) })
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
                            TodoLine(item, onToggle = { lists.toggleComplete(item.id) })
                        }
                        item {
                            Text(
                                "show less",
                                color = Dim,
                                modifier = Modifier
                                    .clickable { todosExpanded = false }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    } else {
                        items(shown, key = { it.packageName + it.activityName }) { app ->
                            Text(
                                app.label,
                                color = Paper,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (choices.isNotEmpty()) {
                                                executor.applyPick(app, pick)
                                                input = ""
                                                choices = emptyList()
                                            } else {
                                                apps.launch(app)
                                            }
                                        },
                                        onLongClick = {
                                            if (pins.isPinned(app.packageName)) {
                                                pins.unpin(app.packageName)
                                            } else {
                                                pins.pin(app.packageName)
                                            }
                                        },
                                    )
                                    .padding(vertical = 6.dp),
                            )
                        }
                        if (shown.isEmpty() && input.isBlank() && openTodos.isEmpty() && doneTodos.isEmpty()) {
                            item {
                                Text("Type to work. help for commands. Then put it down.", color = Dim)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandBar(
                    value = input,
                    hardware = hardware,
                    onValue = { onInput(it) },
                    onSubmit = { runCommand(input) },
                    onHub = { page = Page.Hub },
                )
            }
            Page.Hub -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("hub", color = Prompt)
                    Text("home", color = Dim, modifier = Modifier.clickable { page = Page.Home })
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val hubTodos = HomeTodos.of(local)
                    val hubOpen = HomeTodos.open(hubTodos)
                    val hubDone = HomeTodos.completed(hubTodos)
                    val notes = local.filter { !it.kind.equals("todo", ignoreCase = true) }
                    items(hubOpen, key = { "l" + it.id }) { item ->
                        HubLocalRow(item, onTap = { lists.toggleComplete(item.id) })
                    }
                    items(notes, key = { "n" + it.id }) { item ->
                        HubLocalRow(item, onTap = { lists.remove(item.id) })
                    }
                    items(hubDone, key = { "ld" + it.id }) { item ->
                        HubLocalRow(item, onTap = { lists.toggleComplete(item.id) })
                    }
                    items(hub, key = { it.key }) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { HubStore.open(item.key) },
                            ) {
                                Text(item.source, color = Dim, style = MaterialTheme.typography.labelSmall)
                                Text(item.title, color = Paper)
                                if (item.body.isNotBlank()) {
                                    Text(item.body, color = Dim, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text(
                                "dismiss",
                                color = Dim,
                                modifier = Modifier
                                    .clickable { HubStore.dismiss(item.key) }
                                    .padding(start = 12.dp),
                            )
                        }
                    }
                    if (hub.isEmpty() && local.isEmpty()) {
                        item {
                            Text(
                                "Grant notification access in settings to fill the hub. Todos and notes typed with - and + appear here. Hold a todo to remove it. Tap a notification to open it.",
                                color = Dim,
                            )
                        }
                    }
                }
            }
            Page.Settings -> {
                SettingsPage(
                    settings = settings,
                    hardware = hardware,
                    onBack = { page = Page.Home },
                    repo = settingsRepo,
                    weather = weather,
                    onRequestHome = onRequestHome,
                )
            }
        }
    }
}

@Composable
private fun ClockHeader(weather: String?, onOpenSettings: () -> Unit) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(15_000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now.value))
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now.value))
    Column(Modifier.clickable { onOpenSettings() }) {
        Text(time, style = MaterialTheme.typography.headlineLarge)
        Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
        if (!weather.isNullOrBlank()) {
            Text(weather, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TodoPreview(
    open: List<LocalItem>,
    done: List<LocalItem>,
    hasMore: Boolean,
    onToggle: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        open.forEach { item ->
            TodoLine(item, onToggle = { onToggle(item.id) }, compact = true)
        }
        if (hasMore) {
            Text(
                "…more todos",
                color = Prompt,
                modifier = Modifier
                    .clickable { onMore() }
                    .padding(vertical = 4.dp),
            )
        }
        if (done.isNotEmpty()) {
            Text(
                "done",
                color = Dim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            done.forEach { item ->
                TodoLine(item, onToggle = { onToggle(item.id) }, compact = true)
            }
        }
    }
}

@Composable
private fun TodoLine(item: LocalItem, onToggle: () -> Unit, compact: Boolean = false) {
    Text(
        item.text,
        color = if (item.done) Dim else Paper,
        style = MaterialTheme.typography.bodyLarge.copy(
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = if (compact) 4.dp else 6.dp),
    )
}

@Composable
private fun HubLocalRow(item: LocalItem, onTap: () -> Unit) {
    Column(Modifier.clickable { onTap() }) {
        Text(item.kind, color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(
            item.text,
            color = if (item.done) Dim else Paper,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
    }
}

@Composable
private fun CommandBar(
    value: String,
    hardware: Boolean,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    onHub: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(hardware) {
        focus.requestFocus()
        if (hardware) keyboard?.hide()
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(">", color = Prompt, modifier = Modifier.padding(end = 10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            cursorBrush = SolidColor(Prompt),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onSubmit()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onHub()
                            true
                        }
                        else -> false
                    }
                },
        )
    }
    HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun HelpBlock() {
    val lines = listOf(
        "@name message   text",
        "#name           call",
        "*title when     calendar",
        "-todo           save todo",
        "+note           save note",
        "?question       ask AI",
        "pin Termux      pin an app",
        "unpin Termux    unpin",
        "hub / settings",
        "type a name     launch app",
        "hold an app     pin or unpin",
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
    repo: SettingsRepository,
    weather: WeatherRepository,
    onRequestHome: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hermes by remember { mutableStateOf(settings.hermesBaseUrl) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var placeQuery by remember { mutableStateOf(settings.weatherPlace) }
    var suggestions by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
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
            Text("settings", color = Prompt)
            Text("home", color = Dim, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(16.dp))
        Text("AI provider", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Hermes",
                color = if (settings.provider == LlmProvider.HERMES) Prompt else Dim,
                modifier = Modifier.clickable {
                    repo.update { it.copy(provider = LlmProvider.HERMES) }
                },
            )
            Text(
                "xAI / SuperGrok",
                color = if (settings.provider == LlmProvider.XAI) Prompt else Dim,
                modifier = Modifier.clickable {
                    repo.update { it.copy(provider = LlmProvider.XAI) }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (settings.provider == LlmProvider.HERMES) {
            LabeledField("Hermes base URL", hermes, "http://192.168.1.10:8642") {
                hermes = it
                repo.update { s -> s.copy(hermesBaseUrl = it) }
            }
        } else {
            Text("Uses https://api.x.ai/v1 — paste a SuperGrok / xAI API key below.", color = Dim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        LabeledField("API key (stored on device)", key, "optional for local Hermes") {
            key = it
            repo.update { s -> s.copy(apiKey = it) }
        }
        LabeledField("Model", model, if (settings.provider == LlmProvider.XAI) "grok-4.6" else "default") {
            model = it
            repo.update { s -> s.copy(model = it) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Keyboard", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            KeyboardMode.entries.forEach { mode ->
                Text(
                    mode.name.lowercase(),
                    color = if (settings.keyboardMode == mode) Prompt else Dim,
                    modifier = Modifier.clickable { repo.update { it.copy(keyboardMode = mode) } },
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
            "Set as default home app",
            color = Paper,
            modifier = Modifier.clickable { onRequestHome() },
        )
        Spacer(Modifier.height(24.dp))
        Text("Keys never leave the device except as a Bearer token to the URL you set.", color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LabeledField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        cursorBrush = SolidColor(Prompt),
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
        cursorBrush = SolidColor(Prompt),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
        decorationBox = { inner ->
            if (query.isEmpty()) Text("Kitchener, Ontario", color = Dim, style = MaterialTheme.typography.bodyMedium)
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
