package app.thelasthit.nrt.agents

import androidx.appfunctions.AppFunctionSerializable
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.domain.agent.AgentAdviceAnswer
import app.thelasthit.nrt.domain.agent.AgentLogRequest
import app.thelasthit.nrt.domain.agent.AgentSummary
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

// What the five functions return, in the shapes the AppFunctions library can send. Each class
// and property is described by its KDoc, which the agent reads, so the KDoc is written for it.

/** One of the user's nicotine replacement therapy (NRT) products. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtProduct(
    /** The product's ID. Pass it to logNrtUsage as productDescription to log exactly this product. */
    val id: String,
    /** How the app names it, with the strength on the pack, such as "Nicorette gum 2 mg". */
    val label: String,
    /** The product's full name, such as "Nicorette Icy White gum 2 mg". */
    val name: String,
    /**
     * The kind of product: gum, lozenge, mini_lozenge, patch_16h, patch_24h, mouth_spray,
     * nasal_spray, inhalator, microtab, oral_film, pouch or other.
     */
    val form: String,
    /**
     * What it's logged in, which quantities count: piece, lozenge, patch, spray, session,
     * cartridge, puff, tablet, strip, pouch or custom.
     */
    val unit: String,
    /** Nicotine in one unit, in micrograms: 2000 is 2 mg. */
    val microgramsPerUnit: Long,
    /** Whether it's on the app's quick log, the products the user logs most. */
    val onQuickLog: Boolean,
    /** The most units a day the pack's label allows, or null when it doesn't say. */
    val labelMaxPerDay: Int?,
)

/** A dose in the app's log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtEntry(
    /** The entry's ID. */
    val id: String,
    /** The ID of the product taken. */
    val productId: String,
    /** The product, as the app names it, such as "Nicorette gum". */
    val productName: String,
    /** How many, in the product's unit. */
    val quantity: Int,
    /** The product's unit, such as piece or spray. */
    val unit: String,
    /** Nicotine in the dose, in micrograms: 4000 is 4 mg. */
    val totalMicrograms: Long,
    /** When it was taken, on the clock where it was logged. */
    val takenAt: LocalDateTime,
    /** Its tags, by key, such as stressed or after_meal. */
    val tags: List<String>,
    /** How strong the craving was, 1 to 5, or null when not given. */
    val cravingStrength: Int?,
)

/** The user's day and week of NRT. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtSummary(
    /** The summary in a few sentences, to read to the user. */
    val message: String,
    /**
     * Today's date in the app. A day runs from the user's day start, 04:00 unless they changed it,
     * so a dose at 01:00 counts towards the day before.
     */
    val date: LocalDate,
    /** Nicotine logged today, in micrograms: 8000 is 8 mg. Patches count too. */
    val todayMicrograms: Long,
    /** How many doses were logged today. */
    val todayDoses: Int,
    /**
     * Today's daily guide in micrograms while a plan week runs, or null. It's a ceiling to stay at
     * or under, not an amount to reach. Zero in an NRT-free week.
     */
    val dailyGuideMicrograms: Long?,
    /** Whether today is at or under the daily guide so far, or null with no guide. */
    val withinDailyGuide: Boolean?,
    /** The programme week's number while a plan runs, or null when the week is Monday to Sunday. */
    val weekNumber: Int?,
    /** The week's first day. */
    val weekStart: LocalDate,
    /** The week's last day. */
    val weekEnd: LocalDate,
    /** Nicotine logged in the week so far, in micrograms. */
    val weekMicrograms: Long,
    /**
     * The week's allowance in micrograms while a plan runs, or null. The week is met when its
     * total stays at or under it. Zero in an NRT-free week.
     */
    val weeklyAllowanceMicrograms: Long?,
    /** Whether the week is at or under its allowance so far, or null with no allowance. */
    val withinWeeklyAllowance: Boolean?,
    /** When the last dose that wasn't a patch was taken, within the last 30 days, or null. */
    val lastDoseAt: LocalDateTime?,
    /** Where the user's programme is: none, baseline, active, overtime or complete. */
    val plan: String,
    /** The name the user gave their demon, once a plan has started. */
    val demonName: String?,
    /** The demon's HP, once a plan has started. Each week met takes 10 off it. */
    val demonHp: Int?,
    /** The demon's HP when the plan started, plus 10 for each repeated week. */
    val demonMaxHp: Int?,
    /** Hits earned by meeting weeks and not spent on the demon yet. */
    val bankedHits: Int?,
)

/** A product a description could mean, and what logging it would log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtCandidate(
    /** The product. Its id goes to logNrtUsage as productDescription. */
    val product: NrtProduct,
    /**
     * How many to log if it's this product, in the product's own unit: "two gums" is 2, and an
     * inhalator cartridge is 8 on an inhalator logged by session. Pass it to logNrtUsage as
     * quantity, since the product's id on its own logs one.
     */
    val quantity: Int,
)

/** What came of logNrtUsage. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtLogResult(
    /** What to tell the user: what was logged, or the question to ask them. */
    val message: String,
    /** Whether the dose was saved. */
    val saved: Boolean,
    /** The dose saved, when saved is true. */
    val entry: NrtEntry?,
    /**
     * True when nothing was saved because the same product was logged less than a minute before
     * or after from somewhere else in the app, such as the widget or the log sheet. Ask the user
     * whether it's a second dose, and if it is, call logNrtUsage again with keepDuplicate set to
     * true. Doses logNrtUsage logged itself aren't checked, so calling it twice logs two doses.
     */
    val possibleDuplicate: Boolean,
    /** The dose already logged, when possibleDuplicate is true. */
    val duplicateOf: NrtEntry?,
    /**
     * When the description fits more than one product, nothing was saved and these are the
     * products it could mean, most likely first, each with the quantity it would log. Ask the
     * user which, then call logNrtUsage again with that candidate's product id as
     * productDescription and its quantity as quantity, and the same time, tags and craving
     * strength.
     */
    val candidates: List<NrtCandidate>,
    /** The user's day and week after this dose, when saved is true. */
    val summary: NrtSummary?,
)

