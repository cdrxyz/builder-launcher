@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package xyz.cdr.builderlauncher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.cdr.builderlauncher.ai.AiPlatforms
import xyz.cdr.builderlauncher.ai.LlmClient
import xyz.cdr.builderlauncher.ai.OAuthSpec
import xyz.cdr.builderlauncher.ai.oauth.DevicePending
import xyz.cdr.builderlauncher.ai.oauth.OAuthService
import xyz.cdr.builderlauncher.ai.oauth.PkceSession
import xyz.cdr.builderlauncher.apps.InstalledApps
import xyz.cdr.builderlauncher.apps.LaunchableApp
import xyz.cdr.builderlauncher.commands.AppPick
import xyz.cdr.builderlauncher.commands.CommandExecutor
import xyz.cdr.builderlauncher.commands.CommandParser
import xyz.cdr.builderlauncher.commands.ContactAction
import xyz.cdr.builderlauncher.commands.ExecResult
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.contacts.PhoneContact
import xyz.cdr.builderlauncher.contacts.PhoneContacts
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.data.LocalLists
import xyz.cdr.builderlauncher.data.Notes
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

enum class Page { Home, Todos, Notes, NoteEditor, Hub, Settings }

@Composable
fun BuilderRoot(
    settingsRepo: SettingsRepository,
    apps: InstalledApps,
    lists: LocalLists,
    pins: PinnedApps,
    contacts: PhoneContacts,
    llm: LlmClient,
    oauth: OAuthService,
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
    var people by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var contactAction by remember { mutableStateOf<ContactAction?>(null) }
    var contactBody by remember { mutableStateOf("") }
    var smsDraft by remember { mutableStateOf<ExecResult.SmsDraft?>(null) }
    var pick by remember { mutableStateOf(AppPick.Launch) }
    var noteId by remember { mutableStateOf<String?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var noteFromList by remember { mutableStateOf(false) }
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

    fun openNoteEditor(id: String?, draft: String, fromList: Boolean) {
        noteId = id
        noteDraft = if (id == null) Notes.headingDraft(draft) else draft
        noteFromList = fromList
        input = ""
        choices = emptyList()
        people = emptyList()
        page = Page.NoteEditor
    }

    fun openNotesList() {
        input = ""
        choices = emptyList()
        people = emptyList()
        page = Page.Notes
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

    fun onInput(value: String) {
        if (value.startsWith(Notes.PREFIX) && page != Page.NoteEditor) {
            openNoteEditor(id = null, draft = Notes.draftFromInput(value), fromList = false)
            return
        }
        input = value
        contactAction = null
        if (value.isNotBlank() && !value.equals("send", ignoreCase = true)) {
            smsDraft = null
        }
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
        if (page == Page.Todos) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == HomeTodos.TASK_PREFIX) {
                input = HomeTodos.enterDraft()
                return
            }
        }
        val draft = smsDraft
        if (draft != null) {
            if (line.isBlank() || line.equals("send", ignoreCase = true)) {
                executor.sendSms(draft.contact, draft.body)
            }
            smsDraft = null
            input = ""
            people = emptyList()
            return
        }
        val result = executor.execute(CommandParser.parse(line))
        when (result) {
            ExecResult.None -> {
                input = ""
                choices = emptyList()
                people = emptyList()
                contactAction = null
                smsDraft = null
                help = false
            }
            ExecResult.ShowHelp -> {
                help = true
                aiText = null
            }
            ExecResult.NavigateSettings -> page = Page.Settings
            ExecResult.NavigateHub -> page = Page.Hub
            ExecResult.NavigateNotes -> openNotesList()
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
            is ExecResult.SmsDraft -> {
                smsDraft = result
                input = ""
                people = emptyList()
                contactAction = null
                help = false
            }
        }
        if (page == Page.Todos && input.isBlank()) {
            input = HomeTodos.keepDraft(input)
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
                val previewTodos = HomeTodos.preview(HomeTodos.of(local))
                ClockHeader(
                    weather = forecast?.line,
                    onOpenSettings = { page = Page.Settings },
                )
                Spacer(Modifier.height(8.dp))
                TodoPreview(
                    open = previewTodos,
                    onToggle = { lists.toggleComplete(it) },
                    onMore = {
                        input = HomeTodos.enterDraft()
                        page = Page.Todos
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (aiText != null) {
                    Text(if (aiBusy) "…" else aiText!!, color = Prompt, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                smsDraft?.let { draft ->
                    Text(
                        "Send to ${draft.contact.name} (${draft.contact.number})",
                        color = Prompt,
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
                                            when (val result = executor.applyContact(person, contactBody, pending)) {
                                                is ExecResult.SmsDraft -> {
                                                    smsDraft = result
                                                    input = ""
                                                }
                                                else -> {
                                                    input = ""
                                                }
                                            }
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
                    } else {
                        if (Notes.matchesQuery(input)) {
                            item(key = "all-notes") {
                                Text(
                                    Notes.MORE,
                                    color = Prompt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openNotesList() }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
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
                        if (shown.isEmpty() && input.isBlank()) {
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
                        color = Prompt,
                        modifier = Modifier
                            .clickable {
                                input = HomeTodos.leaveDraft(input)
                                page = Page.Home
                            }
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
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Page.Notes -> {
                val notes = Notes.of(local)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Notes.BACK,
                        color = Prompt,
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
                        color = Prompt,
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
                BasicTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    visualTransformation = MarkdownVisualTransformation,
                    cursorBrush = SolidColor(Prompt),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            Page.Hub -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("hub", color = Prompt)
                    Text("home", color = Dim, modifier = Modifier.clickable { page = Page.Home })
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    if (hub.isEmpty()) {
                        item {
                            Text(
                                "Grant notification access in settings to fill the hub. Tap a notification to open it, or dismiss.",
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
                    oauth = oauth,
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
    onToggle: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        open.forEach { item ->
            TodoLine(item, onToggle = { onToggle(item.id) }, compact = true)
        }
        Text(
            HomeTodos.MORE_TASKS,
            color = Prompt,
            modifier = Modifier
                .clickable { onMore() }
                .padding(vertical = 4.dp),
        )
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
private fun CommandBar(
    value: String,
    hardware: Boolean,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    onHub: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    LaunchedEffect(hardware) {
        focus.requestFocus()
        if (hardware) keyboard?.hide()
    }
    fun pick(index: Int) {
        val cmd = PrefixCommands.all.getOrNull(index) ?: return
        menuOpen = false
        selected = 0
        onValue(PrefixCommands.fill(cmd.glyph))
        focus.requestFocus()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (menuOpen) {
            CommandMenu(
                selected = selected,
                onSelect = { cmd ->
                    val index = PrefixCommands.all.indexOf(cmd)
                    if (index >= 0) pick(index)
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                ">",
                color = Prompt,
                modifier = Modifier
                    .semantics { contentDescription = "commands" }
                    .clickable {
                        menuOpen = !menuOpen
                        if (menuOpen) selected = 0
                    }
                    .padding(end = 10.dp),
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
                        val code = event.nativeKeyEvent.keyCode
                        val ch = event.nativeKeyEvent.unicodeChar.toChar()
                        if (menuOpen) {
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
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
    }
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
        "?question       ask AI",
        "pin Termux      pin an app",
        "unpin Termux    unpin",
        "hub / notes / settings",
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
    oauth: OAuthService,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = AiPlatforms.of(settings.provider)
    var hermes by remember { mutableStateOf(settings.hermesBaseUrl) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var placeQuery by remember { mutableStateOf(settings.weatherPlace) }
    var suggestions by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var paste by remember { mutableStateOf("") }
    var oauthMsg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DevicePending?>(null) }
    var pkce by remember { mutableStateOf<PkceSession?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(placeQuery, settings.weatherPlace, settings.weatherLat) {
        val q = placeQuery.trim()
        if (q.length < 2 || (q == settings.weatherPlace && settings.weatherLat != null)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(280)
        suggestions = weather.suggest(q)
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
            Text("settings", color = Prompt)
            Text("home", color = Dim, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(16.dp))
        Text("AI provider", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            AiPlatforms.all.take(2).forEach { item ->
                Text(
                    item.label,
                    color = if (settings.provider == item.provider) Prompt else Dim,
                    modifier = Modifier.clickable {
                        cancelAuth()
                        repo.setProvider(item.provider)
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            AiPlatforms.all.drop(2).forEach { item ->
                Text(
                    item.label,
                    color = if (settings.provider == item.provider) Prompt else Dim,
                    modifier = Modifier.clickable {
                        cancelAuth()
                        repo.setProvider(item.provider)
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (settings.provider == LlmProvider.HERMES) {
            LabeledField("Hermes base URL", hermes, "http://192.168.1.10:8642") {
                hermes = it
                repo.update { s -> s.copy(hermesBaseUrl = it) }
            }
        } else {
            Text(
                "${platform.apiBase} — sign in or paste an API key. Used only for ? questions.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
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
                                    val next = oauth.beginDevice(settings.provider)
                                    pending = next
                                    pkce = null
                                    oauth.browserUrl(next, settings.provider)?.let { openHttps(it) }
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
        }
        LabeledField("API key (stored on device)", key, if (settings.provider == LlmProvider.HERMES) "optional for local Hermes" else "optional if signed in") {
            key = it
            repo.update { s -> s.copy(apiKey = it) }
        }
        LabeledField("Model", model, platform.defaultModel) {
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
        Text("Tokens stay on the device. They are sent only as a Bearer token to the provider you chose.", color = Dim, style = MaterialTheme.typography.bodyMedium)
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
    val signInLabel = when (settings.provider) {
        LlmProvider.XAI -> "Sign in with SuperGrok"
        LlmProvider.OPENAI -> "Sign in with ChatGPT"
        LlmProvider.ANTHROPIC -> "Sign in with Claude"
        LlmProvider.HERMES -> "Sign in"
    }
    if (settings.signedIn) {
        Text(
            if (settings.oauthAccount.isNotBlank()) "Signed in as ${settings.oauthAccount}" else "Signed in with $platformLabel",
            color = Paper,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("Sign out", color = Prompt, modifier = Modifier.clickable { onSignOut() })
    } else {
        Text(signInLabel, color = Paper, modifier = Modifier.clickable { onSignIn() })
    }
    pending?.let {
        Spacer(Modifier.height(8.dp))
        Text("Enter this code in the browser", color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(it.userCode, color = Prompt, style = MaterialTheme.typography.headlineSmall)
        Text("Waiting for approval…", color = Dim, style = MaterialTheme.typography.bodyMedium)
    }
    if (pkce != null) {
        LabeledField("Paste code or callback URL", paste, "code from the page") { onPaste(it) }
        Text("Finish sign-in", color = Prompt, modifier = Modifier.clickable { onCompletePaste() }.padding(vertical = 8.dp))
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
