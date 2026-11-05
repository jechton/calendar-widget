package app.agenda.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.agenda.data.CalendarRepository
import app.agenda.widget.redrawWidgets
import java.util.concurrent.TimeUnit

/** Redraws the widget, then schedules the next redraw for when an event is due to drop off. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        redrawWidgets(applicationContext)
        Refresh.watchCalendar(applicationContext)
        val repo = CalendarRepository(applicationContext)
        if (repo.hasPermission()) {
            val next = repo.snapshot().nextChangeMillis
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

/**
 * Starts the background jobs that keep the widget current. Android may kill a process at any time, so jobs go
 * through WorkManager, which stores them and runs them later even after a restart. Each job has a unique name:
 * enqueueing the same name again replaces or keeps the old job instead of piling up copies.
 */
object Refresh {
    private const val PERIODIC = "agenda-periodic"
    private const val BOUNDARY = "agenda-boundary"
    private const val NOW = "agenda-now"
    private const val WATCH = "agenda-watch"

    /** Safety net for missed broadcasts; the boundary job handles the exact drop-off times. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The PROVIDER_CHANGED broadcast is unreliable on modern Android; a content trigger fires once per change. */
    fun watchCalendar(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
                        .setTriggerContentMaxDelay(10, TimeUnit.SECONDS)
                        .build(),
                )
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WATCH, ExistingWorkPolicy.REPLACE, request)
    }

    /** Redraw as soon as possible. */
    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Redraw once after [delayMillis], when the next event is due to drop off the widget. */
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

/**
 * Receives system broadcasts: calendar provider changes, clock changes and date rollover.
 * The filters are declared in AndroidManifest.xml. The work itself is handed to [Refresh] because a receiver
 * must finish within a few seconds.
 */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Refresh.now(context)
    }
}
