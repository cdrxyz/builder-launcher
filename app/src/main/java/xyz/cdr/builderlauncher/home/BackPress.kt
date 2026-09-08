package xyz.cdr.builderlauncher.home

sealed class BackResult {
    data object Stay : BackResult()
    data object DismissUi : BackResult()
    data object OpenHome : BackResult()
    data object ResetPrompt : BackResult()
}

object BackPress {
    fun result(onHome: Boolean, overlayOpen: Boolean, promptActive: Boolean = false): BackResult = when {
        overlayOpen -> BackResult.DismissUi
        onHome && promptActive -> BackResult.ResetPrompt
        onHome -> BackResult.Stay
        else -> BackResult.OpenHome
    }
}
