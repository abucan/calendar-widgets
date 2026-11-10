package app.calwidgets.widgets

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.calwidgets.R
import app.calwidgets.core.CalendarEvent
import app.calwidgets.core.formatTimeRange
import app.calwidgets.ui.MainActivity
import java.time.LocalDate

/** Colours of one widget style. */
data class Palette(
    val background: ColorProvider,
    val text: ColorProvider,
    val textSecondary: ColorProvider,
    /** Weekday label, today marker, navigation arrows. */
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    /** Dividers and quiet button backgrounds. */
    val subtle: ColorProvider,
)

@Composable
fun palette(style: WidgetStyle): Palette = when (style) {
    // Material You: follows the wallpaper colours and light/dark mode.
    WidgetStyle.NATIVE -> Palette(
        background = GlanceTheme.colors.widgetBackground,
        text = GlanceTheme.colors.onSurface,
        textSecondary = GlanceTheme.colors.onSurfaceVariant,
        accent = GlanceTheme.colors.primary,
        onAccent = GlanceTheme.colors.onPrimary,
        subtle = GlanceTheme.colors.secondaryContainer,
    )
    WidgetStyle.IOS -> Palette(
        background = dayNight(Color.White, Color(0xFF1C1C1E)),
        text = dayNight(Color.Black, Color.White),
        textSecondary = dayNight(Color(0xFF8A8A8E), Color(0xFF98989F)),
        accent = dayNight(Color(0xFFFF3B30), Color(0xFFFF453A)),
        onAccent = dayNight(Color.White, Color.White),
        subtle = dayNight(Color(0xFFE5E5EA), Color(0xFF3A3A3C)),
    )
}

private fun dayNight(day: Color, night: Color): ColorProvider =
    androidx.glance.color.ColorProvider(day = day, night = night)

/**
 * Shared frame of every widget: keeps the event data current, picks the
 * palette and draws the rounded background. [load] is re-run whenever the
 * widget's stored state changes, which every refresh does.
 */
@Composable
fun WidgetFrame(
    initial: WidgetData,
    load: suspend (Preferences) -> WidgetData,
    padding: Dp = 14.dp,
    content: @Composable (data: WidgetData, palette: Palette, style: WidgetStyle) -> Unit,
) {
    val prefs = currentState<Preferences>()
    val data by produceState(initial, prefs) { value = load(prefs) }
    val style = prefs.widgetStyle()
    GlanceTheme {
        val palette = palette(style)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(palette.background)
                .cornerRadius(R.dimen.widget_radius)
                .padding(padding),
        ) {
            if (data.hasPermission) {
                content(data, palette, style)
            } else {
                PermissionNeeded(palette)
            }
        }
    }
}

@Composable
private fun PermissionNeeded(palette: Palette) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tap to allow calendar access",
            style = TextStyle(color = palette.text, fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

/** A small filled circle; square on Android 11 and older, which cannot round corners. */
@Composable
fun Dot(color: ColorProvider, size: Dp) {
    Box(modifier = GlanceModifier.width(size).height(size).background(color).cornerRadius(size / 2)) {}
}

/** One event: colour bar, title and time (plus location when [showLocation]). */
@Composable
fun EventRow(
    event: CalendarEvent,
    data: WidgetData,
    palette: Palette,
    modifier: GlanceModifier = GlanceModifier,
    titleSize: Int = 14,
    titleLines: Int = 1,
    showLocation: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(openEvent(event)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(if (showLocation && event.location != null) 50.dp else 32.dp)
                .background(ColorProvider(Color(event.color)))
                .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = titleLines,
                style = TextStyle(color = palette.text, fontSize = titleSize.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = formatTimeRange(event, data.zone, data.use24h),
                maxLines = 1,
                style = TextStyle(color = palette.textSecondary, fontSize = 12.sp),
            )
            if (showLocation && event.location != null) {
                Text(
                    text = event.location!!,
                    maxLines = 1,
                    style = TextStyle(color = palette.textSecondary, fontSize = 12.sp),
                )
            }
        }
    }
}

/** Opens the event in the phone's calendar app. */
fun openEvent(event: CalendarEvent): Action {
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}

/** Opens [day] in the phone's calendar app. */
fun openDay(day: LocalDate, data: WidgetData): Action {
    val millis = day.atTime(8, 0).atZone(data.zone).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        .also { ContentUris.appendId(it, millis) }
        .build()
    val intent = Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}
