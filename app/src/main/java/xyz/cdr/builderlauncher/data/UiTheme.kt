package xyz.cdr.builderlauncher.data

enum class UiTone {
    DARK,
    LIGHT,
    ;

    val label: String get() = name.lowercase()

    companion object {
        fun parse(raw: String?): UiTone =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

enum class UiTheme {
    CYBERPUNK,
    PLAIN,
    MATERIAL,
    IOS,
    ;

    val label: String get() = name.lowercase()

    val defaultTone: UiTone
        get() = when (this) {
            CYBERPUNK, PLAIN -> UiTone.DARK
            MATERIAL, IOS -> UiTone.LIGHT
        }

    val defaultAccentHex: String get() = defaultAccentHex(defaultTone)

    fun defaultAccentHex(tone: UiTone): String = when (this) {
        CYBERPUNK -> if (tone == UiTone.DARK) AccentColor.DEFAULT_HEX else "#008C28"
        PLAIN -> if (tone == UiTone.DARK) "#F5F5F5" else "#111111"
        MATERIAL -> if (tone == UiTone.DARK) "#D0BCFF" else "#6750A4"
        IOS -> if (tone == UiTone.DARK) "#0A84FF" else "#007AFF"
    }

    val blurb: String
        get() = when (this) {
            CYBERPUNK -> "Terminal UI. Monospace, green on black."
            PLAIN -> "Black and white. Rounded accent command bar."
            MATERIAL -> "Material Design surfaces and type."
            IOS -> "iOS grouped lists and system blue."
        }

    companion object {
        fun parse(raw: String?): UiTheme =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: CYBERPUNK
    }
}
