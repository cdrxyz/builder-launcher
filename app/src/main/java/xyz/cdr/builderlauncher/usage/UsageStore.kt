package xyz.cdr.builderlauncher.usage

import android.content.Context

class UsageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun overrides(): Map<String, UsageKind> {
        val raw = prefs.getString(KEY, "") ?: return emptyMap()
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val kind = runCatching { UsageKind.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            parts[0] to kind
        }.toMap()
    }

    fun kind(packageName: String): UsageKind = Usage.kindOf(packageName, overrides())

    fun cycle(packageName: String): UsageKind {
        val next = kind(packageName).next()
        set(packageName, next)
        return next
    }

    fun set(packageName: String, kind: UsageKind) {
        val next = overrides().toMutableMap()
        if (kind == Usage.defaultKind(packageName)) {
            next.remove(packageName)
        } else {
            next[packageName] = kind
        }
        val encoded = next.entries.joinToString("\n") { "${it.key}=${it.value.name}" }
        prefs.edit().putString(KEY, encoded).apply()
    }

    companion object {
        private const val PREFS = "builder.usage"
        private const val KEY = "kinds"
    }
}
