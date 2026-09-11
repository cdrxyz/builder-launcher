package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.LocalTokens
import xyz.cdr.builderlauncher.ui.theme.Loss
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.ThemedList
import xyz.cdr.builderlauncher.ui.theme.ThemedRow
import xyz.cdr.builderlauncher.ui.theme.ThemedSegmented
import xyz.cdr.builderlauncher.data.HomeTodos
import xyz.cdr.builderlauncher.data.LocalItem
import xyz.cdr.builderlauncher.usage.Usage
import xyz.cdr.builderlauncher.usage.UsageApp
import xyz.cdr.builderlauncher.usage.UsageBar
import xyz.cdr.builderlauncher.usage.UsageKind
import xyz.cdr.builderlauncher.usage.UsagePeriod
import xyz.cdr.builderlauncher.usage.UsageSnapshot

@Composable
fun UsageScreen(
    snapshot: UsageSnapshot,
    modifier: Modifier = Modifier,
    todos: List<LocalItem> = emptyList(),
    onBack: () -> Unit,
    onPeriod: (UsagePeriod) -> Unit,
    onGrant: () -> Unit,
    onCycleApp: (String) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        ScreenHeader(
            title = Usage.COMMAND,
            leading = { ScreenBack(Usage.BACK, onBack = onBack) },
        )
        Spacer(Modifier.height(12.dp))
        if (!snapshot.granted) {
            Column(
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                UsageCompletedSection(snapshot = snapshot, todos = todos, selectedIndex = null)
            }
        } else {
            UsageBody(
                snapshot = snapshot,
                todos = todos,
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
    todos: List<LocalItem> = emptyList(),
    onPeriod: (UsagePeriod) -> Unit = {},
    onCycleApp: (String) -> Unit = {},
    selectedIndex: Int? = null,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(snapshot.period) { scrub = null }
    val selected = selectedIndex ?: scrub
    val bar = selected?.let { snapshot.bars.getOrNull(it) }
    val total = bar?.totalMs ?: snapshot.totalMs
    val productive = bar?.productiveMs ?: snapshot.productiveMs
    val distracting = bar?.distractingMs ?: snapshot.distractingMs
    val other = bar?.otherMs ?: snapshot.otherMs
    val apps = bar?.apps ?: snapshot.apps
    Column(modifier.fillMaxWidth()) {
        ThemedSegmented(
            labels = UsagePeriod.entries.map { it.label },
            selected = UsagePeriod.entries.indexOf(snapshot.period).coerceAtLeast(0),
            onSelect = { onPeriod(UsagePeriod.entries[it]) },
        )
        Spacer(Modifier.height(8.dp))
        Text(Usage.formatDuration(total), style = MaterialTheme.typography.headlineLarge, color = Paper)
        val subtitle = bar?.detail ?: Usage.vsLabel(snapshot.vsLastWeekMs)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (bar == null && snapshot.pickups > 0) {
            Text(
                "${snapshot.pickups} pickups",
                color = Dim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        UsageChart(
            bars = snapshot.bars,
            period = snapshot.period,
            selectedIndex = selected,
            onSelect = { scrub = it },
        )
        Spacer(Modifier.height(16.dp))
        ThemedList {
            UsageShare(label = "productive", ms = productive, share = Usage.percent(productive, total), color = Accent, last = false)
            UsageShare(label = "distracting", ms = distracting, share = Usage.percent(distracting, total), color = Loss, last = false)
            UsageShare(label = "other", ms = other, share = Usage.percent(other, total), color = Dim, last = true)
        }
        Spacer(Modifier.height(16.dp))
        Text("most used", color = Dim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        ThemedList {
            apps.forEachIndexed { index, app ->
                UsageAppRow(app = app, onCycle = { onCycleApp(app.packageName) }, last = index == apps.lastIndex)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Drag a bar to read that day and its apps. Tap an app to mark it productive, distracting, or other.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        UsageCompletedSection(snapshot = snapshot, todos = todos, selectedIndex = selected)
    }
}

@Composable
internal fun UsageCompletedSection(
    snapshot: UsageSnapshot,
    todos: List<LocalItem>,
    selectedIndex: Int?,
) {
    val zone = java.time.ZoneId.systemDefault()
    val starts = snapshot.bars.map { it.startMs }.ifEmpty { HomeTodos.recentDayStarts(zone = zone) }
    val labels = if (snapshot.bars.isNotEmpty()) {
        snapshot.bars.map { it.label }
    } else {
        starts.map { Usage.barLabel(it, UsagePeriod.W1, zone) }
    }
    val details = if (snapshot.bars.isNotEmpty()) {
        snapshot.bars.map { it.detail }
    } else {
        starts.map { Usage.barDetail(it, UsagePeriod.W1, zone) }
    }
    val period = if (snapshot.bars.isNotEmpty()) snapshot.period else UsagePeriod.W1
    val counts = HomeTodos.completedByDays(todos, starts)
    val selected = selectedIndex?.let { counts.getOrNull(it) }
    val subtitle = if (selected != null) {
        HomeTodos.completedDayLabel(selected, details.getOrNull(selectedIndex).orEmpty())
    } else {
        HomeTodos.completedLabel(counts.sum())
    }
    Spacer(Modifier.height(20.dp))
    Text(HomeTodos.COMPLETED, color = Dim, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(6.dp))
    UsageCompletedChart(counts = counts, labels = labels, period = period, selectedIndex = selectedIndex)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Paper, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun UsageCompletedChart(
    counts: List<Int>,
    labels: List<String>,
    period: UsagePeriod,
    selectedIndex: Int? = null,
) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val accent = Accent
    val paper = Paper
    val dim = Dim
    val radiusDp = LocalTokens.current.chartRadius
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = "tasks completed chart" },
    ) {
        if (counts.isEmpty()) return@Canvas
        val radius = radiusDp.toPx()
        val gap = if (counts.size > 10) 2.dp.toPx() else 8.dp.toPx()
        val barWidth = ((size.width - gap * (counts.size - 1)) / counts.size).coerceAtLeast(2.dp.toPx())
        counts.forEachIndexed { index, count ->
            val x = index * (barWidth + gap)
            val h = (count.toFloat() / max.toFloat()) * size.height
            val y = size.height - h
            if (h <= 0f) return@forEachIndexed
            if (radius > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(radius, radius),
                )
            } else {
                drawRect(color = accent, topLeft = Offset(x, y), size = Size(barWidth, h))
            }
            if (selectedIndex == index) {
                drawLine(
                    color = paper,
                    start = Offset(x + barWidth / 2f, 0f),
                    end = Offset(x + barWidth / 2f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                if (Usage.axisLabel(index, labels.size, period)) label else "",
                color = dim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UsageShare(label: String, ms: Long, share: Int, color: Color, last: Boolean) {
    ThemedRow(last = last) {
        Text("$label  $share%", color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Usage.formatDuration(ms), color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UsageAppRow(app: UsageApp, onCycle: () -> Unit, last: Boolean) {
    ThemedRow(
        modifier = Modifier.clickable { onCycle() },
        last = last,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(app.label, color = Paper, style = MaterialTheme.typography.bodyLarge)
            Text(app.kind.label, color = kindColor(app.kind), style = MaterialTheme.typography.bodyMedium)
        }
        Text(Usage.formatDuration(app.millis), color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UsageChart(
    bars: List<UsageBar>,
    period: UsagePeriod,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val max = bars.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val accent = Accent
    val paper = Paper
    val dim = Dim
    val loss = Loss
    val radiusDp = LocalTokens.current.chartRadius
    val select = rememberUpdatedState(onSelect)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = "usage chart" }
            .pointerInput(bars, period) {
                if (bars.isEmpty()) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = Usage.indexAt(down.position.x, size.width.toFloat(), bars.size)
                    select.value(hit)
                    down.consume()
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (moved) {
                                // Keep the last bar so a press still shows that datapoint.
                            }
                            break
                        }
                        if (change.position != down.position) moved = true
                        change.consume()
                        select.value(Usage.indexAt(change.position.x, size.width.toFloat(), bars.size))
                    }
                }
            },
    ) {
        if (bars.isEmpty()) return@Canvas
        val radius = radiusDp.toPx()
        val gap = if (bars.size > 10) 2.dp.toPx() else 8.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { index, bar ->
            val x = index * (barWidth + gap)
            var y = size.height
            fun stack(ms: Long, color: Color) {
                if (ms <= 0L) return
                val h = (ms.toFloat() / max.toFloat()) * size.height
                y -= h
                if (radius > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                } else {
                    drawRect(color = color, topLeft = Offset(x, y), size = Size(barWidth, h))
                }
            }
            stack(bar.otherMs, dim)
            stack(bar.productiveMs, accent)
            stack(bar.distractingMs, loss)
            if (selectedIndex == index) {
                drawLine(
                    color = paper,
                    start = Offset(x + barWidth / 2f, 0f),
                    end = Offset(x + barWidth / 2f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        bars.forEachIndexed { index, bar ->
            Text(
                if (Usage.axisLabel(index, bars.size, period)) bar.label else "",
                color = Dim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun kindColor(kind: UsageKind): Color = when (kind) {
    UsageKind.PRODUCTIVE -> Accent
    UsageKind.DISTRACTING -> Loss
    UsageKind.OTHER -> Dim
}
