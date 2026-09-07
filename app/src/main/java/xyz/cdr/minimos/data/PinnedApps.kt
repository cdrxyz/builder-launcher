package xyz.cdr.minimos.data

import android.content.Context

class PinnedApps(context: Context) {
    private val prefs = context.getSharedPreferences("minimos.pins", Context.MODE_PRIVATE)

    fun list(): List<String> =
        prefs.getString("pins", "")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun pin(packageName: String) {
        val next = (list() + packageName).distinct()
        prefs.edit().putString("pins", next.joinToString("\n")).apply()
    }

    fun unpin(packageName: String) {
        prefs.edit().putString("pins", list().filterNot { it == packageName }.joinToString("\n")).apply()
    }
}
