package xyz.cdr.builderlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PinnedApps(context: Context) {
    private val prefs = context.getSharedPreferences("builder.pins", Context.MODE_PRIVATE)
    private val _packages = MutableStateFlow(read())
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    fun packages(): List<String> = _packages.value

    fun pin(packageName: String) {
        persist((_packages.value + packageName).distinct())
    }

    fun unpin(packageName: String) {
        persist(_packages.value.filterNot { it == packageName })
    }

    fun isPinned(packageName: String): Boolean = packageName in _packages.value

    private fun persist(next: List<String>) {
        _packages.value = next
        prefs.edit().putString("pins", next.joinToString("\n")).apply()
    }

    private fun read(): List<String> =
        prefs.getString("pins", "")
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
