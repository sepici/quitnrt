package app.thelasthit.nrt.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.thelasthit.nrt.LastHitApp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Puts the reminders' checks on WorkManager, one unique job each, so a new time replaces the old.
 * WorkManager's timing is rough, which is fine for these (PLAN §13), and it keeps them through a
 * restart.
 */
class ReminderScheduler(private val context: Context, private val now: () -> Instant) {

    /** Runs [worker] at [at], replacing whatever was waiting under [name]. Null cancels it. */
    fun at(name: String, worker: Class<out ListenableWorker>, at: Instant?, data: Data = Data.EMPTY) {
        val manager = workManager() ?: return
        if (at == null) {
            manager.cancelUniqueWork(name)
            return
        }
        val delay = Duration.between(now(), at).let { if (it.isNegative) Duration.ZERO else it }
        val request = OneTimeWorkRequest.Builder(worker)
            .setInitialDelay(delay)
            .setInputData(data)
            .addTag(TAG)
            .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * WorkManager starts with the app. Where it hasn't, as in tests that don't set it up, the
     * reminders simply aren't scheduled.
     */
    private fun workManager(): WorkManager? = try {
        WorkManager.getInstance(context)
    } catch (e: IllegalStateException) {
        null
    }

    companion object {
        const val TAG = "reminders"
        const val CHECK_IN = "reminder-check-in"
        const val FORGOT = "reminder-forgot-to-log"
        const val RESULTS = "reminder-weekly-results"
        const val WIDGET_DAY = "widget-new-day"
        const val WIDGET_MOMENT = "widget-moment"

        fun held(id: Int) = "reminder-held-$id"
    }
}

/**
 * Each worker does its job through the app's [Reminders], which then books its next run. The job
 * runs to the end even if its booking is replaced meanwhile, since a replaced job is cancelled.
 */
private suspend fun ListenableWorker.withReminders(block: suspend Reminders.() -> Unit): ListenableWorker.Result {
    val app = applicationContext as LastHitApp
    withContext(NonCancellable) { app.reminders.block() }
    return ListenableWorker.Result.success()
}

/** The evening check-in. */
class CheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withReminders { checkIn() }
}

/** "Forgot to log". */
class ForgotToLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withReminders { forgotToLog() }
}

/** Brings in the week's result when it's due, and tells of it. */
class ResultWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withReminders { results() }
}

/** A result or badge held through quiet hours. */
class HeldNoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withReminders { Notice.fromData(inputData)?.let { send(it) } }
}

/** The widget at the day start, when today's total starts again, and once its "Logged" moment is over. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withReminders { refreshWidget(newDay = inputData.getBoolean(KEY_NEW_DAY, false)) }

    companion object {
        const val KEY_NEW_DAY = "new_day"
    }
}
