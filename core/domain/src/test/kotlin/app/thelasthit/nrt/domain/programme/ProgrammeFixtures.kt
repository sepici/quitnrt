package app.thelasthit.nrt.domain.programme

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal val ZONE: ZoneId = ZoneId.of("Europe/London")

internal val CATALOGUE: Catalogue = Catalogue.bundled()

/** The fixtures' market: the UK. */
internal const val MARKET = "GB"

/** A catalogue product as one of the user's own. */
internal fun product(catalogueId: String): UserProduct =
    requireNotNull(CATALOGUE.find(catalogueId)) { "No catalogue product $catalogueId" }.toUserProduct()

internal val GUM_2 = product("nicorette-gum-uk-2mg")
internal val GUM_4 = product("nicorette-gum-uk-4mg")
internal val SPRAY = product("nicorette-quickmist-uk-1mg")
internal val PATCH_21 = product("niquitin-clear-patch-uk-21mg")
internal val PATCH_14 = product("niquitin-clear-patch-uk-14mg")
internal val PATCH_7 = product("niquitin-clear-patch-uk-7mg")
internal val LOZENGE_4 = product("niquitin-lozenges-uk-4mg")
internal val LOZENGE_2 = product("niquitin-lozenges-uk-2mg")

internal val GUM: String = GUM_2.id
internal val PATCH: String = PATCH_21.id

internal fun date(text: String): LocalDate = LocalDate.parse(text)

/** The clock at [at] on London's wall clock, with a 04:00 day start. */
internal fun clockAt(at: String): ProgrammeClock = ProgrammeClock(LocalDateTime.parse(at).atZone(ZONE).toInstant(), ZONE)

internal fun clockAt(instant: Instant): ProgrammeClock = ProgrammeClock(instant, ZONE)

/** One entry of [total] at [at] on London's wall clock, counted toward its day with a 04:00 day start. */
internal fun dose(at: String, total: Dose, productId: String = GUM): Entry = dose(LocalDateTime.parse(at), total, productId)

internal fun dose(at: LocalDateTime, total: Dose, productId: String = GUM): Entry {
    val product = if (productId == PATCH) PATCH_21 else GUM_2
    return entry(at, product.copy(id = productId, perUnit = total), quantity = 1)
}

/** [quantity] units of [product] at [at] on London's wall clock. */
internal fun use(at: String, product: UserProduct, quantity: Int = 1): Entry = entry(LocalDateTime.parse(at), product, quantity)

private fun entry(at: LocalDateTime, product: UserProduct, quantity: Int): Entry {
    val instant = at.atZone(ZONE).toInstant()
    return Entry(
        id = UUID.randomUUID().toString(),
        productId = product.id,
        productName = product.name,
        form = product.form,
        unit = product.unit,
        perUnit = product.perUnit,
        quantity = quantity,
        total = product.perUnit * quantity,
        occurredAt = instant,
        zone = ZONE,
        logicalDate = DayBoundary().logicalDate(instant, ZONE),
    )
}

/** A typed usual day of whole units of each product. */
internal fun usual(vararg units: Pair<UserProduct, Int>): UsualDay = UsualDay.typed(*units)

/** The ladder down from [usual] for a user with [own] products, in the UK unless said otherwise. */
internal fun ladder(usual: UsualDay, own: List<UserProduct> = usual.mix.products, market: String = MARKET): Ladder =
    Ladder.build(usual, own, CATALOGUE, market)

/** A mix of whole units, for comparing with a week's. */
internal fun mix(vararg units: Pair<UserProduct, Int>): Mix = Mix.of(*units)

/** Saturday 24 October 2026, the day after the app tests' Friday: the default start. */
internal val START: LocalDate = date("2026-10-24")

/** PLAN's first example: 12 pieces of 2 mg gum a day (24 mg), over 12 weeks from [start]. */
internal fun planExample(start: LocalDate = START): Programme =
    Programme.create(ladder(usual(GUM_2 to 12)), ProgrammeLength.WEEKS_12, start)

internal val Programme.allowances: List<Dose> get() = weeks.map { it.target.weeklyAllowance }

internal val Programme.guides: List<Dose> get() = weeks.map { it.target.dailyGuide }

internal val Programme.mixes: List<Mix> get() = weeks.map { it.target.mix }

internal val Programme.steps: List<Int?> get() = weeks.map { it.target.step }

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
