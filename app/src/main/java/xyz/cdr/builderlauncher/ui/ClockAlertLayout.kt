package xyz.cdr.builderlauncher.ui

/**
 * Sizes for the full-screen alarm and timer alert.
 * The block is centered in the visible height after system bars and the software keyboard,
 * so a short hardware-keyboard display and a slab with the keyboard open both keep the actions on screen.
 */
internal object ClockAlertLayout {
    fun titleSp(heightDp: Int): Int = when {
        heightDp < 420 -> 48
        heightDp < 560 -> 56
        else -> 72
    }

    fun timeSp(heightDp: Int): Int = when {
        heightDp < 420 -> 36
        heightDp < 560 -> 40
        else -> 48
    }

    fun actionSp(heightDp: Int): Int = when {
        heightDp < 420 -> 28
        heightDp < 560 -> 32
        else -> 36
    }

    fun actionMinHeightDp(heightDp: Int): Int = when {
        heightDp < 420 -> 72
        heightDp < 560 -> 84
        else -> 96
    }
}