/** What undoLastNrtEntry deleted. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtUndoResult(
    /** What to tell the user. */
    val message: String,
    /** The entry deleted. */
    val deleted: NrtEntry,
    /** The user's day and week now that it's gone. */
    val summary: NrtSummary,
)

/** A way through a craving, from public health guidance. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtAdvice(
    /** The strategy's key, such as breathing_timer. */
    val key: String,
    /** Its title, such as "Slow your breathing". */
    val title: String,
    /** Why it helps, in one to three sentences. */
    val body: String,
    /** What to do, in order. */
    val steps: List<String>,
    /** Why this strategy: the tag asked about, the user's top trigger, or advice for any time. */
    val reason: String,
    /** The tag it's for, by key, or null when it's for any time. */
    val tag: String?,
    /** Whether the app has a timer for it: breathing, or a delay of 5 to 10 minutes. */
    val hasTimer: Boolean,
    /** Where the advice comes from, such as "NHS: Quit smoking". */
    val sources: List<String>,
    /**
     * To pass on with it: it's general information, not medical advice, and where there are
     * quitlines, that a free one can help.
     */
    val note: String,
    /**
     * The free quitlines for the user's country, for help beyond this advice. Pass on the one for
     * where the user lives, by name and number or site, when the craving is strong or keeps up,
     * withdrawal is hard, or the user asks for more help. Empty where the app has none.
     */
    val quitlines: List<NrtQuitline>,
)

/** A free line for help with stopping, from public health guidance. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NrtQuitline(
    /** Its name, such as "Smokefree National Helpline". */
    val name: String,
    /** The part of the country it's for, such as "Scotland", or null for the whole country. */
    val region: String?,
    /** Its phone number, as the service writes it, or null when it only has a site. */
    val phone: String?,
    /** Its site, a full https address, or null when it only has a number. */
    val web: String?,
)

/** A form or unit as the functions name it: lower case, as in the KDoc lists. */
private fun Enum<*>.key(): String = name.lowercase(Locale.ROOT)

internal fun UserProduct.toResponse(text: AgentText) = NrtProduct(
    id = id,
    label = text.productLabel(this),
    name = name,
    form = form.key(),
    unit = unit.key(),
    microgramsPerUnit = perUnit.micrograms,
    onQuickLog = quickLogPosition != null,
    labelMaxPerDay = label.maxUnitsPerDay,
)

internal fun Entry.toResponse(name: String) = NrtEntry(
    id = id,
    productId = productId,
    productName = name,
    quantity = quantity,
    unit = unit.key(),
    totalMicrograms = total.micrograms,
    takenAt = LocalDateTime.ofInstant(occurredAt, zone),
    tags = tagKeys.sorted(),
    cravingStrength = cravingStrength,
)

internal fun AgentSummary.toResponse(text: AgentText) = NrtSummary(
    message = text.summary(this),
    date = today,
    todayMicrograms = todayTotal.micrograms,
    todayDoses = todayDoses,
    dailyGuideMicrograms = dailyGuide?.micrograms,
    withinDailyGuide = withinGuide,
    weekNumber = week.number,
    weekStart = week.start,
    weekEnd = week.end,
    weekMicrograms = week.total.micrograms,
    weeklyAllowanceMicrograms = week.allowance?.micrograms,
    withinWeeklyAllowance = week.withinAllowance,
    lastDoseAt = lastDose?.let { LocalDateTime.ofInstant(it.occurredAt, it.zone) },
    plan = stage.key(),
    demonName = demon?.let { it.name ?: text.defaultDemonName() },
    demonHp = demon?.hp,
    demonMaxHp = demon?.maxHp,
    bankedHits = demon?.bankedHits,
)

internal fun AgentAdviceAnswer.toResponse(text: AgentText): NrtAdvice {
    val tag = when (val why = reason) {
        is AgentAdviceAnswer.Reason.AskedTag -> why.key
        is AgentAdviceAnswer.Reason.ForTopTrigger -> why.trigger.key
        AgentAdviceAnswer.Reason.AnyTime -> null
    }
    val timer = strategy.interactive
    return NrtAdvice(
        key = strategy.key,
        title = strategy.title,
        body = strategy.body,
        steps = strategy.steps,
        reason = listOfNotNull(text.adviceReason(this), timer?.let { text.adviceTimer() }).joinToString(" "),
        tag = tag,
        hasTimer = timer == Interactive.BREATHING_TIMER || timer == Interactive.DELAY_TIMER,
        sources = strategy.sources.map { "${it.publisher}: ${it.title}" },
        note = text.adviceNote(withQuitlines = quitlines.isNotEmpty()),
        quitlines = quitlines.map { NrtQuitline(it.name, it.region, it.phone, it.web) },
    )
}

/** A candidate for an ambiguous product, with the quantity it would log in its own unit. */
internal fun AgentLogRequest.toCandidate(text: AgentText) = NrtCandidate(product.toResponse(text), quantity)
