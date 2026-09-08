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
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.data.Notes
import xyz.cdr.builderlauncher.ui.theme.Dim
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
                        color = if (label == Notes.MORE || label == AppList.MORE) Accent else Paper,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, commandsOpen = commandsOpen, prompt = prompt)
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
                Text(text, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            if (doneTodos.isNotEmpty()) {
                Text(
                    "done",
                    color = Dim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                doneTodos.forEach { text ->
                    Text(
                        text,
                        color = Dim,
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
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
private fun CommandRow(value: String, commandsOpen: Boolean = false, prompt: String = ">") {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (commandsOpen) {
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
        val w = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = size.minDimension * 0.12f
        val tip = Offset(pad, size.height * 0.28f)
        val shaft = Path().apply {
            moveTo(size.width - pad, size.height - pad)
            lineTo(size.width * 0.46f, size.height - pad)
            quadraticTo(pad, size.height - pad, pad, size.height * 0.48f)
            lineTo(tip.x, tip.y)
        }
        drawPath(shaft, color = accent, style = w)
        drawLine(
            color = accent,
            start = tip,
            end = Offset(pad + size.width * 0.30f, pad),
            strokeWidth = w.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = tip,
            end = Offset(pad + size.width * 0.30f, tip.y + size.height * 0.22f),
            strokeWidth = w.width,
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
