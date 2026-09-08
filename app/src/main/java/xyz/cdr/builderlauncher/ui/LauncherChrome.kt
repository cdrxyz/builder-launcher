package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Prompt

data class HubRow(
    val kind: String,
    val title: String,
    val body: String = "",
)

@Composable
fun HomeChrome(
    time: String,
    date: String,
    input: String,
    weather: String = "",
    todos: List<String> = emptyList(),
    doneTodos: List<String> = emptyList(),
    todosExpanded: Boolean = false,
    apps: List<String> = emptyList(),
    hint: String = "Type to work. help for commands. Then put it down.",
    commandsOpen: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(time, style = MaterialTheme.typography.headlineLarge, color = Paper)
        Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
        if (weather.isNotBlank()) {
            Text(weather, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (!todosExpanded) {
            todos.take(HomeTodos.PREVIEW).forEach { text ->
                Text(text, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            Text(HomeTodos.MORE_TASKS, color = Prompt, modifier = Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(8.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (todosExpanded) {
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
                Text("show less", color = Dim, modifier = Modifier.padding(vertical = 6.dp))
            } else if (apps.isEmpty() && input.isBlank()) {
                Text(hint, color = Dim)
            } else {
                apps.forEach { label ->
                    Text(label, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CommandRow(input, commandsOpen = commandsOpen)
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
            Text("hub", color = Prompt)
            Text("home", color = Dim)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) {
                Text(
                    "Grant notification access in settings to fill the hub. Todos and notes typed with - and + appear here.",
                    color = Dim,
                )
            } else {
                rows.forEach { row ->
                    Column {
                        Text(row.kind, color = Dim, style = MaterialTheme.typography.labelSmall)
                        Text(row.title, color = Paper)
                        if (row.body.isNotBlank()) {
                            Text(row.body, color = Dim, style = MaterialTheme.typography.bodyMedium)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("settings", color = Prompt)
            Text("home", color = Dim)
        }
        Spacer(Modifier.height(16.dp))
        Text("AI provider", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Hermes", color = if (settings.provider == LlmProvider.HERMES) Prompt else Dim)
            Text("xAI", color = if (settings.provider == LlmProvider.XAI) Prompt else Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Text("OpenAI", color = if (settings.provider == LlmProvider.OPENAI) Prompt else Dim)
            Text("Anthropic", color = if (settings.provider == LlmProvider.ANTHROPIC) Prompt else Dim)
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
                    color = if (settings.keyboardMode == mode) Prompt else Dim,
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
private fun CommandRow(value: String, commandsOpen: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (commandsOpen) {
            CommandMenu()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(">", color = Prompt, modifier = Modifier.padding(end = 10.dp))
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
