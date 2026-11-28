package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.clock.ClockSound
import xyz.cdr.builderlauncher.data.DayStat
import xyz.cdr.builderlauncher.data.Habit
import xyz.cdr.builderlauncher.data.HabitMark
import xyz.cdr.builderlauncher.data.Habits
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.LocalTokens
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.ThemedList
import java.time.ZoneId

@Composable
internal fun HomeLists(
    previewTodos: List<xyz.cdr.builderlauncher.data.LocalItem>,
    leavingIds: Set<String>,
    habits: List<Habit>,
    marks: List<HabitMark>,
    onToggleTodo: (String) -> Unit,
    onOpenTodos: () -> Unit,
    onToggleHabit: (String, Long) -> Unit,
    onOpenHabits: () -> Unit,
) {
    val zone = java.time.ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    HomeTaskSwipe(
        tasks = {
            TodoPreview(
                open = previewTodos,
                onToggle = onToggleTodo,
                leavingIds = leavingIds,
                onMore = onOpenTodos,
            )
        },
        habits = {
            HabitPreview(
                habits = habits,
                marks = marks,
                now = now,
                zone = zone,
                onToggle = onToggleHabit,
                onMore = onOpenHabits,
            )
        },
    )
}

@Composable
internal fun HabitsHost(
    habits: List<Habit>,
    marks: List<HabitMark>,
    reminders: Boolean,
    sound: ClockSound,
    onReminders: (Boolean) -> Unit,
    onSound: (ClockSound) -> Unit,
    prompt: Char,
    input: String,
    hardware: Boolean,
    onBack: () -> Unit,
    onToggle: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Habit) -> Unit,
    onValue: (String) -> Unit,
    onPick: (Char) -> Unit,
    onClearMode: () -> Unit,
    onSubmit: () -> Unit,
    onSlash: (xyz.cdr.builderlauncher.commands.SlashCommand) -> Unit,
    onHub: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        HabitScreen(
            habits = habits,
            marks = marks,
            now = System.currentTimeMillis(),
            zone = java.time.ZoneId.systemDefault(),
            reminders = reminders,
            sound = sound,
            onReminders = onReminders,
            onSound = onSound,
            onBack = onBack,
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        CommandBar(
            prompt = prompt,
            value = input,
            hardware = hardware,
            onValue = onValue,
            onPick = onPick,
            onClearMode = onClearMode,
            onSubmit = onSubmit,
            onSlash = onSlash,
            onHub = onHub,
        )
    }
}

@Composable
internal fun HomeTaskSwipe(
    tasks: @Composable () -> Unit,
    habits: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = 48.dp
    Column(
        modifier.pointerInput(page) {
            val px = threshold.toPx()
            detectHorizontalDragGestures(
                onDragEnd = {
                    page = Habits.pageAfterSwipe(page, drag, px)
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
                onHorizontalDrag = { change, amount ->
                    drag += amount
                    change.consume()
                },
            )
        },
    ) {
        if (page == 0) tasks() else habits()
    }
}

@Composable
internal fun HabitPreview(
    habits: List<Habit>,
    marks: List<HabitMark>,
    now: Long,
    zone: ZoneId,
    onToggle: (String, Long) -> Unit,
    onMore: () -> Unit,
) {
    val rows = habits.mapNotNull { habit ->
        Habits.openSlot(habit, now, zone)?.let { slot -> habit to slot }
    }.sortedBy { Habits.done(marks, it.first.id, it.second) }.take(Habits.PREVIEW)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (rows.isEmpty()) {
            Text(Habits.EMPTY, color = Dim, style = MaterialTheme.typography.bodyMedium)
        }
        rows.forEach { (habit, slot) ->
            HabitLine(
                habit = habit,
                checked = Habits.done(marks, habit.id, slot),
                onToggle = { onToggle(habit.id, slot) },
            )
        }
        CaretLink(
            Habits.MORE,
            modifier = Modifier.clickable { onMore() }.padding(vertical = 4.dp),
            color = Dim,
            caretColor = Dim,
        )
    }
}

