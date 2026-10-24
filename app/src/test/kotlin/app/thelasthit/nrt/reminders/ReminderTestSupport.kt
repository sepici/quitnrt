package app.thelasthit.nrt.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.UsualDay
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

/** WorkManager's test set-up, which runs work at once on the test's thread when it's due. */
fun TestApp.startWorkManager() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        this,
        Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
    )
}

/** Notifications allowed, as on a phone where the permission was granted, or not. */
fun TestApp.allowNotifications(allowed: Boolean = true) {
    if (allowed) {
        shadowOf(this).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        shadowOf(this).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
}

val TestApp.notifications: ShadowNotificationManager
    get() = shadowOf(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

fun TestApp.clearNotifications() = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

/** The notification showing under [id], if any. */
fun TestApp.notification(id: Int): Notification? = notifications.getNotification(id)

/** Waits up to [timeoutMs] for a notification under [id], for nudges that follow a log on another thread. */
fun TestApp.awaitNotification(id: Int, timeoutMs: Long = 5_000, matching: (Notification) -> Boolean = { true }): Notification? {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        notification(id)?.takeIf(matching)?.let { return it }
        Thread.sleep(20)
    }
    return null
}

val Notification.title: String? get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
val Notification.text: String? get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
val Notification.actionLabels: List<String> get() = actions.orEmpty().map { it.title.toString() }

/** The clock at [at] on London's wall clock. */
fun TestApp.setTime(at: String) {
    clock.now = TestApp.london(at)
}

/**
 * A 12-week plan from Saturday 24 October, down from a typed usual day of 8 pieces of 4 mg gum:
 * week 1 is 6 a day, a 24 mg guide and 168 mg for the week.
 */
fun TestApp.startGumPlan(): ProgrammeRecord = runBlocking {
    val gum = checkNotNull(graph.products.get(GUM_4))
    graph.programmes.keepUsualDay(UsualDay.typed(gum to 8))
    graph.programmes.startPlan(checkNotNull(graph.programmes.buildPlan()))
}

/** [pieces] of 4 mg gum at [at] on London's wall clock, logged in the app. */
fun TestApp.chew(at: String, pieces: Int = 1): Entry = runBlocking {
    val result = graph.entries.log(LogRequest(GUM_4, quantity = pieces, at = TestApp.london(at), allowDuplicate = true))
    (result as LogResult.Saved).entry
}

/** The state of the job booked under [name], if there is one. */
fun TestApp.work(name: String): WorkInfo? = WorkManager.getInstance(this).getWorkInfosForUniqueWork(name).get().lastOrNull()
