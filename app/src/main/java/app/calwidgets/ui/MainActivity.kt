package app.calwidgets.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.calwidgets.data.CalendarRepository
import app.calwidgets.refresh.WidgetRefresh
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { MainScreen() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
        if (ok) scope.launch { WidgetRefresh.updateAll(context.applicationContext) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Calendar Widgets", style = MaterialTheme.typography.headlineMedium)

        Section("1. Calendar access") {
            if (granted) {
                Text("Allowed. The widgets show the calendars synced to this phone, including Google Calendar.")
            } else {
                Text("The widgets need permission to read your calendar. Events never leave this phone.")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("Allow calendar access")
                }
                if (askedOnce) {
                    Text(
                        "If no prompt appeared, allow Calendar under Permissions in the app's settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { context.startActivity(appSettingsIntent(context.packageName)) }) {
                        Text("Open app settings")
                    }
                }
            }
        }

        Section("2. Add a widget") {
            Text(
                "Touch and hold an empty spot on the home screen, tap Widgets, find Calendar Widgets and " +
                    "drag Up next, Agenda or Month onto the screen. Drag the handles to resize.",
            )
        }

        Section("3. Change the look") {
            Text(
                "Touch and hold a widget and tap Settings to switch between the Android and the iOS-like " +
                    "style, and to choose which calendars it shows.",
            )
        }

        Section("If a widget looks out of date") {
            Text(
                "Widgets refresh when the calendar changes and at least every 30 minutes. Battery saving " +
                    "can delay this; setting this app's battery usage to Unrestricted helps.",
            )
            Button(onClick = { scope.launch { WidgetRefresh.updateAll(context.applicationContext) } }) {
                Text("Refresh widgets now")
            }
            OutlinedButton(onClick = { context.startActivity(appSettingsIntent(context.packageName)) }) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
