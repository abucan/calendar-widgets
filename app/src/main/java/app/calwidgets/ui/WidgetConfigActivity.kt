package app.calwidgets.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.calwidgets.data.CalendarInfo
import app.calwidgets.data.CalendarRepository
import app.calwidgets.refresh.WidgetRefresh
import app.calwidgets.widgets.WidgetKeys
import app.calwidgets.widgets.WidgetStyle
import app.calwidgets.widgets.hiddenCalendars
import app.calwidgets.widgets.widgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings of one widget: its style and which calendars it shows. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Leaving without saving keeps the widget as it is.
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val glanceId = try {
                GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            } catch (_: IllegalArgumentException) {
                finish()
                return@launch
            }
            val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            val calendars = withContext(Dispatchers.IO) {
                CalendarRepository.calendars(this@WidgetConfigActivity)
            }
            setContent {
                AppTheme {
                    ConfigScreen(
                        initialStyle = prefs.widgetStyle(),
                        initialHidden = prefs.hiddenCalendars(),
                        calendars = calendars,
                        onSave = { style, hidden -> save(glanceId, style, hidden) },
                    )
                }
            }
        }
    }

    private suspend fun save(glanceId: GlanceId, style: WidgetStyle, hidden: Set<Long>) {
        updateAppWidgetState(this, glanceId) { prefs ->
            prefs[WidgetKeys.STYLE] = style.name
            prefs[WidgetKeys.HIDDEN_CALENDARS] = hidden.map { it.toString() }.toSet()
        }
        WidgetRefresh.updateAll(applicationContext)
        finish()
    }
}

@Composable
private fun ConfigScreen(
    initialStyle: WidgetStyle,
    initialHidden: Set<Long>,
    calendars: List<CalendarInfo>,
    onSave: suspend (WidgetStyle, Set<Long>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(initialStyle) }
    var hidden by remember { mutableStateOf(initialHidden) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Widget settings", style = MaterialTheme.typography.headlineSmall)

            Text("Style", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            StyleOption(
                title = "Android",
                description = "Follows your wallpaper colours and dark mode",
                selected = style == WidgetStyle.NATIVE,
                onClick = { style = WidgetStyle.NATIVE },
            )
            StyleOption(
                title = "iOS-like",
                description = "White or black card with a red accent",
                selected = style == WidgetStyle.IOS,
                onClick = { style = WidgetStyle.IOS },
            )

            Text("Calendars", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            if (calendars.isEmpty()) {
                Text(
                    "No calendars found. Allow calendar access in the Calendar Widgets app first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            calendars.forEach { calendar ->
                val shown = calendar.id !in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hidden = if (shown) hidden + calendar.id else hidden - calendar.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = shown, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                        if (calendar.account.isNotEmpty() && calendar.account != calendar.name) {
                            Text(
                                calendar.account,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { scope.launch { onSave(style, hidden) } },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun StyleOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
