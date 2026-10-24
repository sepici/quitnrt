package app.thelasthit.nrt.agents

import android.content.res.Resources
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.agent.AgentAdviceAnswer
import app.thelasthit.nrt.domain.agent.AgentLogReader
import app.thelasthit.nrt.domain.agent.AgentRefusal
import app.thelasthit.nrt.domain.agent.AgentSummary
import app.thelasthit.nrt.domain.agent.PlanStage
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.format.ShortDateFormatter
import app.thelasthit.nrt.ui.format.labelNoteText
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The words an agent gets back from the five functions, from `strings_agents.xml`. An agent may
 * read them out, so they're app text like any screen's. Times follow the phone's 12 or 24 hour
 * setting, as the screens do.
 */
class AgentText(
    private val resources: Resources,
    is24Hour: Boolean,
    /** A tag's name as the app shows it, by key. */
    private val tagName: (String) -> String,
    /** The short name ("Nicorette gum") of an entry's product, which may be hidden now. */
    private val shortName: (Entry) -> String = { it.productName },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** The time now, so a dose from another day says which day. */
    private val now: () -> Instant = Instant::now,
) {
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.UK)

    /** Why a call was refused. A refused log says nothing was logged, so no one wonders. */
    fun refusal(reason: AgentRefusal, logging: Boolean = false): String {
        val why = when (reason) {
            AgentRefusal.NotSetUp -> return resources.getString(R.string.agents_not_set_up)
            AgentRefusal.MissingProduct -> resources.getString(R.string.agents_missing_product)
            is AgentRefusal.UnknownProduct -> resources.getString(R.string.agents_unknown_product, reason.text)
            is AgentRefusal.HiddenProduct -> resources.getString(R.string.agents_hidden_product, productLabel(reason.product))
            is AgentRefusal.BadQuantity -> resources.getString(R.string.agents_bad_quantity, AgentLogReader.MAX_QUANTITY)
            is AgentRefusal.ConflictingQuantity ->
                resources.getString(R.string.agents_conflicting_quantity, reason.inProduct, reason.quantity)
            is AgentRefusal.TooMany -> resources.getString(
                R.string.agents_too_many,
                resources.getQuantityString(reason.unit.plural, reason.quantity, reason.quantity),
                AgentLogReader.MAX_QUANTITY,
            )
            is AgentRefusal.TimeInFuture ->
                resources.getString(R.string.agents_time_in_future, ShortDateFormatter.format(reason.at), timeFormat.format(reason.at))
            is AgentRefusal.UnknownTags ->
                resources.getQuantityString(R.plurals.agents_unknown_tags, reason.tags.size, reason.tags.joinToString(", "))
            is AgentRefusal.AmbiguousTags -> resources.getString(R.string.agents_ambiguous_tag, reason.tags.keys.first())
            is AgentRefusal.BadCravingStrength -> resources.getString(R.string.agents_bad_craving_strength)
            AgentRefusal.NothingToUndo -> resources.getString(R.string.agents_nothing_to_undo)
        }
        return if (logging) "$why ${resources.getString(R.string.agents_nothing_logged)}" else why
    }

    /** "Logged 2 mg of Nicorette gum at 15:05.", the pack-limit note if there is one, and the summary. */
    fun logged(outcome: AgentLogOutcome.Logged): String = sentences(
        at(outcome.entry.occurredAt, R.string.agents_logged, R.string.agents_logged_on, outcome.entry.total.mgText(), shortName(outcome.entry)),
        outcome.warning?.let(resources::labelNoteText),
        summary(outcome.summary),
    )

    fun possibleDuplicate(outcome: AgentLogOutcome.PossibleDuplicate): String =
        // The duplicate check only matches the same product, so the request's product is the one.
        resources.getString(R.string.agents_possible_duplicate, productLabel(outcome.request.product), time(outcome.existing.occurredAt))

    fun ambiguous(outcome: AgentLogOutcome.Ambiguous): String =
        resources.getString(R.string.agents_ambiguous_product, outcome.text, orList(outcome.choices.map { productLabel(it.product) }))

    fun undone(outcome: AgentUndoOutcome.Undone): String = sentences(
        at(outcome.entry.occurredAt, R.string.agents_undone, R.string.agents_undone_on, outcome.entry.total.mgText(), shortName(outcome.entry)),
        summary(outcome.summary),
    )

    /** Today, the week, the last dose, where the plan is and the demon, a sentence each. */
    fun summary(summary: AgentSummary): String = sentences(
        today(summary),
        week(summary),
        summary.lastDose?.let { at(it.occurredAt, R.string.agents_last_dose, R.string.agents_last_dose_on) },
        when (summary.stage) {
            PlanStage.BASELINE -> resources.getString(R.string.agents_stage_baseline)
            PlanStage.COMPLETE -> resources.getString(R.string.agents_stage_complete)
            PlanStage.NONE, PlanStage.ACTIVE, PlanStage.OVERTIME -> null
        },
        summary.demon?.let { demon ->
            val name = demon.name ?: defaultDemonName()
            if (demon.bankedHits == 0) {
                resources.getString(R.string.agents_demon, name, demon.hp, demon.maxHp)
            } else {
                resources.getQuantityString(R.plurals.agents_demon_hits, demon.bankedHits, name, demon.hp, demon.maxHp, demon.bankedHits)
            }
        },
    )

    /** Why this advice: the tag asked about, the top trigger, or too little logged to know. */
    fun adviceReason(answer: AgentAdviceAnswer): String = when (val reason = answer.reason) {
        is AgentAdviceAnswer.Reason.AskedTag -> resources.getString(R.string.agents_advice_asked, tagName(reason.key))
        is AgentAdviceAnswer.Reason.ForTopTrigger ->
            resources.getString(R.string.agents_advice_top_trigger, tagName(reason.trigger.key), (reason.trigger.share * 100).roundToInt())
        AgentAdviceAnswer.Reason.AnyTime -> resources.getString(R.string.agents_advice_any_time)
    }

    /** What the app calls a demon the user didn't name. */
    fun defaultDemonName(): String = resources.getString(R.string.demon_default_name)

    fun adviceTimer(): String = resources.getString(R.string.agents_advice_timer)

    fun adviceNote(): String = resources.getString(R.string.agents_advice_note)

    /** "Nicorette gum 2 mg": the short name and the strength on the pack (a cartridge, for the inhalator). */
    fun productLabel(product: UserProduct): String =
        resources.getString(R.string.dose_named, product.shortName, (product.cartridge ?: product.perUnit).mgText())

    /** The name an entry's product goes by now ("Nicorette gum"), or the name it was logged with. */
    fun productName(entry: Entry): String = shortName(entry)

    /** A time on the phone's clock, as the screens show it. */
    fun time(instant: Instant): String = timeFormat.format(instant.atZone(zone()))

    private fun today(summary: AgentSummary): String {
        val total = summary.todayTotal
        val guide = summary.dailyGuide
        return when {
            guide == Dose.ZERO && total == Dose.ZERO -> resources.getString(R.string.agents_today_nrt_free_none)
            guide == Dose.ZERO -> resources.getString(R.string.agents_today_nrt_free, total.mgText())
            guide != null && total <= guide -> resources.getString(R.string.agents_today_within_guide, total.mgText(), guide.mgText())
            guide != null -> resources.getString(R.string.agents_today_over_guide, total.mgText(), guide.mgText())
            summary.todayDoses == 0 -> resources.getString(R.string.agents_today_none)
            else -> resources.getQuantityString(R.plurals.agents_today, summary.todayDoses, total.mgText(), summary.todayDoses)
        }
    }

    private fun week(summary: AgentSummary): String {
        val week = summary.week
        val number = week.number
        val allowance = week.allowance
        return when {
            number == null || allowance == null -> resources.getString(R.string.agents_week_calendar, week.total.mgText())
            week.isNrtFree && week.total == Dose.ZERO -> resources.getString(R.string.agents_week_nrt_free_none, number)
            week.isNrtFree -> resources.getString(R.string.agents_week_nrt_free, number, week.total.mgText())
            week.total <= allowance -> resources.getString(R.string.agents_week_within, number, week.total.mgText(), allowance.mgText())
            else -> resources.getString(R.string.agents_week_over, number, week.total.mgText(), allowance.mgText())
        }
    }

    /** [today] with the time, or [otherDay] with the date and time when it's another day. */
    private fun at(instant: Instant, today: Int, otherDay: Int, vararg before: Any): String {
        val local = LocalDateTime.ofInstant(instant, zone())
        return if (local.toLocalDate() == LocalDateTime.ofInstant(now(), zone()).toLocalDate()) {
            resources.getString(today, *before, timeFormat.format(local))
        } else {
            resources.getString(otherDay, *before, ShortDateFormatter.format(local), timeFormat.format(local))
        }
    }

    /** "a, b or c". */
    private fun orList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> resources.getString(R.string.agents_list_or, items.dropLast(1).joinToString(", "), items.last())
    }

    private fun sentences(vararg parts: String?): String = parts.filterNotNull().joinToString(" ")
}
