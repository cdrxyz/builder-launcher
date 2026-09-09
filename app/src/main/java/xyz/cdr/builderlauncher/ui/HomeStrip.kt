package xyz.cdr.builderlauncher.ui

object HomeStrip {
    const val PODCASTS = 0
    const val HOME = 1
    const val HUB = 2
    const val COUNT = 3

    val pages = listOf(Page.Podcasts, Page.Home, Page.Hub)

    fun contains(page: Page): Boolean = page == Page.Home || page == Page.Hub || page == Page.Podcasts

    fun coversPager(page: Page): Boolean = !contains(page)

    fun indexOf(page: Page): Int? = when (page) {
        Page.Podcasts -> PODCASTS
        Page.Home -> HOME
        Page.Hub -> HUB
        else -> null
    }

    fun pageAt(index: Int): Page = pages.getOrElse(index) { Page.Home }

    fun homeAfterOverlay(): Page = Page.Home

    fun followSettled(
        page: Page,
        settledIndex: Int,
        currentIndex: Int,
        scrolling: Boolean,
    ): Page? {
        if (scrolling || currentIndex != settledIndex) return null
        if (!contains(page)) return null
        val next = pageAt(settledIndex)
        return next.takeIf { it != page }
    }
}
