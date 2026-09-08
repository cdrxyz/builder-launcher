package xyz.cdr.builderlauncher.data

data class AccentSwatch(val name: String, val hex: String)

object AccentColor {
    const val DEFAULT_HEX = "#00FF41"
    const val DEFAULT_UP_HEX = "#00FF41"
    const val DEFAULT_DOWN_HEX = "#FF3B30"

    val presets = listOf(
        AccentSwatch("cyberpunk green", DEFAULT_HEX),
        AccentSwatch("red", DEFAULT_DOWN_HEX),
        AccentSwatch("sage", "#B7C9A8"),
        AccentSwatch("amber", "#FFB000"),
        AccentSwatch("cyan", "#00E5FF"),
        AccentSwatch("magenta", "#FF2BD6"),
        AccentSwatch("paper", "#E8E4D9"),
    )

    fun parse(hex: String?): Int? {
        val raw = hex.orEmpty().trim().removePrefix("#")
        val digits = when (raw.length) {
            3 -> raw.map { "$it$it" }.joinToString("")
            6, 8 -> raw
            else -> return null
        }
        if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val n = digits.toLongOrNull(16) ?: return null
        val rgb = (n and 0xFFFFFF).toInt()
        return (0xFF shl 24) or rgb
    }

    fun argb(hex: String?): Int = parse(hex) ?: parse(DEFAULT_HEX)!!

    fun normalize(hex: String?): String {
        val rgb = argb(hex) and 0xFFFFFF
        return "#%06X".format(java.util.Locale.US, rgb)
    }

    fun nameOf(hex: String?): String? {
        val n = normalize(hex)
        return presets.firstOrNull { normalize(it.hex) == n }?.name
    }

    fun same(a: String?, b: String?): Boolean = parse(a) != null && parse(a) == parse(b)
}
