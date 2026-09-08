package xyz.cdr.builderlauncher.ui

object HomeStrip {
    const val USAGE = 0
    const val HOME = 1
    const val HUB = 2
    const val COUNT = 3

    val pages = listOf(Page.Usage, Page.Home, Page.Hub)

    fun contains(page: Page): Boolean = page == Page.Home || page == Page.Hub || page == Page.Usage

    fun indexOf(page: Page): Int? = when (page) {
        Page.Usage -> USAGE
        Page.Home -> HOME
        Page.Hub -> HUB
        else -> null
    }

    fun pageAt(index: Int): Page = pages.getOrElse(index) { Page.Home }
}
