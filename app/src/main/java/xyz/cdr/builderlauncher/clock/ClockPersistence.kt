package xyz.cdr.builderlauncher.clock

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ClockPersistence {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(snapshot: ClockSnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): ClockSnapshot? =
        runCatching { json.decodeFromString<ClockSnapshot>(text) }.getOrNull()

    fun read(file: File): ClockSnapshot {
        if (!file.exists()) return ClockSnapshot()
        val text = runCatching { file.readText() }.getOrNull() ?: return ClockSnapshot()
        return decode(text) ?: ClockSnapshot()
    }

    fun write(file: File, snapshot: ClockSnapshot) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encode(snapshot))
        runCatching {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure {
            runCatching { file.writeText(tmp.readText()) }
            tmp.delete()
        }
    }
}
