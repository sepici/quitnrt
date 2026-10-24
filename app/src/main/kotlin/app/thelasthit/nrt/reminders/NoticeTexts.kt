package app.thelasthit.nrt.reminders

import android.content.res.Resources
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.LabelWarning
import app.thelasthit.nrt.domain.reminders.AllowanceNudge
import app.thelasthit.nrt.domain.reminders.CheckIn
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ResultNotice
import app.thelasthit.nrt.ui.format.labelNoteText
import app.thelasthit.nrt.ui.format.mgText
import java.time.Duration

/** Each reminder in the app's words (docs/STYLE.md): plain, specific, and kind about slips. */
class NoticeTexts(private val resources: Resources) {

    fun nudge(nudge: AllowanceNudge): Notice = when (nudge) {
        is AllowanceNudge.WeekOver -> if (nudge.week.isNrtFree) {
            allowance(R.string.nudge_nrt_free_title, resources.getString(R.string.nudge_nrt_free_text))
        } else {
            allowance(
                R.string.nudge_week_over_title,
                resources.getString(R.string.nudge_week_over_text, nudge.total.mgText(), nudge.week.target.weeklyAllowance.mgText()),
            )
        }
        is AllowanceNudge.WeekNear -> Notice(
            reminder = Reminder.ALLOWANCE,
            channel = NoticeChannel.ALLOWANCE,
            id = Notice.ID_ALLOWANCE,
            title = resources.getString(R.string.nudge_week_near_title, AllowanceNudge.NEAR_PERCENT),
            text = resources.getString(R.string.nudge_week_near_text, nudge.total.mgText(), nudge.week.target.weeklyAllowance.mgText(), nudge.left.mgText()),
        )
        is AllowanceNudge.DayOver -> allowance(
            R.string.nudge_day_over_title,
            resources.getString(R.string.nudge_day_over_text, nudge.today.mgText(), nudge.guide.mgText(), nudge.weekLeft.mgText()),
        )
    }

    private fun allowance(title: Int, text: String) =
        Notice(Reminder.ALLOWANCE, NoticeChannel.ALLOWANCE, Notice.ID_ALLOWANCE, resources.getString(title), text)

    fun checkIn(checkIn: CheckIn): Notice = Notice(
        reminder = Reminder.CHECK_IN,
        channel = NoticeChannel.CHECK_IN,
        id = Notice.ID_CHECK_IN,
        title = resources.getString(R.string.check_in_title),
        text = checkIn.guide?.let { guide -> resources.getString(R.string.check_in_text_guide, checkIn.today.mgText(), guide.mgText()) }
            ?: if (checkIn.doses == 0) {
                resources.getString(R.string.check_in_text_none)
            } else {
                resources.getString(R.string.check_in_text, checkIn.today.mgText())
            },
        actions = listOf(NoticeAction.LOG, NoticeAction.ALL_GOOD),
    )

    fun forgotToLog(): Notice = Notice(
        reminder = Reminder.FORGOT_TO_LOG,
        channel = NoticeChannel.FORGOT,
        id = Notice.ID_FORGOT,
        title = resources.getString(R.string.forgot_title),
        text = resources.getString(R.string.forgot_text),
        actions = listOf(NoticeAction.LOG),
    )

    /** [demonName] is the demon's own name, or null for one that hasn't got one. */
    fun result(notice: ResultNotice, demonName: String?): Notice {
        val number = notice.week.number
        val (title, text) = if (notice.met) {
            val hits = if (demonName != null) {
                resources.getQuantityString(R.plurals.result_hits_named, notice.hits, notice.hits, demonName)
            } else {
                resources.getQuantityString(R.plurals.result_hits, notice.hits, notice.hits)
            }
            val paidBack = when (notice.paidBack.size) {
                0 -> null
                1 -> resources.getString(R.string.result_paid_back_one, notice.paidBack.single())
                else -> resources.getString(R.string.result_paid_back_many, listText(notice.paidBack.map { it.toString() }))
            }
            resources.getString(R.string.result_met_title, number) to listOfNotNull(paidBack, hits.takeIf { notice.hits > 0 }).joinToString(" ")
        } else {
            resources.getString(R.string.result_missed_title, number) to resources.getString(R.string.result_missed_text)
        }
        return Notice(Reminder.WEEKLY_RESULT, NoticeChannel.RESULTS, Notice.ID_RESULTS + number, title, text.ifBlank { null })
    }

    /**
     * Badges and gear that just unlocked. More than 3 badges at once read as one summary, as the
     * lair's card does (ROADMAP P4-2).
     */
    fun achievements(badges: List<String>, gear: List<String>, summary: Boolean): Notice? {
        val gearText = gear.takeIf { it.isNotEmpty() }?.let { listText(it) }
        val (title, text) = when {
            badges.isEmpty() && gearText == null -> return null
            badges.isEmpty() -> resources.getString(R.string.achievement_gear_title, gearText) to resources.getString(R.string.achievement_gear_text)
            badges.size == 1 -> badges.single() to listOfNotNull(
                resources.getString(R.string.achievement_badge_text),
                gearText?.let { resources.getString(R.string.achievement_gear_too, it) },
            ).joinToString(" ")
            else -> resources.getQuantityString(R.plurals.achievement_badges_title, badges.size, badges.size) to listOfNotNull(
                if (summary) resources.getString(R.string.achievement_badges_summary) else resources.getString(R.string.achievement_badges_text, listText(badges)),
                gearText?.let { resources.getString(R.string.achievement_gear_too, it) },
            ).joinToString(" ")
        }
        return Notice(Reminder.ACHIEVEMENTS, NoticeChannel.ACHIEVEMENTS, Notice.achievementsId(badges, gear), title, text)
    }

    /** The tile's note: what it logged, with Undo, gone after [timeout]. */
    fun loggedFromTile(entryId: String, total: Dose, name: String, warning: LabelWarning?, timeout: Duration): Notice = Notice(
        reminder = null,
        channel = NoticeChannel.LOGGED,
        id = Notice.ID_LOGGED,
        title = resources.getString(R.string.today_logged, total.mgText(), name),
        text = warning?.let { resources.labelNoteText(it) },
        actions = listOf(NoticeAction.UNDO),
        entryId = entryId,
        timeout = timeout,
    )

    /** "3", "3 and 4", "3, 4 and 5". */
    private fun listText(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> resources.getString(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())
    }
}
