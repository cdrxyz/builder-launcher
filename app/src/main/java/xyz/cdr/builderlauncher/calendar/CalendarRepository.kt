package xyz.cdr.builderlauncher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class CalendarRepository(context: Context) {
    private val app = context.applicationContext
    private val _current = MutableStateFlow<UpcomingEvent?>(null)
    val current: StateFlow<UpcomingEvent?> = _current.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun refresh(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            _current.value = null
            return@withContext
        }
        _current.value = runCatching { UpcomingEvents.pick(load(now), now) }.getOrNull()
    }

    fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChange()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChange()
            }
        }
        return try {
            app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            ({ app.contentResolver.unregisterContentObserver(observer) })
        } catch (_: SecurityException) {
            ({})
        }
    }

    fun viewIntent(event: UpcomingEvent): Intent =
        Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun load(now: Long): List<UpcomingEvent> {
        val start = now - UpcomingEvents.LOOKBACK_MS
        val end = now + UpcomingEvents.HORIZON_MS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, start)
            ContentUris.appendId(this, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection =
            "${CalendarContract.Instances.VISIBLE}=1 AND ${CalendarContract.Instances.STATUS}!=${CalendarContract.Events.STATUS_CANCELED}"
        return try {
            app.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                buildList {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIdx)?.trim().orEmpty()
                        if (title.isEmpty()) continue
                        add(
                            UpcomingEvent(
                                eventId = cursor.getLong(idIdx),
                                title = title,
                                begin = cursor.getLong(beginIdx),
                                end = cursor.getLong(endIdx),
                                allDay = cursor.getInt(allDayIdx) == 1,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
