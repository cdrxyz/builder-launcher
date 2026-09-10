package xyz.cdr.builderlauncher.calendar

data class CalendarSelection(
    val restrict: Boolean = false,
    val ids: Set<Long> = emptySet(),
) {
    fun allows(calendarId: Long): Boolean = !restrict || calendarId in ids

    fun checked(calendar: DeviceCalendar): Boolean =
        if (restrict) calendar.id in ids else calendar.visible

    fun toggle(id: Long, calendars: List<DeviceCalendar>): CalendarSelection {
        val visibleIds = calendars.filter { it.visible }.map { it.id }.toSet()
        val current = if (restrict) ids else visibleIds
        val next = if (id in current) current - id else current + id
        return if (next == visibleIds) CalendarSelection() else CalendarSelection(restrict = true, ids = next)
    }

    companion object {
        fun decode(restrictRaw: String?, idsRaw: String?): CalendarSelection {
            val ids = idsRaw.orEmpty()
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
            return if (restrictRaw != "true") CalendarSelection() else CalendarSelection(restrict = true, ids = ids)
        }

        fun encodeRestrict(selection: CalendarSelection): String =
            if (selection.restrict) "true" else "false"

        fun encodeIds(selection: CalendarSelection): String =
            if (!selection.restrict) "" else selection.ids.sorted().joinToString(",")
    }
}