@Composable
internal fun HabitScreen(
    habits: List<Habit>,
    marks: List<HabitMark>,
    now: Long,
    zone: ZoneId,
    reminders: Boolean,
    sound: ClockSound,
    onReminders: (Boolean) -> Unit,
    onSound: (ClockSound) -> Unit,
    onBack: () -> Unit,
    onToggle: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Habit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starts = Habits.recentDayStarts(now, zone = zone)
    val stats = Habits.graph(habits, marks, starts, zone)
    val today = stats.lastOrNull()
    Column(modifier) {
        ScreenHeader(
            title = Habits.TITLE,
            leading = { ScreenBack(onBack = onBack) },
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("completion", color = Dim, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            HabitChart(stats)
            if (today != null) {
                Spacer(Modifier.height(8.dp))
                Text(Habits.dayLabel(today), color = Paper, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            HabitReminderControls(reminders, sound, onReminders, onSound)
            Spacer(Modifier.height(16.dp))
            if (habits.isEmpty()) {
                Text(Habits.EMPTY, color = Dim, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(Habits.HINT, color = Dim, style = MaterialTheme.typography.bodyMedium)
            } else {
                ThemedList {
                    habits.forEachIndexed { index, habit ->
                        val slot = Habits.openSlot(habit, now, zone)
                        HabitLine(
                            habit = habit,
                            checked = slot != null && Habits.done(marks, habit.id, slot),
                            onToggle = { if (slot != null) onToggle(habit.id, slot) },
                            onDelete = { onDelete(habit.id) },
                            onEdit = { onEdit(habit) },
                            last = index == habits.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HabitReminderControls(
    reminders: Boolean,
    sound: ClockSound,
    onReminders: (Boolean) -> Unit,
    onSound: (ClockSound) -> Unit,
) {
    Text("alarms", color = Dim, style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        listOf(true to "on", false to "off").forEach { (on, label) ->
            Text(
                label,
                color = if (reminders == on) Accent else Dim,
                modifier = Modifier.clickable { onReminders(on) },
            )
        }
    }
    Text("tone", color = Dim, style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        ClockSound.entries.take(3).forEach { tone ->
            Text(
                tone.label,
                color = if (sound == tone) Accent else Dim,
                modifier = Modifier.clickable { onSound(tone) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        ClockSound.entries.drop(3).forEach { tone ->
            Text(
                tone.label,
                color = if (sound == tone) Accent else Dim,
                modifier = Modifier.clickable { onSound(tone) },
            )
        }
    }
    Text(
        if (reminders) "A reminder plays ${sound.label} when a slot is due." else "Habits stay on the list. No reminder.",
        color = Dim,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun HabitLine(
    habit: Habit,
    checked: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    last: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckIcon(
            checked = checked,
            modifier = Modifier
                .semantics { contentDescription = if (checked) "reopen habit" else "check habit" }
                .clickable { onToggle() }
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .clickable { if (onEdit != null) onEdit() else onToggle() }
                .semantics { if (onEdit != null) contentDescription = "edit habit" }
                .padding(vertical = 6.dp),
        ) {
            Text(
                habit.name,
                color = if (checked) Dim else Paper,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            Text(Habits.scheduleLabel(habit), color = Dim, style = MaterialTheme.typography.labelSmall)
        }
        if (onDelete != null) {
            DeleteIcon(
                Modifier
                    .semantics { contentDescription = "delete habit" }
                    .clickable { onDelete() }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
    if (!last && onDelete != null) Spacer(Modifier.height(0.dp))
}

@Composable
internal fun HabitChart(stats: List<DayStat>) {
    val max = stats.maxOfOrNull { it.completed }?.coerceAtLeast(1) ?: 1
    val accent = Accent
    val radiusDp = LocalTokens.current.chartRadius
    val description = stats.lastOrNull()?.let { "habits chart ${Habits.dayLabel(it)}" } ?: "habits chart"
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description },
    ) {
        if (stats.isEmpty()) return@Canvas
        val radius = radiusDp.toPx()
        val gap = if (stats.size > 10) 2.dp.toPx() else 6.dp.toPx()
        val barWidth = ((size.width - gap * (stats.size - 1)) / stats.size).coerceAtLeast(2.dp.toPx())
        stats.forEachIndexed { index, stat ->
            val x = index * (barWidth + gap)
            val h = (stat.completed.toFloat() / max.toFloat()) * size.height
            if (h > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }
}
