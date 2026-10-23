package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal val ZONE: ZoneId = ZoneId.of("Europe/London")

internal const val GUM = "gum-2mg"
internal const val PATCH = "patch-21mg"

internal fun date(text: String): LocalDate = LocalDate.parse(text)

/** The clock at [at] on London's wall clock, with a 04:00 day start. */
internal fun clockAt(at: String): ProgrammeClock = ProgrammeClock(LocalDateTime.parse(at).atZone(ZONE).toInstant(), ZONE)

internal fun clockAt(instant: Instant): ProgrammeClock = ProgrammeClock(instant, ZONE)

/** One entry of [total] at [at] on London's wall clock, counted toward its day with a 04:00 day start. */
internal fun dose(at: String, total: Dose, productId: String = GUM): Entry = dose(LocalDateTime.parse(at), total, productId)

internal fun dose(at: LocalDateTime, total: Dose, productId: String = GUM): Entry {
    val instant = at.atZone(ZONE).toInstant()
    val form = if (productId == PATCH) ProductForm.PATCH_24H else ProductForm.GUM
    return Entry(
        id = UUID.randomUUID().toString(),
        productId = productId,
        productName = productId,
        form = form,
        unit = form.defaultUnit,
        perUnit = total,
        quantity = 1,
        total = total,
        occurredAt = instant,
        zone = ZONE,
        logicalDate = DayBoundary().logicalDate(instant, ZONE),
    )
}

/** Saturday 24 October 2026, the day after the app tests' Friday: the default start. */
internal val START: LocalDate = date("2026-10-24")

/** PLAN's worked example: 24 mg a day of 2 mg gum, over 12 weeks from [START]. */
internal fun planExample(): Programme =
    Programme.create(Baseline.typed(Dose.mg(24)), ProgrammeLength.WEEKS_12, Dose.mg(2), START, mainProductId = GUM)

internal val Programme.allowances: List<Dose> get() = weeks.map { it.target.weeklyAllowance }

/** [total] logged across [week], as one dose at noon on its first day. Nothing for zero. */
internal fun dosesIn(week: ProgrammeWeek, total: Dose): List<Entry> =
    if (total == Dose.ZERO) emptyList() else listOf(dose(week.startDate.atTime(12, 0), total))

/** Exactly [week]'s allowance, which meets it. */
internal fun meeting(week: ProgrammeWeek): List<Entry> = dosesIn(week, week.target.weeklyAllowance)

/** 1 mg over [week]'s allowance, which misses it. */
internal fun missing(week: ProgrammeWeek): List<Entry> = dosesIn(week, week.target.weeklyAllowance + Dose.mg(1))

/** The clock at the moment [week]'s result comes in. */
internal fun dueFor(week: ProgrammeWeek): ProgrammeClock = clockAt(ProgrammeClock(Instant.EPOCH, ZONE).resultDue(week))

/** A programme played through, with the doses logged and every hit its settlements brought. */
internal data class Played(val programme: Programme, val entries: List<Entry>, val hits: List<EarnedHit>)

/**
 * Plays [programme] a week at a time, with one letter of [outcomes] for each week in the order
 * they come: `m` meets it, `x` misses it and carries on, and `r` misses it and repeats it. Each
 * result is brought in when it's due, and [check] sees the programme after every one.
 */
internal fun play(programme: Programme, outcomes: String, check: (Programme) -> Unit = {}): Played {
    var current = programme
    val entries = mutableListOf<Entry>()
    val hits = mutableListOf<EarnedHit>()
    for (outcome in outcomes) {
        val week = current.weeks.first { it.result == WeekResult.PENDING }
        entries += when (outcome) {
            'm' -> meeting(week)
            'x', 'r' -> missing(week)
            else -> error("No outcome '$outcome'")
        }
        val clock = dueFor(week)
        val settlement = current.settle(entries, clock)
        current = settlement.programme
        hits += settlement.newHits
        if (outcome == 'r') current = current.repeat(week.number, clock)
        check(current)
    }
    return Played(current, entries, hits)
}
