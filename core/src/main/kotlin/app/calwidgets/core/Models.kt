package app.calwidgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One occurrence of a calendar event.
 *
 * For all-day events the Android calendar provider stores [startMillis] and
 * [endMillis] as UTC midnights, with the end exclusive; [firstDay] and
 * [lastDay] hide that difference.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String? = null,
    val calendarId: Long = 0,
) {
    fun firstDay(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(startMillis).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    fun lastDay(zone: ZoneId): LocalDate {
        val first = firstDay(zone)
        if (endMillis <= startMillis) return first
        // The end is exclusive: an event ending at 00:00 does not touch that day.
        val last = Instant.ofEpochMilli(endMillis - 1)
            .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
        return if (last.isBefore(first)) first else last
    }

    fun occursOn(day: LocalDate, zone: ZoneId): Boolean =
        !day.isBefore(firstDay(zone)) && !day.isAfter(lastDay(zone))
}

data class DayCell(val date: LocalDate, val inMonth: Boolean)
