package app.agenda.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.agenda.data.CalendarRepository
import app.agenda.widget.AgendaWidget
import java.util.concurrent.TimeUnit

/** Redraws the widget, then schedules the next redraw for when an event is due to drop off. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AgendaWidget().updateAll(applicationContext)
        if (CalendarRepository(applicationContext).hasPermission()) {
            val next = CalendarRepository(applicationContext).snapshot().nextChangeMillis
            val delay = (next - System.currentTimeMillis()).coerceAtLeast(MIN_DELAY_MS)
            Refresh.enqueueBoundary(applicationContext, delay + SLACK_MS)
        }
        return Result.success()
    }

    private companion object {
        const val MIN_DELAY_MS = 60_000L
        const val SLACK_MS = 2_000L
    }
}

object Refresh {
    private const val PERIODIC = "agenda-periodic"
    private const val BOUNDARY = "agenda-boundary"
    private const val NOW = "agenda-now"

    /** Safety net for missed broadcasts; the boundary job handles the exact drop-off times. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueBoundary(
        context: Context,
        delayMillis: Long,
    ) {
        val request =
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BOUNDARY, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Calendar provider changes, clock changes and date rollover. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Refresh.now(context)
    }
}
