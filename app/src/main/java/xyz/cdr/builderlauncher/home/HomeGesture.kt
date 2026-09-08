package xyz.cdr.builderlauncher.home

import android.content.Intent

object HomeGesture {
    fun isHomePress(action: String?, categories: Collection<String>?): Boolean =
        action == Intent.ACTION_MAIN && categories?.contains(Intent.CATEGORY_HOME) == true

    fun shouldOpenHome(
        wasStopped: Boolean,
        action: String?,
        categories: Collection<String>?,
    ): Boolean = !wasStopped && isHomePress(action, categories)
}
