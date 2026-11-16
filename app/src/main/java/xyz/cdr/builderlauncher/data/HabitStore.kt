package xyz.cdr.builderlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HabitSnapshot(
    val habits: List<Habit> = emptyList(),
    val marks: List<HabitMark> = emptyList(),
    val deletedIds: List<String> = emptyList(),
)

class HabitStore internal constructor(
    private val file: File,
    private val marksFile: File,
    private val deletedFile: File,
) {
    constructor(context: Context) : this(
        File(context.filesDir, "habits.json"),
        File(context.filesDir, "habit-marks.json"),
        File(context.filesDir, "deleted-habit-ids.json"),
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<HabitSnapshot> = _state.asStateFlow()

    fun add(draft: HabitDraft, now: Long = System.currentTimeMillis()): Habit {
        val habit = Habit(
            id = now.toString(36),
            name = draft.name,
            kind = draft.kind,
            everyMinutes = draft.everyMinutes,
            times = draft.times,
            createdAt = now,
            updatedAt = now,
        )
        val deleted = _state.value.deletedIds.filterNot { it == habit.id }
        persist(_state.value.copy(habits = listOf(habit) + _state.value.habits, deletedIds = deleted))
        return habit
    }

    fun remove(id: String) {
        val snap = _state.value
        persist(
            snap.copy(
                habits = snap.habits.filterNot { it.id == id },
                marks = snap.marks.filterNot { it.habitId == id },
                deletedIds = (snap.deletedIds + id).distinct(),
            ),
        )
    }

    fun complete(id: String, slotAt: Long, now: Long = System.currentTimeMillis()) {
        val snap = _state.value
        if (Habits.done(snap.marks, id, slotAt)) return
        persist(snap.copy(marks = Habits.toggle(snap.marks, id, slotAt, now)))
    }

    fun toggle(id: String, slotAt: Long, now: Long = System.currentTimeMillis()) {
        persist(_state.value.copy(marks = Habits.toggle(_state.value.marks, id, slotAt, now)))
    }

    fun replaceAll(habits: List<Habit>, marks: List<HabitMark>, deleted: List<String>) {
        val drop = deleted.distinct()
        val gone = drop.toSet()
        persist(
            HabitSnapshot(
                habits = habits.filterNot { it.id in gone },
                marks = marks.filterNot { it.habitId in gone },
                deletedIds = drop,
            ),
        )
    }

    private fun persist(next: HabitSnapshot) {
        _state.value = next
        file.writeText(json.encodeToString(next.habits))
        marksFile.writeText(json.encodeToString(next.marks))
        deletedFile.writeText(json.encodeToString(next.deletedIds))
    }

    private fun load(): HabitSnapshot {
        val habits = read<List<Habit>>(file)
        val marks = read<List<HabitMark>>(marksFile)
        val deleted = read<List<String>>(deletedFile)
        val gone = deleted.toSet()
        return HabitSnapshot(
            habits = habits.filterNot { it.id in gone },
            marks = marks.filterNot { it.habitId in gone },
            deletedIds = deleted,
        )
    }

    private inline fun <reified T> read(source: File): T {
        if (!source.exists()) {
            return when (T::class) {
                List::class -> emptyList<Any>() as T
                else -> error("unsupported")
            }
        }
        return runCatching { json.decodeFromString<T>(source.readText()) }.getOrElse {
            emptyList<Any>() as T
        }
    }
}
