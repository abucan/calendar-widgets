package app.calwidgets.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.calwidgets.core.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

/** Reads the calendars already synced to the phone (Google Calendar included). */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Event occurrences touching the days [from]..[to]. Calendars hidden in the
     * calendar app, calendars in [hiddenCalendars] and declined invitations are
     * left out.
     */
    fun events(
        context: Context,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        hiddenCalendars: Set<Long> = emptySet(),
    ): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()

        // All-day events are stored in UTC, so query a day more on each side
        // and let CalendarEvent.occursOn decide.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin) }
            .also { ContentUris.appendId(it, end) }
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )

        val result = ArrayList<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val calendarId = c.getLong(7)
                    if (calendarId in hiddenCalendars) continue
                    if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val event = CalendarEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        startMillis = c.getLong(2),
                        endMillis = c.getLong(3),
                        allDay = c.getInt(4) != 0,
                        color = c.getInt(5) or 0xFF000000.toInt(),
                        location = c.getString(6)?.takeIf { it.isNotBlank() },
                        calendarId = calendarId,
                    )
                    val touchesRange = !event.lastDay(zone).isBefore(from) && !event.firstDay(zone).isAfter(to)
                    if (touchesRange) result += event
                }
            }
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the query.
        }
        return result
    }

    /** Calendars that are visible in the calendar app. */
    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val result = ArrayList<CalendarInfo>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        account = c.getString(2) ?: "",
                        color = c.getInt(3) or 0xFF000000.toInt(),
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return result
    }
}
