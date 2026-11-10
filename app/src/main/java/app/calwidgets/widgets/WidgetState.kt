package app.calwidgets.widgets

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.calwidgets.core.CalendarEvent
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
    val events: List<CalendarEvent>,
)

/** How many days ahead the Up next and Agenda widgets look. */
const val DAYS_AHEAD = 7L

/**
 * Loads events for a widget. With [monthView] the range is the month selected
 * in [prefs], including the days of neighbouring months shown in the grid;
 * otherwise it is today and the following [DAYS_AHEAD] days.
 */
suspend fun loadWidgetData(context: Context, prefs: Preferences, monthView: Boolean): WidgetData =
    withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val month = YearMonth.from(today).plusMonths((prefs[WidgetKeys.MONTH_OFFSET] ?: 0).toLong())
        val (from, to) = if (monthView) {
            // The grid also shows the tail of the previous and the start of the next month.
            val first = minOf(month.atDay(1).minusDays(6), today)
            val last = maxOf(month.atEndOfMonth().plusDays(6), today)
            first to last
        } else {
            today to today.plusDays(DAYS_AHEAD)
        }
        WidgetData(
            hasPermission = CalendarRepository.hasPermission(context),
            nowMillis = now,
            zone = zone,
            today = today,
            use24h = DateFormat.is24HourFormat(context),
            firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            month = month,
            events = CalendarRepository.events(context, from, to, zone, prefs.hiddenCalendars()),
        )
    }
