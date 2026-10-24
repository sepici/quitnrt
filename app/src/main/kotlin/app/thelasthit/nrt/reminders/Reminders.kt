package app.thelasthit.nrt.reminders

import android.content.Context
import androidx.work.Data
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.repo.NewDose
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear
import app.thelasthit.nrt.domain.game.NewUnlocks
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.reminders.AllowanceNudge
import app.thelasthit.nrt.domain.reminders.CheckIn
import app.thelasthit.nrt.domain.reminders.Delivery
import app.thelasthit.nrt.domain.reminders.ForgotToLog
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ResultNotices
import app.thelasthit.nrt.domain.reminders.TodayGuide
import app.thelasthit.nrt.domain.time.DayBoundary
import app.thelasthit.nrt.widget.LastHitWidget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The reminders (PLAN §13). Nudges that follow a log fire from the log itself, through
 * [app.thelasthit.nrt.data.repo.EntryRepository.logged], whichever way it came in. The rest are
 * checks on WorkManager: the evening check-in, "Forgot to log" and the weekly results. Each
 * respects its switch and quiet hours ([app.thelasthit.nrt.domain.reminders.ReminderSettings.delivery]).
 * Nothing goes out before set-up is finished.
 *
 * The game layer tells of badges and gear through [achievementsUnlocked].
 */
