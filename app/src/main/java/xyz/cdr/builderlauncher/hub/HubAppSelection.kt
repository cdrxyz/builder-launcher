package xyz.cdr.builderlauncher.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which installed apps feed the hub. Unrestricted means the built-in messengers,
 * including Slack. A restriction is an explicit include list and stays on the phone.
 */
data class HubAppSelection(
    val restrict: Boolean = false,
    val packages: Set<String> = emptySet(),
) {
    fun allows(packageName: String): Boolean {
        val key = packageName.lowercase()
        return packages.any { it.lowercase() == key }
    }

    fun checked(packageName: String): Boolean =
        if (restrict) allows(packageName) else HubMessages.isKnownMessenger(packageName)

    fun toggle(packageName: String, installedPackages: List<String>): HubAppSelection {
        val key = packageName.lowercase()
        val defaults = defaultPackages(installedPackages)
        val current = if (restrict) packages.map { it.lowercase() }.toSet() else defaults
        val next = if (key in current) current - key else current + key
        return if (next == defaults) HubAppSelection() else HubAppSelection(restrict = true, packages = next)
    }

    companion object {
        fun defaultPackages(installedPackages: List<String>): Set<String> =
            installedPackages
                .map { it.lowercase() }
                .filter { HubMessages.isKnownMessenger(it) }
                .toSet()

        fun decode(restrictRaw: String?, packagesRaw: String?): HubAppSelection {
            val packages = packagesRaw.orEmpty()
                .split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            return if (restrictRaw != "true") HubAppSelection() else HubAppSelection(restrict = true, packages = packages)
        }

        fun encodeRestrict(selection: HubAppSelection): String =
            if (selection.restrict) "true" else "false"

        fun encodePackages(selection: HubAppSelection): String =
            if (!selection.restrict) "" else selection.packages.map { it.lowercase() }.sorted().joinToString(",")
    }
}

object HubFilter {
    private val _selection = MutableStateFlow(HubAppSelection())
    val selection: StateFlow<HubAppSelection> = _selection.asStateFlow()

    @Volatile
    private var published = false

    fun publish(selection: HubAppSelection) {
        synchronized(this) {
            published = true
            _selection.value = selection
        }
    }

    /** Listener start must not clobber a selection the UI already published. */
    fun ensure(fromDisk: HubAppSelection) {
        synchronized(this) {
            if (published) return
            published = true
            _selection.value = fromDisk
        }
    }
}
