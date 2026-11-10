package app.calwidgets.core

import java.time.LocalDate
import java.time.ZoneId

/** All-day events first, then by start time, then by title. */
val eventOrder: Comparator<CalendarEvent> =
    compareByDescending<CalendarEvent> { it.allDay }
        .thenBy { it.startMillis }
        .thenBy { it.title }

fun eventsOnDay(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): List<CalendarEvent> =
    events.filter { it.occursOn(day, zone) }.sortedWith(eventOrder)

/**
 * Events of [day] that are still relevant at [nowMillis]: all-day events and
 * timed events that have not finished yet.
 */
fun remainingOnDay(
    events: List<CalendarEvent>,
    day: LocalDate,
    zone: ZoneId,
    nowMillis: Long,
): List<CalendarEvent> =
    eventsOnDay(events, day, zone).filter { it.allDay || it.endMillis > nowMillis }

/**
 * The event to show as "up next": the running or upcoming timed event that
 * starts first. An event already in progress wins over a later one.
 */
fun nextTimedEvent(events: List<CalendarEvent>, nowMillis: Long): CalendarEvent? =
    events.asSequence()
        .filter { !it.allDay && it.endMillis > nowMillis }
        .minWithOrNull(compareBy<CalendarEvent> { it.startMillis }.thenBy { it.endMillis })

/**
 * The moment after [nowMillis] at which a widget's content changes because an
 * event starts or ends, or null if there is none in [events].
 */
fun nextChangeMillis(events: List<CalendarEvent>, nowMillis: Long): Long? =
    events.asSequence()
        .filter { !it.allDay }
        .flatMap { sequenceOf(it.startMillis, it.endMillis) }
        .filter { it > nowMillis }
        .minOrNull()

/** Up to [max] distinct event colours for each day in [from]..[to]. */
fun colorsByDay(
    events: List<CalendarEvent>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId,
    max: Int = 3,
): Map<LocalDate, List<Int>> {
    val result = LinkedHashMap<LocalDate, MutableList<Int>>()
    for (event in events.sortedWith(eventOrder)) {
        var day = maxOf(event.firstDay(zone), from)
        val last = minOf(event.lastDay(zone), to)
        while (!day.isAfter(last)) {
            val colors = result.getOrPut(day) { mutableListOf() }
            if (colors.size < max && event.color !in colors) colors += event.color
            day = day.plusDays(1)
        }
    }
    return result
}
