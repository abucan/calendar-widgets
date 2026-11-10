package app.calwidgets.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.calwidgets.core.nextChangeMillis
import app.calwidgets.core.nextTimedEvent
import app.calwidgets.data.CalendarRepository
import app.calwidgets.widgets.AgendaWidget
import app.calwidgets.widgets.MonthWidget
import app.calwidgets.widgets.UpNextWidget
import app.calwidgets.widgets.WeekWidget
import app.calwidgets.widgets.WidgetKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps the widgets current. They are redrawn
 *  - when the calendar changes (WorkManager content trigger, plus the
 *    calendar provider's own broadcast where the system delivers it),
 *  - when an event starts or ends, every minute during the hour before the
 *    next event (for the countdown), and at midnight (alarm),
 *  - every 30 minutes as a fallback (periodic work),
 *  - after a reboot, an app update, or a clock, time zone or language change.
 */
object WidgetRefresh {
    private const val PERIODIC_WORK = "periodic-refresh"
    private const val CALENDAR_WORK = "calendar-changed"
    private const val MINUTE = 60_000L

    private val widgets: List<GlanceAppWidget>
        get() = listOf(UpNextWidget(), AgendaWidget(), WeekWidget(), MonthWidget())

    /** Redraws every placed widget with fresh calendar data. */
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val tick = System.currentTimeMillis()
        for (widget in widgets) {
            for (id in manager.getGlanceIds(widget.javaClass)) {
                // Changing the stored state is what makes the widget reload its events.
                updateAppWidgetState(context, id) { it[WidgetKeys.TICK] = tick }
                widget.update(context, id)
            }
        }
    }

    /** Sets up all background refreshes. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build(),
        )
        watchCalendar(context, ExistingWorkPolicy.KEEP)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { scheduleAlarm(context) }
    }

    /** Runs [CalendarChangedWorker] the next time the calendar provider's data changes. */
    fun watchCalendar(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CALENDAR_WORK,
            policy,
            OneTimeWorkRequestBuilder<CalendarChangedWorker>().setConstraints(constraints).build(),
        )
    }

    /**
     * Sets the alarm for the next moment the widgets' content changes. The
     * alarm does not wake a sleeping phone; a missed one fires as soon as the
     * phone wakes up, which is when the widgets become visible again.
     */
    suspend fun scheduleAlarm(context: Context) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        var next = midnight
        val events = CalendarRepository.events(context, today, today.plusDays(1), zone)
        nextChangeMillis(events, now)?.let { next = minOf(next, it) }

        // Countdown: tick every minute during the hour before the next event,
        // otherwise wake up when that hour begins.
        val upcoming = nextTimedEvent(events.filter { it.startMillis > now }, now)
        if (upcoming != null) {
            val countdownStart = upcoming.startMillis - 60 * MINUTE
            next = minOf(next, if (countdownStart > now) countdownStart else (now / MINUTE + 1) * MINUTE)
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.set(AlarmManager.RTC, next + 500, alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RefreshReceiver::class.java).setAction(RefreshReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Alarm, boot, clock and calendar-provider broadcasts all end up here. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetRefresh.updateAll(appContext)
                if (intent.action == ACTION_ALARM) {
                    WidgetRefresh.scheduleAlarm(appContext)
                } else {
                    WidgetRefresh.schedule(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "app.calwidgets.REFRESH_ALARM"
    }
}

/** Periodic fallback refresh. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresh.updateAll(applicationContext)
        WidgetRefresh.scheduleAlarm(applicationContext)
        return Result.success()
    }
}

/** Runs when the calendar changed, then starts watching for the next change. */
class CalendarChangedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresh.updateAll(applicationContext)
        WidgetRefresh.scheduleAlarm(applicationContext)
        // Queued behind this run, so it starts watching once this run has finished.
        WidgetRefresh.watchCalendar(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
