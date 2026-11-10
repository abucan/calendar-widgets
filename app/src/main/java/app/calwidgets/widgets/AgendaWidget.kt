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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
import androidx.glance.text.TextStyle
import app.calwidgets.core.CalendarEvent
import app.calwidgets.core.eventsOnDay
import app.calwidgets.core.formatDayLabel
import app.calwidgets.core.remainingOnDay
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** Medium widget: today's date beside a scrolling list of upcoming events. */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadWidgetData(context, prefs, monthView = false)
        provideContent {
            WidgetFrame(initial, load = { loadWidgetData(context, it, monthView = false) }) { data, palette, style ->
                AgendaContent(data, palette, style)
            }
        }
    }
}

class AgendaReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

/** A row of the agenda list: a day heading or an event. */
private sealed interface AgendaItem {
    data class Header(val text: String) : AgendaItem
    data class Entry(val event: CalendarEvent) : AgendaItem
}

private const val MAX_ITEMS = 40

private fun agendaItems(data: WidgetData, locale: Locale): List<AgendaItem> {
    val items = ArrayList<AgendaItem>()
    for (offset in 0..DAYS_AHEAD) {
        val day = data.today.plusDays(offset)
        val events = if (offset == 0L) {
            remainingOnDay(data.events, day, data.zone, data.nowMillis)
        } else {
            // A multi-day event is listed on its first visible day only.
            eventsOnDay(data.events, day, data.zone).filter { it.firstDay(data.zone) == day }
        }
        if (events.isEmpty()) continue
        if (offset != 0L) items += AgendaItem.Header(formatDayLabel(day, data.today, locale))
        events.forEach { items += AgendaItem.Entry(it) }
        if (items.size >= MAX_ITEMS) break
    }
    return items
}

@Composable
private fun AgendaContent(data: WidgetData, palette: Palette, style: WidgetStyle) {
    val locale = Locale.getDefault()
    val wide = LocalSize.current.width >= 230.dp
    val items = agendaItems(data, locale)

    if (wide) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Column(
                modifier = GlanceModifier.width(76.dp).fillMaxHeight().clickable(openDay(data.today, data)),
            ) {
                Text(
                    text = data.today.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).uppercase(locale),
                    maxLines = 1,
                    style = TextStyle(color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = data.today.dayOfMonth.toString(),
                    maxLines = 1,
                    style = TextStyle(
                        color = palette.text,
                        fontSize = 36.sp,
                        fontWeight = if (style == WidgetStyle.IOS) FontWeight.Medium else FontWeight.Bold,
                    ),
                )
                Text(
                    text = DateTimeFormatter.ofPattern("MMMM", locale).format(data.today),
                    maxLines = 1,
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            AgendaList(items, data, palette, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(data.today),
                maxLines = 1,
                modifier = GlanceModifier.clickable(openDay(data.today, data)),
                style = TextStyle(color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            AgendaList(items, data, palette, GlanceModifier.defaultWeight().fillMaxWidth())
        }
    }
}

@Composable
private fun AgendaList(rows: List<AgendaItem>, data: WidgetData, palette: Palette, modifier: GlanceModifier) {
    if (rows.isEmpty()) {
        Box(modifier = modifier) {
            Text(
                text = "No events in the next week",
                style = TextStyle(color = palette.textSecondary, fontSize = 14.sp),
            )
        }
        return
    }
    LazyColumn(modifier = modifier) {
        items(rows) { item ->
            when (item) {
                is AgendaItem.Header -> Text(
                    text = item.text,
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                is AgendaItem.Entry -> EventRow(
                    event = item.event,
                    data = data,
                    palette = palette,
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}
