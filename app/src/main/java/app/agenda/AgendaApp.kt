package app.agenda

import android.app.Application
import app.agenda.work.Refresh

/**
 * Runs once each time Android starts the app's process, before any screen or widget code.
 * Registered in AndroidManifest.xml (`android:name=".AgendaApp"`). Used here to start the background refresh jobs.
 */
class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Refresh.ensurePeriodic(this)
        Refresh.watchCalendar(this)
    }
}
