package xyz.cdr.builderlauncher.calendar

data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)
