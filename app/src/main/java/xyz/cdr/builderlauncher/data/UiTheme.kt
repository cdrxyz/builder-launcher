package xyz.cdr.builderlauncher.data

enum class UiTheme {
    CYBERPUNK,
    PLAIN,
    MATERIAL,
    IOS,
    ;

    val label: String get() = name.lowercase()

    val defaultAccentHex: String
        get() = when (this) {
            CYBERPUNK -> AccentColor.DEFAULT_HEX
            PLAIN -> "#F5F5F5"
            MATERIAL -> "#6750A4"
            IOS -> "#007AFF"
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
