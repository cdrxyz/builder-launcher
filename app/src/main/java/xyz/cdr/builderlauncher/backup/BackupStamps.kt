package xyz.cdr.builderlauncher.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Epoch-millisecond stamps cross the sync boundary as integers. A client that
 * writes a fractional stamp (`updatedAt: 1794951762423.9924`) cannot be
 * decoded into the Long fields of [BackupDocument] and bricks this phone's
 * sync until the vault is rewritten. The Worker rounds stamps on every vault
 * read and write (merge.js roundStamps); this side tolerates a document that
 * was stored before that deploy, so the next sync heals the vault instead of
 * failing forever.
 */
object BackupStamps {
    fun decodeDocument(text: String, json: Json): BackupDocument =
        json.decodeFromJsonElement(roundStamps(json.parseToJsonElement(text)))

    /**
     * Copies only the paths that change: an integral document comes back
     * byte-identical, a fractional stamp becomes a rounded integer. Stamp
     * keys are `…At`, `…AtEpochMs`, `…Ms`, and pinChanges' `at`; other
     * fractional numbers (prices, coordinates, speed) stay untouched.
     */
    fun roundStamps(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            var changed = false
            val out = LinkedHashMap<String, JsonElement>(element.size)
            for ((key, value) in element) {
                val next = when {
                    value is JsonPrimitive && !value.isString && isStampKey(key) -> rounded(value)
                    else -> roundStamps(value)
                }
                if (next != value) changed = true
                out[key] = next
            }
            if (changed) JsonObject(out) else element
        }
        is JsonArray -> {
            var changed = false
            val out = ArrayList<JsonElement>(element.size)
            for (value in element) {
                val next = roundStamps(value)
                if (next != value) changed = true
                out.add(next)
            }
            if (changed) JsonArray(out) else element
        }
        else -> element
    }

    private fun isStampKey(key: String): Boolean =
        key == "at" || key.endsWith("At") || key.endsWith("Ms")

    private fun rounded(primitive: JsonPrimitive): JsonElement {
        // Integral longs pass through untouched; only a fractional number
        // becomes a rounded long. Math.round matches the Worker's JS
        // Math.round (ties toward positive infinity); kotlin.math.round
        // would round ties to even and disagree by 1 ms on half values.
        if (primitive.longOrNull != null) return primitive
        val fraction = primitive.doubleOrNull ?: return primitive
        if (!fraction.isFinite() || fraction == kotlin.math.floor(fraction)) return primitive
        return JsonPrimitive(Math.round(fraction))
    }
}
