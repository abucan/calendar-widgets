package app.calwidgets.widgets

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.calwidgets.core.CalendarEvent
import app.calwidgets.core.weekOf
import app.calwidgets.data.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

enum class WidgetStyle { NATIVE, IOS }

/** Per-widget settings, stored in each widget's Glance state. */
object WidgetKeys {
    val STYLE = stringPreferencesKey("style")
    val HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendars")

    /** Months away from the current month shown by the month widget. */
    val MONTH_OFFSET = intPreferencesKey("month_offset")

    /** Day shown by the week widget, as an epoch day; ignored once it is outside the current week. */
    val SELECTED_DAY = longPreferencesKey("selected_day")

    /** Changed on every refresh so the widget reloads its events. */
    val TICK = longPreferencesKey("tick")
}

fun Preferences.widgetStyle(): WidgetStyle =
    if (this[WidgetKeys.STYLE] == WidgetStyle.IOS.name) WidgetStyle.IOS else WidgetStyle.NATIVE

fun Preferences.hiddenCalendars(): Set<Long> =
    this[WidgetKeys.HIDDEN_CALENDARS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

/** Everything a widget needs to draw itself, read at one moment. */
data class WidgetData(
    val hasPermission: Boolean,
    val nowMillis: Long,
    val zone: ZoneId,
    val today: LocalDate,
    val use24h: Boolean,
    val firstDayOfWeek: DayOfWeek,
    val month: YearMonth,
    /** Day whose events the week widget lists; always within the current week. */
    val selectedDay: LocalDate,
    val events: List<CalendarEvent>,
)

/** How many days ahead the Up next and Agenda widgets look. */
const val DAYS_AHEAD = 7L

/** Which days a widget needs events for. */
enum class WidgetRange {
    /** Today and the following [DAYS_AHEAD] days. */
    UPCOMING,

    /** The month selected in the widget, including the neighbouring days shown in its grid. */
    MONTH,

    /** The whole current week. */
    WEEK,
}

/** Loads the events of [range] for a widget whose stored settings are [prefs]. */
suspend fun loadWidgetData(context: Context, prefs: Preferences, range: WidgetRange): WidgetData =
    withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val month = YearMonth.from(today).plusMonths((prefs[WidgetKeys.MONTH_OFFSET] ?: 0).toLong())
        val week = weekOf(today, firstDayOfWeek)
        val selectedDay = prefs[WidgetKeys.SELECTED_DAY]
            ?.let { LocalDate.ofEpochDay(it) }
            ?.takeIf { it in week }
            ?: today
        val (from, to) = when (range) {
            WidgetRange.UPCOMING -> today to today.plusDays(DAYS_AHEAD)
            // The grid also shows the tail of the previous and the start of the next month.
            WidgetRange.MONTH ->
                minOf(month.atDay(1).minusDays(6), today) to maxOf(month.atEndOfMonth().plusDays(6), today)
            WidgetRange.WEEK -> week.first() to week.last()
        }
        WidgetData(
            hasPermission = CalendarRepository.hasPermission(context),
            nowMillis = now,
            zone = zone,
            today = today,
            use24h = DateFormat.is24HourFormat(context),
            firstDayOfWeek = firstDayOfWeek,
            month = month,
            selectedDay = selectedDay,
            events = CalendarRepository.events(context, from, to, zone, prefs.hiddenCalendars()),
        )
    }