class Reminders(
    private val context: Context,
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val poster: NoticePoster = NoticePoster(context),
    private val scheduler: ReminderScheduler = ReminderScheduler(context, now = { graph.clock.instant() }),
) {
    private val store get() = graph.reminders
    private val texts get() = NoticeTexts(context.resources)

    /** The dose behind the allowance nudge in the shade, so taking it back takes the nudge too. */
    private val nudgedBy = MutableStateFlow<Entry?>(null)

    /** One telling of results at a time, so a result settled twice over is told once. */
    private val telling = Mutex()

    /**
     * Runs as long as the app does: makes the channels, nudges after each log, books the checks
     * again whenever what they depend on changes, and keeps the widget up to date.
     */
    suspend fun follow() = coroutineScope {
        poster.createChannels()
        launch { graph.entries.logged.collect { afterLog(it) } }
        launch { nudgedBy.collectLatest { entry -> if (entry != null) clearNudgeOnceGone(entry) } }
        launch {
            combine(
                store.reminders,
                graph.settings.settings.map { it.onboardingComplete to it.dayStart },
                graph.programmes.observeCurrent().map { record -> record?.plan?.weeks?.firstOrNull { it.result == WeekResult.PENDING } },
            ) { reminders, setUp, nextResult -> Triple(reminders, setUp, nextResult) }
                .distinctUntilChanged()
                .collect { reschedule() }
        }
        launch { LastHitWidget.follow(context, graph) }
    }

    // After a log.

    /**
     * The allowance nudge [dose] brings, if any, and what became of it: null when there's none.
     * It goes by the doses that stood when this one was saved, so one logged straight after
     * can't hide the line this one crossed. A dose logged also answers "Forgot to log".
     */
    suspend fun afterLog(dose: NewDose): Delivery? {
        poster.cancel(Notice.ID_FORGOT)
        if (!isSetUp()) return null
        val now = graph.clock.instant()
        val today = boundary().logicalDate(now, zone())
        val record = graph.programmes.current() ?: return null
        val plan = record.plan ?: return null
        val week = plan.weekOn(today) ?: return null
        val logged = dose.standing.filter { it.logicalDate in week }
        val nudge = AllowanceNudge.forLog(dose.entry, plan, record.status, logged, today) ?: return null
        return send(texts.nudge(nudge)).also { if (it == Delivery.Now) nudgedBy.value = dose.entry }
    }

    /**
     * Waits for [entry], whose log brought the nudge in the shade, to be taken back, by Undo or
     * deleting it, then clears the nudge, since its numbers no longer hold.
     */
    private suspend fun clearNudgeOnceGone(entry: Entry) {
        graph.entries.observeRange(entry.logicalDate, entry.logicalDate).first { day -> day.none { it.id == entry.id } }
        poster.cancel(Notice.ID_ALLOWANCE)
        nudgedBy.compareAndSet(entry, null)
    }

    // The checks on WorkManager.

    /** The evening check-in, if it's due and today hasn't had one. Then books the next. */
    suspend fun checkIn() {
        val settings = store.currentReminders()
        val now = graph.clock.instant()
        val zone = zone()
        val boundary = boundary()
        val today = boundary.logicalDate(now, zone)
        if (isSetUp() && store.currentMarks().checkInSentOn != today && CheckIn.isDue(settings.checkInTime, now, zone)) {
            val day = graph.entries.observeDay(today).first().entries
            val record = graph.programmes.current()
            val notice = texts.checkIn(CheckIn.of(day, TodayGuide.on(record?.plan, record?.status, today)))
            if (send(notice) == Delivery.Now) store.markCheckInSent(today)
        }
        scheduleCheckIn()
    }

    /** "Forgot to log", if it's past the usual time and nothing is logged today. Then books the next look. */
    suspend fun forgotToLog() {
        val now = graph.clock.instant()
        val zone = zone()
        val boundary = boundary()
        val today = boundary.logicalDate(now, zone)
        val entries = recentEntries(today)
        val marks = store.currentMarks()
        if (isSetUp() && store.currentReminders().isOn(Reminder.FORGOT_TO_LOG) &&
            ForgotToLog.shouldRemind(now, entries, boundary, zone, marks.forgotRemindedOn)
        ) {
            if (send(texts.forgotToLog()) == Delivery.Now) store.markForgotReminded(today)
        }
        scheduleForgot()
    }

    /**
     * Brings in the results that are due, tells of each one not told of yet, then books the next.
     * Results that come in while the reminder is off are noted all the same, so switching it on
     * later doesn't bring old news.
     */
    suspend fun results() {
        telling.withLock {
            val record = graph.programmes.current()
            val plan = record?.plan
            if (record != null && plan != null && isSetUp()) {
                val marks = store.currentMarks()
                val notices = ResultNotices.since(plan, marks.resultsNotifiedUpTo, graph.clock.instant())
                val name = record.demon?.name ?: graph.settings.current().demonName
                notices.forEach { send(texts.result(it, name)) }
                ResultNotices.notifiedUpTo(notices, marks.resultsNotifiedUpTo)?.let { store.markResultsNotified(it) }
            }
        }
        scheduleResults()
    }

    // For the game layer.

    /**
     * Tells of badges and gear that just unlocked, by their names. The game layer calls this once
     * it has stored them. More than [NewUnlocks.MAX_TOASTS] badges at once go out as one summary.
     */
    suspend fun achievementsUnlocked(badges: List<String>, gear: List<String>) {
        if (!isSetUp()) return
        val notice = texts.achievements(badges, gear, summary = badges.size > NewUnlocks.MAX_TOASTS) ?: return
        send(notice)
    }

    /** [achievementsUnlocked] for [unlocks], named by the game layer's own words. */
    suspend fun achievementsUnlocked(unlocks: NewUnlocks, badgeName: (Achievement) -> String, gearName: (Gear) -> String) =
        achievementsUnlocked(unlocks.achievements.map(badgeName), unlocks.gear.map(gearName))

    // Sending.

    /**
     * Sends [notice] as its reminder's switch and quiet hours say: now, held until quiet hours
     * end, or not at all. A notice that isn't a reminder, such as the tile's Undo, goes now.
     */
    suspend fun send(notice: Notice): Delivery {
        val reminder = notice.reminder
        val delivery = if (reminder == null) Delivery.Now else store.currentReminders().delivery(reminder, graph.clock.instant(), zone())
        when (delivery) {
            Delivery.Now -> poster.post(notice)
            is Delivery.At -> scheduler.at(ReminderScheduler.held(notice.id), HeldNoticeWorker::class.java, delivery.instant, notice.toData())
            Delivery.Dropped, Delivery.Off -> Unit
        }
        return delivery
    }

    // Booking the checks.

    /** Books every check again, as the settings and the programme now say. */
    suspend fun reschedule() {
        scheduleCheckIn()
        scheduleForgot()
        scheduleResults()
        scheduler.at(ReminderScheduler.WIDGET_DAY, WidgetRefreshWorker::class.java, nextDayStart(), newDayData)
    }

    private suspend fun scheduleCheckIn() {
        val settings = store.currentReminders()
        val at = if (isSetUp() && settings.isOn(Reminder.CHECK_IN)) {
            CheckIn.nextAt(settings.checkInTime, graph.clock.instant(), zone(), boundary(), store.currentMarks().checkInSentOn)
        } else {
            null
        }
        scheduler.at(ReminderScheduler.CHECK_IN, CheckInWorker::class.java, at)
    }

    private suspend fun scheduleForgot() {
        val at = if (isSetUp() && store.currentReminders().isOn(Reminder.FORGOT_TO_LOG)) {
            val now = graph.clock.instant()
            val boundary = boundary()
            val today = boundary.logicalDate(now, zone())
            // With too little logged for a usual time, it looks again in a day.
            ForgotToLog.nextCheck(now, recentEntries(today), boundary, zone(), store.currentMarks().forgotRemindedOn)
                ?: now.plus(Duration.ofDays(1))
        } else {
            null
        }
        scheduler.at(ReminderScheduler.FORGOT, ForgotToLogWorker::class.java, at)
    }

    /**
     * At the next result due, while a plan runs, or now when a result is in that hasn't been told
     * of yet. A result can come in before its booking runs, whenever the app looks or a dose is
     * logged, and the last week's has no next one to wait for. Results are noted even with the
     * reminder off.
     */
    private suspend fun scheduleResults() {
        val now = graph.clock.instant()
        val plan = graph.programmes.current()?.plan
        val untold = plan != null && isSetUp() && ResultNotices.since(plan, store.currentMarks().resultsNotifiedUpTo, now).isNotEmpty()
        val next = plan?.weeks?.firstOrNull { it.result == WeekResult.PENDING }
        val clock = graph.programmes.clock()
        val at = if (untold) now else next?.let { clock.resultDue(it) }
        scheduler.at(ReminderScheduler.RESULTS, ResultWorker::class.java, at)
    }

    /** The widget again, and at a new day the next day's booking. */
    suspend fun refreshWidget(newDay: Boolean) {
        LastHitWidget.update(context)
        if (newDay) scheduler.at(ReminderScheduler.WIDGET_DAY, WidgetRefreshWorker::class.java, nextDayStart(), newDayData)
    }

    /** A fallback for when the widget's "Logged" moment ends, in case the app is gone by then. */
    fun refreshWidgetAt(at: Instant) = scheduler.at(ReminderScheduler.WIDGET_MOMENT, WidgetRefreshWorker::class.java, at)

    private suspend fun nextDayStart(): Instant {
        val clock = graph.programmes.clock()
        return clock.endOf(clock.today)
    }

    private val newDayData = Data.Builder().putBoolean(WidgetRefreshWorker.KEY_NEW_DAY, true).build()

    private suspend fun recentEntries(today: LocalDate): List<Entry> =
        graph.entries.observeRange(today.minusDays(ForgotToLog.LOOK_BACK_DAYS.toLong()), today).first()

    private suspend fun isSetUp(): Boolean = graph.settings.current().onboardingComplete

    private suspend fun boundary(): DayBoundary = graph.settings.current().dayBoundary
}
