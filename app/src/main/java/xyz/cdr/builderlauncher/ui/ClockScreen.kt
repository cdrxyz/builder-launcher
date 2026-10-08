package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import xyz.cdr.builderlauncher.clock.Clock
import xyz.cdr.builderlauncher.clock.ClockAlarm
import xyz.cdr.builderlauncher.clock.ClockAlert
import xyz.cdr.builderlauncher.clock.ClockAlertKind
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.clock.ClockSoundPlayer
import xyz.cdr.builderlauncher.clock.ClockSnapshot
import xyz.cdr.builderlauncher.clock.ClockTab
import xyz.cdr.builderlauncher.clock.TimerState
import xyz.cdr.builderlauncher.clock.WorldClock
import xyz.cdr.builderlauncher.data.ListReorder
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Ink
import xyz.cdr.builderlauncher.ui.theme.LocalTokens
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
    onStartPause: (String) -> Unit,
    onReset: (String) -> Unit,
    onRemoveTimer: (String) -> Unit,
    onToggleAlarm: (String) -> Unit,
    onRemoveAlarm: (String) -> Unit,
    onDismissSnooze: (String) -> Unit,
    onPickZone: (WeatherPlace) -> Unit,
    onRemoveZone: (String) -> Unit,
    onMoveZone: (Int, Int) -> Unit,
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val timers = Clock.timersOf(snapshot)
    LaunchedEffect(timers.any { it.running }, timers.map { it.endsAt }, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now.value = System.currentTimeMillis()
            while (true) {
                now.value = System.currentTimeMillis()
                delay(Clock.homeTickMs(Clock.anyTimerRunning(timers), analog = false))
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = Clock.COMMAND,
            leading = { ScreenBack(Clock.BACK, onBack = onBack) },
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                ClockTab.Timer -> TimerPane(
                    timers = timers,
                    now = now.value,
                    onPreset = onPreset,
                    onStartPause = onStartPause,
                    onReset = onReset,
                    onRemove = onRemoveTimer,
                    modifier = Modifier.fillMaxSize(),
                )
                ClockTab.Alarm -> AlarmPane(
                    alarms = snapshot.alarms,
                    now = now.value,
                    onToggle = onToggleAlarm,
                    onRemove = onRemoveAlarm,
                    onDismissSnooze = onDismissSnooze,
                    modifier = Modifier.fillMaxSize(),
                )
                ClockTab.Zones -> ZonePane(
                    zones = snapshot.zones,
                    hits = zoneHits,
                    now = now.value,
                    onPick = onPickZone,
                    onRemove = onRemoveZone,
                    onMove = onMoveZone,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TimerPane(
    timers: List<TimerState>,
    now: Long,
    onPreset: (Int) -> Unit,
    onStartPause: (String) -> Unit,
    onReset: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = timers.ifEmpty { listOf(TimerState()) }
    val idle = rows.singleOrNull()?.takeIf { !it.running }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        rows.forEach { timer ->
            val display = Clock.formatTimer(Clock.remainingMs(timer, now))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        display,
                        color = Paper,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = if (rows.size == 1) 56.sp else 36.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (rows.size == 1) 60.sp else 40.sp,
                        ),
                    )
                    if (timer.label.isNotBlank()) {
                        Text(timer.label, color = Dim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(
                            if (timer.running) "pause" else "start",
                            color = Accent,
                            modifier = Modifier.clickable { onStartPause(timer.id) }.padding(vertical = 8.dp),
                        )
                        Text(
                            "reset",
                            color = Dim,
                            modifier = Modifier.clickable { onReset(timer.id) }.padding(vertical = 8.dp),
                        )
                    }
                }
                if (rows.size > 1 || timer.label.isNotBlank()) {
                    DeleteIcon(
                        Modifier
                            .clickable { onRemove(timer.id) }
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Clock.PRESETS_MIN.forEach { min ->
                val selected = idle != null && idle.durationMs == min * 60_000L
                Text(
                    min.toString(),
                    color = if (selected) Accent else Dim,
                    modifier = Modifier.clickable { onPreset(min) }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
        Text(
            "Type Pasta 8 minutes, then Enter. Each name is its own timer.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun AlarmPane(
    alarms: List<ClockAlarm>,
    now: Long,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissSnooze: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (alarms.isEmpty()) {
            Text(Clock.ALARM_HINT, color = Dim)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                alarms.forEach { alarm ->
                    val waiting = Clock.snoozed(alarm, now)
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
                            if (alarm.label.isNotBlank()) {
                                Text(alarm.label, color = Dim, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                Clock.alarmStatus(alarm, now),
                                color = if (alarm.enabled) Accent else Dim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (waiting) {
                            Text(
                                "dismiss",
                                color = Accent,
                                modifier = Modifier
                                    .clickable { onDismissSnooze(alarm.id) }
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
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
    modifier: Modifier = Modifier,
) {
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { 4.dp.toPx() }
    val liveZones = rememberUpdatedState(zones)
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll, enabled = dragFrom == null),
    ) {
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
                                            val i = ListReorder.liveIndex(liveZones.value) { it.id == zone.id }
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
                                            dragTo = ListReorder.targetIndex(from, dragY, step, liveZones.value.lastIndex)
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
    sound: ClockSound = ClockSound.OFF,
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    onStop: () -> Unit = {},
    onRunAgain: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onSnooze: () -> Unit = {},
) {
    val ctx = LocalContext.current
    DisposableEffect(alert, sound) {
        if (!sound.silent) ClockSoundPlayer.startAlert(ctx, sound)
        onDispose { }
    }
    val timer = alert.kind == ClockAlertKind.TIMER
    val title = if (timer) alert.label.ifBlank { "Time is up" } else alert.label.ifBlank { "Alarm" }
    val time = if (timer) Clock.formatTimer(alert.durationMs) else Clock.formatAlarm(alert.hour, alert.minute)
    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            .windowInsetsPadding(contentInsets),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val heightDp = maxHeight.value.toInt()
            val titleSize = ClockAlertLayout.titleSp(heightDp)
            val timeSize = ClockAlertLayout.timeSp(heightDp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title,
                    color = Paper,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (titleSize + 6).sp,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    time,
                    color = Paper,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = timeSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (timeSize + 4).sp,
                    ),
                )
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (timer) {
                        AlertAction("stop", Accent, Accent, heightDp, onStop)
                        AlertAction("run again", Accent, Accent, heightDp, onRunAgain)
                    } else {
                        AlertAction("dismiss", Paper, Dim, heightDp, onDismiss)
                        AlertAction("snooze 8 min", Accent, Accent, heightDp, onSnooze)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertAction(
    label: String,
    text: Color,
    border: Color,
    heightDp: Int,
    onClick: () -> Unit,
) {
    val size = ClockAlertLayout.actionSp(heightDp)
    val shape = RoundedCornerShape(LocalTokens.current.radius)
    Box(
        Modifier
            .fillMaxWidth(0.86f)
            .defaultMinSize(minHeight = ClockAlertLayout.actionMinHeightDp(heightDp).dp)
            .border(2.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = size.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (size + 6).sp,
            ),
        )
    }
}
