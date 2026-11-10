package app.calwidgets.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.calwidgets.core.colorsByDay
import app.calwidgets.core.monthGrid
import app.calwidgets.core.remainingOnDay
import app.calwidgets.core.weekdayOrder
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** Large widget: a month grid with event dots and month navigation. */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadWidgetData(context, prefs, monthView = true)
        provideContent {
            WidgetFrame(
                initial = initial,
                load = { loadWidgetData(context, it, monthView = true) },
                padding = 10.dp,
            ) { data, palette, style ->
                MonthContent(data, palette, style)
            }
        }
    }
}

class MonthReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

private val STEP = ActionParameters.Key<Int>("step")

/** Moves the month widget by "step" months; a step of 0 returns to the current month. */
class MonthNavAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val step = parameters[STEP] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetKeys.MONTH_OFFSET] = if (step == 0) 0 else (prefs[WidgetKeys.MONTH_OFFSET] ?: 0) + step
        }
        MonthWidget().update(context, glanceId)
    }
}

private fun navigate(step: Int) = actionRunCallback<MonthNavAction>(actionParametersOf(STEP to step))

@Composable
private fun MonthContent(data: WidgetData, palette: Palette, style: WidgetStyle) {
    val locale = Locale.getDefault()
    val size = LocalSize.current
    val weeks = monthGrid(data.month, data.firstDayOfWeek)
    val dots = colorsByDay(
        data.events,
        weeks.first().first().date,
        weeks.last().last().date,
        data.zone,
    )
    val showsCurrentMonth = data.month == YearMonth.from(data.today)
    val todayEvents = if (showsCurrentMonth && size.height >= 300.dp) {
        remainingOnDay(data.events, data.today, data.zone, data.nowMillis).take(2)
    } else {
        emptyList()
    }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Title and navigation.
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(data.month)
            Text(
                text = if (style == WidgetStyle.IOS) title.uppercase(locale) else title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(navigate(0)),
                style = TextStyle(
                    color = if (style == WidgetStyle.IOS) palette.accent else palette.text,
                    fontSize = if (style == WidgetStyle.IOS) 12.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            NavArrow("‹", -1, palette)
            NavArrow("›", 1, palette)
        }

        // Weekday initials.
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
            weekdayOrder(data.firstDayOfWeek).forEach { weekday ->
                Text(
                    text = weekday.getDisplayName(DateTextStyle.NARROW, locale),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        // Weeks.
        weeks.forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { cell ->
                    val isToday = cell.date == data.today
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(openDay(cell.date, data)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val numberBox = GlanceModifier.width(24.dp).height(24.dp)
                        Box(
                            modifier = if (isToday) numberBox.background(palette.accent).cornerRadius(12.dp) else numberBox,
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cell.date.dayOfMonth.toString(),
                                maxLines = 1,
                                style = TextStyle(
                                    color = when {
                                        isToday -> palette.onAccent
                                        cell.inMonth -> palette.text
                                        else -> palette.textSecondary
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                        // Event dots; the row keeps its height when there are none.
                        Row(
                            modifier = GlanceModifier.height(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dots[cell.date].orEmpty().forEachIndexed { index, color ->
                                if (index > 0) Spacer(GlanceModifier.width(2.dp))
                                Dot(ColorProvider(Color(color)), 4.dp)
                            }
                        }
                    }
                }
            }
        }

        // Today's remaining events, when the widget is tall enough.
        if (todayEvents.isNotEmpty()) {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp)) {
                todayEvents.forEach { event ->
                    EventRow(event, data, palette, modifier = GlanceModifier.padding(top = 4.dp), titleSize = 13)
                }
            }
        }
    }
}

@Composable
private fun NavArrow(symbol: String, step: Int, palette: Palette) {
    Box(
        modifier = GlanceModifier.width(40.dp).height(28.dp).clickable(navigate(step)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = TextStyle(color = palette.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
    }
}
