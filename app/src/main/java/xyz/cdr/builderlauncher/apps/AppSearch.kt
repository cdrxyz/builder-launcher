package xyz.cdr.builderlauncher.apps

object AppSearch {
    /**
     * Extra label/package tokens that should match a query.
     * Searching Signal also finds Molly, an open-source Signal client.
     */
    private val extraTermsByCanonical = listOf(
        "signal" to setOf("molly"),
    )

    fun filter(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        val extras = extraTermsFor(q)
        return apps.filter { app ->
            val label = app.label.lowercase()
            val pkg = app.packageName.lowercase()
            label.contains(q) || pkg.contains(q) || extras.any { term ->
                label.contains(term) || pkg.contains(term)
            }
        }
    }

    private fun extraTermsFor(q: String): Set<String> {
        return extraTermsByCanonical.flatMap { (canonical, terms) ->
            if (hitsCanonical(q, canonical)) terms else emptySet()
        }.toSet()
    }

    private fun hitsCanonical(q: String, canonical: String): Boolean {
        if (q.contains(canonical)) return true
        return q.length >= 3 && canonical.startsWith(q)
    }
}
