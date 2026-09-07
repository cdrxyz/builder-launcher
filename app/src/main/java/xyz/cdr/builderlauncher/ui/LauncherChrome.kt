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
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.data.BuilderSettings
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
    barAtBottom: Boolean = true,
    apps: List<String> = emptyList(),
    hint: String = "Type to work. help for commands. Then put it down.",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (!barAtBottom) {
            CommandRow(input)
            Spacer(Modifier.height(16.dp))
        }
        Text(time, style = MaterialTheme.typography.headlineLarge, color = Paper)
        Text(date, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (apps.isEmpty() && input.isBlank()) {
            Text(hint, color = Dim)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.forEach { label ->
                Text(label, color = Paper, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
        }
        if (barAtBottom) {
            Spacer(Modifier.height(8.dp))
            CommandRow(input)
        }
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
            Text("xAI / SuperGrok", color = if (settings.provider == LlmProvider.XAI) Prompt else Dim)
        }
        if (settings.provider == LlmProvider.HERMES) {
            Field("Hermes base URL", hermes, "http://192.168.1.10:8642")
        } else {
            Text(
                "Uses https://api.x.ai/v1 — paste a SuperGrok / xAI API key below.",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        Field("API key (stored on device)", apiKey, "optional for local Hermes")
        Field("Model", model, if (settings.provider == LlmProvider.XAI) "grok-4.6" else "default")
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
                "Hardware keyboard detected — command bar sits at the bottom."
            } else {
                "Slab mode — command bar at the top, software keyboard allowed."
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
            "Keys never leave the device except as a Bearer token to the URL you set.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CommandRow(value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
