package app.calwidgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun formatTime(millis: Long, zone: ZoneId, use24h: Boolean, locale: Locale = Locale.getDefault()): String {
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(millis).atZone(zone))
}

/** "09:00 – 10:30", or "All day" for all-day events. */
fun formatTimeRange(
    event: CalendarEvent,
    zone: ZoneId,
    use24h: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    if (event.allDay) return "All day"
    val start = formatTime(event.startMillis, zone, use24h, locale)
    if (event.endMillis <= event.startMillis) return start
    return "$start – ${formatTime(event.endMillis, zone, use24h, locale)}"
}

/** "Now", "in 25 min", "in 2 h 5 min", "Tomorrow", "in 3 days". */
fun formatCountdown(event: CalendarEvent, nowMillis: Long, zone: ZoneId): String {
    if (event.startMillis <= nowMillis) return "Now"
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, event.firstDay(zone))
    // Round up, so 59 s before the start reads "in 1 min", not "in 0 min".
    val minutes = (event.startMillis - nowMillis + 59_999) / 60_000
    return when {
        minutes < 60 -> "in $minutes min"
        days == 0L || minutes < 6 * 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "in $h h" else "in $h h $m min"
        }
        days == 1L -> "Tomorrow"
        else -> "in $days days"
    }
}

/** "Today", "Tomorrow", or a date such as "Fri 13 Nov". */
fun formatDayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
    when (ChronoUnit.DAYS.between(today, day)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day)
    }
