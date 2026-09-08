package xyz.cdr.builderlauncher.home

sealed class BackResult {
    data object Stay : BackResult()
    data object DismissUi : BackResult()
    data object OpenHome : BackResult()
}

object BackPress {
    fun result(onHome: Boolean, overlayOpen: Boolean): BackResult = when {
        overlayOpen -> BackResult.DismissUi
        onHome -> BackResult.Stay
        else -> BackResult.OpenHome
    }
}
