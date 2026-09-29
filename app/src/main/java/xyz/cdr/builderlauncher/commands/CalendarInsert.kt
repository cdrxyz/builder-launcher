package xyz.cdr.builderlauncher.commands

data class CalendarInsert(
    val title: String,
    val beginMillis: Long? = null,
    val endMillis: Long? = null,
    val description: String? = null,
) {
    companion object {
        fun from(title: String, whenText: String, now: Long = System.currentTimeMillis()): CalendarInsert {
            val parsed = EventWhen.parse(whenText, now)
            return if (parsed != null) {
                CalendarInsert(
                    title = title,
                    beginMillis = parsed.beginMillis,
                    endMillis = parsed.endMillis,
                )
            } else {
                CalendarInsert(
                    title = title,
                    description = whenText.trim().takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}
