package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.usage.Usage
import xyz.cdr.builderlauncher.usage.UsageApp
import xyz.cdr.builderlauncher.usage.UsageDay
import xyz.cdr.builderlauncher.usage.UsageKind
import xyz.cdr.builderlauncher.usage.UsagePeriod
import xyz.cdr.builderlauncher.usage.UsageSnapshot

@Composable
fun UsageScreen(
    snapshot: UsageSnapshot,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPeriod: (UsagePeriod) -> Unit,
    onGrant: () -> Unit,
    onCycleApp: (String) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("usage", color = Accent)
            Text(
                Usage.BACK,
                color = Accent,
                modifier = Modifier.clickable { onBack() }.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!snapshot.granted) {
            Text(
                "Grant usage access to see how much time you spend in apps.",
                color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Open usage access",
                color = Accent,
                modifier = Modifier
                    .clickable { onGrant() }
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = "open usage access" },
            )
        } else {
            UsageBody(
                snapshot = snapshot,
                onPeriod = onPeriod,
                onCycleApp = onCycleApp,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
fun UsageBody(
    snapshot: UsageSnapshot,
    modifier: Modifier = Modifier,
    onPeriod: (UsagePeriod) -> Unit = {},
    onCycleApp: (String) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UsagePeriod.entries.forEach { period ->
                Text(
                    if (period == UsagePeriod.TODAY) "today" else "7 days",
                    color = if (snapshot.period == period) Accent else Dim,
                    modifier = Modifier
                        .clickable { onPeriod(period) }
                        .padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Usage.formatDuration(snapshot.totalMs), style = MaterialTheme.typography.headlineLarge, color = Paper)
        Text(Usage.vsLabel(snapshot.vsYesterdayMs), color = Dim, style = MaterialTheme.typography.bodyMedium)
        if (snapshot.pickups > 0) {
            Text(
                "${snapshot.pickups} pickups",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        UsageWeekChart(days = snapshot.days)
        Spacer(Modifier.height(16.dp))
        UsageShare(label = "productive", ms = snapshot.productiveMs, share = snapshot.productiveShare, color = Accent)
        UsageShare(label = "distracting", ms = snapshot.distractingMs, share = snapshot.distractingShare, color = Loss)
        UsageShare(label = "other", ms = snapshot.otherMs, share = snapshot.otherShare, color = Dim)
        Spacer(Modifier.height(16.dp))
        Text("most used", color = Dim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        snapshot.apps.forEach { app ->
            UsageAppRow(app = app, onCycle = { onCycleApp(app.packageName) })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap an app to mark it productive, distracting, or other.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UsageShare(label: String, ms: Long, share: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label  $share%", color = color, style = MaterialTheme.typography.bodyMedium)
        Text(Usage.formatDuration(ms), color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UsageAppRow(app: UsageApp, onCycle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCycle() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(app.label, color = Paper, style = MaterialTheme.typography.bodyLarge)
            Text(app.kind.label, color = kindColor(app.kind), style = MaterialTheme.typography.bodyMedium)
        }
        Text(Usage.formatDuration(app.millis), color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UsageWeekChart(days: List<UsageDay>, modifier: Modifier = Modifier) {
    val max = days.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val accent = Accent
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "usage chart" },
    ) {
        if (days.isEmpty()) return@Canvas
        val gap = 8.dp.toPx()
        val bar = ((size.width - gap * (days.size - 1)) / days.size).coerceAtLeast(4.dp.toPx())
        days.forEachIndexed { index, day ->
            val x = index * (bar + gap)
            var y = size.height
            fun stack(ms: Long, color: Color) {
                if (ms <= 0L) return
                val h = (ms.toFloat() / max.toFloat()) * size.height
                y -= h
                drawRect(color = color, topLeft = Offset(x, y), size = Size(bar, h))
            }
            stack(day.otherMs, Dim)
            stack(day.productiveMs, accent)
            stack(day.distractingMs, Loss)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Text(day.weekday, color = Dim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun kindColor(kind: UsageKind): Color = when (kind) {
    UsageKind.PRODUCTIVE -> Accent
    UsageKind.DISTRACTING -> Loss
    UsageKind.OTHER -> Dim
}
