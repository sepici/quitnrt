package app.thelasthit.nrt.data.db

import app.thelasthit.nrt.data.repo.Demon
import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.Ladder
import app.thelasthit.nrt.domain.programme.Mix
import app.thelasthit.nrt.domain.programme.MixItem
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekChoice
import app.thelasthit.nrt.domain.programme.WeekTarget
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.Instant
import java.time.LocalDate

/**
 * The product a mix's row stands for: the user's product as it is now, with the strength the mix
 * was made with, or a hidden stand-in from the row when the product is gone.
 */
internal fun UnitsColumns.product(products: Map<String, UserProduct>): UserProduct =
    products[productId]?.copy(perUnit = Dose(perUnitMicrograms)) ?: UserProduct(
        id = productId,
        catalogueId = null,
        name = productName,
        shortName = productName,
        form = form,
        unit = unit,
        perUnit = Dose(perUnitMicrograms),
        isHidden = true,
    )

internal fun List<UnitsColumns>.toMix(products: Map<String, UserProduct>): Mix =
    Mix.of(map { MixItem(it.product(products), it.unitsPerDay) })

internal fun MixItem.toColumns() = UnitsColumns(
    productId = product.id,
    productName = product.name,
    form = product.form,
    unit = product.unit,
    perUnitMicrograms = product.perUnit.micrograms,
    unitsPerDay = units,
)

internal fun UsualDay.toEntities(programmeId: String) = mix.items.map { BaselineUnitEntity(programmeId, it.toColumns()) }

internal fun Ladder.toEntities(programmeId: String) = steps.flatMapIndexed { index, step ->
    step.items.map { LadderUnitEntity(programmeId, index + 1, it.toColumns()) }
}

/** A programme's weeks as rows, each with its mix and any choice made after it was missed. */
internal fun Programme.toWeekEntities(
    programmeId: String,
    choices: Map<Int, WeekChoice>,
): Pair<List<ProgrammeWeekEntity>, List<ProgrammeWeekUnitEntity>> {
    val weekRows = weeks.map { week ->
        val choice = choices[week.number]
        ProgrammeWeekEntity(
            programmeId = programmeId,
            number = week.number,
            startDate = week.startDate.toEpochDay(),
            endDate = week.endDate.toEpochDay(),
            kind = week.kind,
            ladderStep = week.target.step,
            dailyGuideMicrograms = week.target.dailyGuide.micrograms,
            weeklyAllowanceMicrograms = week.target.weeklyAllowance.micrograms,
            isNrtFree = week.isNrtFree,
            result = week.result,
            resultAt = week.resultAt?.toEpochMilli(),
            choice = choice?.choice,
            choiceAt = choice?.at?.toEpochMilli(),
        )
    }
    val unitRows = weeks.flatMap { week ->
        week.target.mix.items.map { ProgrammeWeekUnitEntity(programmeId, week.number, it.toColumns()) }
    }
    return weekRows to unitRows
}

internal fun DemonEntity.toDomain() = Demon(
    id = id,
    programmeId = programmeId,
    name = name,
    creature = creature,
    variant = variant,
    maxHp = maxHp,
    createdAt = Instant.ofEpochMilli(createdAt),
    defeatedAt = defeatedAt?.let(Instant::ofEpochMilli),
)

/**
 * The stored programme read back. [products] are the user's, hidden ones too, so the mixes show
 * the products as they are now. Throws if the rows don't make a valid programme.
 */
internal fun ProgrammeWithParts.toRecord(products: Map<String, UserProduct>): ProgrammeRecord {
    val row = programme
    val usual = usualDay.takeIf { it.isNotEmpty() }?.let { units ->
        UsualDay(
            mix = units.map { it.units }.toMix(products),
            source = row.baselineSource ?: BaselineSource.MANUAL,
            measured = row.baselineMeasuredMicrograms?.let(::Dose),
        )
    }
    val plan = if (weeks.isEmpty()) {
        null
    } else {
        val length = ProgrammeLength.entries.firstOrNull { it.weeks == row.lengthWeeks }
        requireNotNull(usual) { "Programme ${row.id} has weeks but no usual day" }
        requireNotNull(length) { "Programme ${row.id} has no length: ${row.lengthWeeks}" }
        val byStep = ladder.groupBy { it.step }
        require(byStep.keys == (1..byStep.size).toSet()) { "Programme ${row.id}'s ladder skips a step: ${byStep.keys}" }
        val steps = (1..byStep.size).map { step -> byStep.getValue(step).map { it.units }.toMix(products) }
        val mixes = weekUnits.groupBy { it.weekNumber }
        val programmeWeeks = weeks.sortedBy { it.number }.map { week ->
            ProgrammeWeek(
                number = week.number,
                startDate = LocalDate.ofEpochDay(week.startDate),
                target = WeekTarget(week.ladderStep, mixes[week.number].orEmpty().map { it.units }.toMix(products)),
                kind = week.kind,
                result = week.result,
                resultAt = week.resultAt?.let(Instant::ofEpochMilli),
            )
        }
        Programme(Ladder(usual, steps), length, programmeWeeks)
    }
    val choices = weeks.mapNotNull { week ->
        val choice = week.choice ?: return@mapNotNull null
        val at = week.choiceAt ?: return@mapNotNull null
        week.number to WeekChoice(week.number, choice, Instant.ofEpochMilli(at))
    }.toMap()
    return ProgrammeRecord(
        id = row.id,
        status = row.status,
        createdAt = Instant.ofEpochMilli(row.createdAt),
        endedAt = row.endedAt?.let(Instant::ofEpochMilli),
        baselineStartedOn = row.baselineStartedOn?.let(LocalDate::ofEpochDay),
        baselineDays = if (row.baselineStart != null && row.baselineEnd != null) {
            LocalDate.ofEpochDay(row.baselineStart)..LocalDate.ofEpochDay(row.baselineEnd)
        } else {
            null
        },
        usualDay = usual,
        plan = plan,
        choices = choices,
        demon = demons.firstOrNull()?.toDomain(),
    )
}
