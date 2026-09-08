package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import xyz.cdr.builderlauncher.clock.Clock
import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.clock.ClockAlert
import xyz.cdr.builderlauncher.clock.ClockAlertKind
import xyz.cdr.builderlauncher.clock.ClockSnapshot
import xyz.cdr.builderlauncher.clock.ClockTab
import xyz.cdr.builderlauncher.clock.WorldClock
import xyz.cdr.builderlauncher.data.ListReorder
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.weather.WeatherPlace

@Composable
fun ClockScreen(
    snapshot: ClockSnapshot,
    tab: ClockTab,
    zoneHits: List<WeatherPlace>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onTab: (ClockTab) -> Unit,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onToggleAlarm: (String) -> Unit,
    onRemoveAlarm: (String) -> Unit,
    onPickZone: (WeatherPlace) -> Unit,
    onRemoveZone: (String) -> Unit,
    onMoveZone: (Int, Int) -> Unit,
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.timer.running) {
        while (true) {
            now.value = System.currentTimeMillis()
            delay(if (snapshot.timer.running) 200 else 15_000)
        }
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            Clock.BACK,
            color = Accent,
            modifier = Modifier.clickable { onBack() }.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ClockTab.entries.forEach { item ->
                val label = when (item) {
                    ClockTab.Timer -> "Timer"
                    ClockTab.Alarm -> "Alarm"
                    ClockTab.Zones -> "Time Zones"
                }
                Text(
                    label,
                    color = if (item == tab) Accent else Dim,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onTab(item) }.padding(vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            ClockTab.Timer -> TimerPane(
                display = Clock.formatTimer(Clock.remainingMs(snapshot.timer, now.value)),
                running = snapshot.timer.running,
                durationMs = snapshot.timer.durationMs,
                onPreset = onPreset,
                onStartPause = onStartPause,
                onReset = onReset,
            )
            ClockTab.Alarm -> AlarmPane(
                alarms = snapshot.alarms,
                onToggle = onToggleAlarm,
                onRemove = onRemoveAlarm,
            )
            ClockTab.Zones -> ZonePane(
                zones = snapshot.zones,
                hits = zoneHits,
                now = now.value,
                onPick = onPickZone,
                onRemove = onRemoveZone,
                onMove = onMoveZone,
            )
        }
    }
}

@Composable
private fun TimerPane(
    display: String,
    running: Boolean,
    durationMs: Long,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            display,
            color = Paper,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 60.sp,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Clock.PRESETS_MIN.forEach { min ->
                val selected = durationMs == min * 60_000L && !running
                Text(
                    min.toString(),
                    color = if (selected) Accent else Dim,
                    modifier = Modifier.clickable { onPreset(min) }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                if (running) "pause" else "start",
                color = Accent,
                modifier = Modifier.clickable { onStartPause() }.padding(vertical = 8.dp),
            )
            Text(
                "reset",
                color = Dim,
                modifier = Modifier.clickable { onReset() }.padding(vertical = 8.dp),
            )
        }
        Text(
            "Type minutes (5) or mm:ss, then Enter.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun AlarmPane(
    alarms: List<ClockAlarm>,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (alarms.isEmpty()) {
            Text("Type 7:30 or 7:30am, then Enter.", color = Dim)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                alarms.forEach { alarm ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onToggle(alarm.id) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(Clock.formatAlarm(alarm.hour, alarm.minute), color = Paper)
                            Text(
                                if (alarm.enabled) "on" else "off",
                                color = if (alarm.enabled) Accent else Dim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        DeleteIcon(
                            Modifier
                                .clickable { onRemove(alarm.id) }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZonePane(
    zones: List<WorldClock>,
    hits: List<WeatherPlace>,
    now: Long,
    onPick: (WeatherPlace) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 4.dp.toPx() }
    Column(Modifier.fillMaxWidth()) {
        if (hits.isNotEmpty()) {
            hits.forEach { place ->
                Text(
                    place.label,
                    color = Paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(place) }
                        .padding(vertical = 8.dp),
                )
            }
        } else if (zones.isEmpty()) {
            Text("Type a city, then Enter.", color = Dim)
        }
        if (hits.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                zones.forEachIndexed { index, zone ->
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
                            .onSizeChanged { rowHeight = it.height.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .pointerInput(zone.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragFrom = index
                                            dragTo = index
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
                                            dragTo = ListReorder.targetIndex(from, dragY, step, zones.lastIndex)
                                        },
                                    )
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(Clock.formatZoneTime(zone.zoneId, now), color = Paper)
                            Text(zone.label, color = Dim, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Clock.formatZoneDate(zone.zoneId, now),
                                color = Dim,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        DeleteIcon(
                            Modifier
                                .clickable { onRemove(zone.id) }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockAlertScreen(
    alert: ClockAlert,
    modifier: Modifier = Modifier,
    onStop: () -> Unit = {},
    onRunAgain: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onSnooze: () -> Unit = {},
) {
    val timer = alert.kind == ClockAlertKind.TIMER
    Column(
        modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            if (timer) "Time is up" else alert.label.ifBlank { "Alarm" },
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (timer) Clock.formatTimer(alert.durationMs) else Clock.formatAlarm(alert.hour, alert.minute),
            color = Paper,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 60.sp,
            ),
        )
        Spacer(Modifier.height(24.dp))
        if (timer) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    "stop",
                    color = Accent,
                    modifier = Modifier.clickable { onStop() }.padding(vertical = 8.dp),
                )
                Text(
                    "run again",
                    color = Accent,
                    modifier = Modifier.clickable { onRunAgain() }.padding(vertical = 8.dp),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    "dismiss",
                    color = Dim,
                    modifier = Modifier.clickable { onDismiss() }.padding(vertical = 8.dp),
                )
                Text(
                    "snooze 8 min",
                    color = Accent,
                    modifier = Modifier.clickable { onSnooze() }.padding(vertical = 8.dp),
                )
            }
        }
    }
}
