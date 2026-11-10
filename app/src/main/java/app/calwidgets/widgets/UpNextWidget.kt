package app.calwidgets.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import app.calwidgets.core.eventsOnDay
import app.calwidgets.core.formatCountdown
import app.calwidgets.core.nextTimedEvent
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** Small widget: today's date and the next event with a countdown. */
class UpNextWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadWidgetData(context, prefs, WidgetRange.UPCOMING)
        provideContent {
            WidgetFrame(initial, load = { loadWidgetData(context, it, WidgetRange.UPCOMING) }) { data, palette, style ->
                UpNextContent(data, palette, style)
            }
        }
    }
}

class UpNextReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

@Composable
private fun UpNextContent(data: WidgetData, palette: Palette, style: WidgetStyle) {
    val locale = Locale.getDefault()
    val size = LocalSize.current
    val tall = size.height >= 150.dp
    val next = nextTimedEvent(data.events, data.nowMillis)
    val allDayToday = eventsOnDay(data.events, data.today, data.zone).filter { it.allDay }

    Column(modifier = GlanceModifier.fillMaxSize().clickable(openDay(data.today, data))) {
        // Header: weekday and date.
        if (style == WidgetStyle.IOS) {
            Text(
                text = data.today.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale).uppercase(locale),
                maxLines = 1,
                style = TextStyle(color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            if (tall) {
                Text(
                    text = data.today.dayOfMonth.toString(),
                    style = TextStyle(color = palette.text, fontSize = 30.sp, fontWeight = FontWeight.Medium),
                )
            }
        } else {
            Text(
                text = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(data.today),
                maxLines = 1,
                style = TextStyle(color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        if (tall && allDayToday.isNotEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dot(ColorProvider(Color(allDayToday.first().color)), 6.dp)
                Spacer(GlanceModifier.width(6.dp))
                val more = if (allDayToday.size > 1) "  +${allDayToday.size - 1}" else ""
                Text(
                    text = allDayToday.first().title + more,
                    maxLines = 1,
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }

        if (next == null) {
            Text(
                text = if (allDayToday.isEmpty()) "No upcoming events" else "No more events",
                style = TextStyle(color = palette.textSecondary, fontSize = 14.sp),
            )
        } else {
            val label = formatCountdown(next, data.nowMillis, data.zone)
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(2.dp))
            EventRow(
                event = next,
                data = data,
                palette = palette,
                titleSize = 15,
                titleLines = if (tall) 2 else 1,
                showLocation = tall,
            )
        }
    }
}
