package app.calwidgets

import android.app.Application
import app.calwidgets.refresh.WidgetRefresh

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefresh.schedule(this)
    }
}
