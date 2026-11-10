package app.calwidgets.widgets

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.calwidgets.core.CalendarEvent
import app.calwidgets.core.eventsOnDay
import app.calwidgets.core.formatCountdown
import app.calwidgets.core.formatTimeRange
import app.calwidgets.core.nextTimedEvent
import app.calwidgets.core.remainingOnDay
import app.calwidgets.core.weekOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Large widget: date header with an add button, the current week, and the
 * events of the day selected in the week strip (today unless another day was
 * tapped).
 */
class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadWidgetData(context, prefs, WidgetRange.WEEK)
        provideContent {
            WidgetFrame(
                initial = initial,
                load = { loadWidgetData(context, it, WidgetRange.WEEK) },
                padding = 16.dp,
            ) { data, palette, _ ->
                WeekContent(data, palette)
            }
        }
    }
}

class WeekReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

private val DAY = ActionParameters.Key<Long>("day")
private const val TODAY = -1L

/** Selects the day (an epoch day) whose events the week widget lists; [TODAY] clears the selection. */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[DAY] ?: TODAY
        updateAppWidgetState(context, glanceId) { prefs ->
            if (day == TODAY) prefs.remove(WidgetKeys.SELECTED_DAY) else prefs[WidgetKeys.SELECTED_DAY] = day
        }
        WeekWidget().update(context, glanceId)
    }
}

private fun select(day: LocalDate?): Action =
    actionRunCallback<SelectDayAction>(actionParametersOf(DAY to (day?.toEpochDay() ?: TODAY)))

/** Opens the calendar app's new-event screen for [day]. */
private fun addEvent(day: LocalDate, data: WidgetData): Action {
    val start = if (day == data.today) {
        // The next full hour.
        Instant.ofEpochMilli(data.nowMillis).atZone(data.zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
    } else {
        day.atTime(9, 0).atZone(data.zone)
    }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plusHours(1).toInstant().toEpochMilli())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}

@Composable
private fun WeekContent(data: WidgetData, palette: Palette) {
    val locale = Locale.getDefault()
    val day = data.selectedDay
    val isToday = day == data.today
    val events = if (isToday) {
        remainingOnDay(data.events, day, data.zone, data.nowMillis)
    } else {
        eventsOnDay(data.events, day, data.zone)
    }
    // Only the next event of today gets a countdown.
    val next = if (isToday) nextTimedEvent(events, data.nowMillis) else null

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header: big date, month, weekday, add button.
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = GlanceModifier.defaultWeight().clickable(select(null)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    maxLines = 1,
                    style = TextStyle(color = palette.text, fontSize = 38.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(
                        text = DateTimeFormatter.ofPattern("LLLL", locale).format(day),
                        maxLines = 1,
                        style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                    val weekday = day.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale)
                    Text(
                        text = if (isToday) "Today, $weekday" else weekday,
                        maxLines = 1,
                        style = TextStyle(color = palette.textSecondary, fontSize = 14.sp),
                    )
                }
            }
            Box(
                modifier = GlanceModifier
                    .width(46.dp)
                    .height(30.dp)
                    .background(palette.subtle)
                    .cornerRadius(15.dp)
                    .clickable(addEvent(day, data)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(color = palette.text, fontSize = 20.sp, textAlign = TextAlign.Center),
                )
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        // Week strip.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            weekOf(data.today, data.firstDayOfWeek).forEach { date ->
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                val dateIsToday = date == data.today
                val selected = date == day
                Column(
                    modifier = GlanceModifier.defaultWeight().clickable(select(date)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).uppercase(locale),
                        maxLines = 1,
                        style = TextStyle(
                            color = if (weekend) palette.textSecondary else palette.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    val pill = GlanceModifier.width(36.dp).height(26.dp)
                    Box(
                        modifier = when {
                            dateIsToday -> pill.background(palette.accent).cornerRadius(13.dp)
                            selected -> pill.background(palette.subtle).cornerRadius(13.dp)
                            else -> pill
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            maxLines = 1,
                            style = TextStyle(
                                color = when {
                                    dateIsToday -> palette.onAccent
                                    weekend -> palette.textSecondary
                                    else -> palette.text
                                },
                                fontSize = 15.sp,
                                fontWeight = if (dateIsToday) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(GlanceModifier.height(10.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(palette.subtle)) {}
        Spacer(GlanceModifier.height(8.dp))

        // Events of the selected day.
        if (events.isEmpty()) {
            Text(
                text = if (isToday) "No more events today" else "No events",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = palette.textSecondary, fontSize = 14.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(events) { event ->
                    WeekEventRow(
                        event = event,
                        countdown = if (event == next) formatCountdown(event, data.nowMillis, data.zone) else null,
                        data = data,
                        palette = palette,
                    )
                }
            }
        }
    }
}

/** An event with its time above its title, and an optional countdown beside the time. */
@Composable
private fun WeekEventRow(event: CalendarEvent, countdown: String?, data: WidgetData, palette: Palette) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(openEvent(event)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(36.dp)
                .background(ColorProvider(Color(event.color)))
                .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimeRange(event, data.zone, data.use24h),
                    maxLines = 1,
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.sp),
                )
                if (countdown != null) {
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        text = countdown,
                        maxLines = 1,
                        style = TextStyle(
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }
            }
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
