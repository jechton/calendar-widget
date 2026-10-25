package app.agenda

import android.app.Application
import app.agenda.work.Refresh

class AgendaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Refresh.ensurePeriodic(this)
    }
}
