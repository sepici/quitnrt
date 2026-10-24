package app.thelasthit.nrt.data.db

import app.thelasthit.nrt.data.repo.ProgrammeRecord
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.game.BreathingSession
import app.thelasthit.nrt.domain.game.GameBaseline
import app.thelasthit.nrt.domain.game.GameProgramme
import app.thelasthit.nrt.domain.game.GameRecord
import app.thelasthit.nrt.domain.game.GameWeek
import app.thelasthit.nrt.domain.game.HitLedger
import app.thelasthit.nrt.domain.game.MixUnit
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.Mix
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The `createdAt` of an entry restored from a backup. A backup doesn't say when its entries were
 * written, so a restored one is never taken as backdated: it stays out of [GameRecord.loggedAt],
 * or a restore would unlock "Honest ledger" for every old dose.
 */
internal const val CREATED_AT_UNKNOWN = 0L

/**
 * Everything stored that the game's rules read, as a [GameRecord] on [today]: the live entries,
 * every programme oldest first with its weeks and its hit ledger, and the breathing sessions.
 *
 * Results and the times they came in are taken as they were recorded, never worked out again,
 * so a result stays fixed once it's in. [programmes] and [ledger] are every programme's, and
 * [boundary] and [zone] are the day start and the phone's zone, for the day a programme was
 * abandoned on.
 */
internal fun gameRecordOf(
    today: LocalDate,
    entries: List<EntryWithTags>,
    programmes: List<ProgrammeRecord>,
    ledger: List<HitLedgerEntity>,
    breathing: List<BreathingSessionEntity>,
    boundary: DayBoundary,
    zone: ZoneId,
): GameRecord {
    val ledgers = ledger.groupBy { it.programmeId }
    return GameRecord(
        today = today,
        entries = entries.map { it.toDomain() },
        loggedAt = entries.map { it.entry }
            .filter { it.createdAt != CREATED_AT_UNKNOWN }
            .associate { it.id to Instant.ofEpochMilli(it.createdAt) },
        programmes = programmes.sortedBy { it.createdAt }.mapNotNull { it.toGame(ledgers[it.id].orEmpty(), boundary, zone) },
        breathing = breathing.map { it.toGame() },
        dayStart = boundary.dayStart,
    )
}

/**
 * A stored programme as the game sees it, or null before its usual day is kept, as there's no
 * baseline to step down from yet.
 *
 * An abandoned programme keeps every week it was planned with, so its demon keeps its max HP,
 * and [GameProgramme.endedOn] is the logical day it was abandoned. A complete one has no end
 * day: its weeks all have results.
 *
 * [ledger] is its hit ledger, oldest first. If the rows don't fit the weeks, which only a
 * damaged database could do, the ledger is the hits the weeks have earned with none spent, so
 * the badges for the weeks still count.
 */
internal fun ProgrammeRecord.toGame(ledger: List<HitLedgerEntity>, boundary: DayBoundary, zone: ZoneId): GameProgramme? {
    val usual = usualDay?.mix?.toGame()?.takeIf { it.isNotEmpty() } ?: return null
    val endedOn = endedAt?.takeIf { status != ProgrammeStatus.COMPLETE }?.let { boundary.logicalDate(it, zone) }
    return runCatching {
        val baseline = GameBaseline(usual, measuredFrom = baselineDays?.start)
        val weeks = plan?.weeks.orEmpty().map { it.toGame() }
        val unchecked = GameProgramme(baseline, weeks, endedOn = endedOn)
        runCatching {
            val rows = ledger.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.toGame() }
            unchecked.copy(ledger = HitLedger(unchecked.maxHp, rows))
        }.getOrElse { unchecked.withEarnedHits() }
    }.getOrNull()
}

/** A programme week with its result and the time it came in, as recorded. */
internal fun ProgrammeWeek.toGame() = GameWeek(
    number = number,
    startDate = startDate,
    mix = target.mix.toGame(),
    kind = when (kind) {
        WeekKind.PLANNED -> GameWeek.Kind.PLANNED
        WeekKind.REPEAT -> GameWeek.Kind.REPEAT
        WeekKind.OVERTIME -> GameWeek.Kind.OVERTIME
    },
    result = when (result) {
        WeekResult.PENDING -> GameWeek.Result.PENDING
        WeekResult.MET -> GameWeek.Result.MET
        WeekResult.MISSED -> GameWeek.Result.MISSED
        WeekResult.CAUGHT_UP -> GameWeek.Result.CAUGHT_UP
    },
    resultAt = resultAt,
)

/** Units a day of each product, leaving out any with no nicotine, which the game can't count. */
private fun Mix.toGame(): List<MixUnit> = items
    .filter { it.product.perUnit > Dose.ZERO }
    .map { MixUnit(it.product.id, it.product.form, it.product.perUnit, it.units) }

private fun HitLedgerEntity.toGame() = HitLedger.Row(
    kind = when (kind) {
        LedgerKind.WEEK_MET -> HitLedger.Kind.WEEK_MET
        LedgerKind.CATCH_UP -> HitLedger.Kind.CATCH_UP
        LedgerKind.HIT -> HitLedger.Kind.HIT
        LedgerKind.COMBO -> HitLedger.Kind.COMBO
    },
    at = Instant.ofEpochMilli(createdAt),
    week = week?.takeIf { kind.hits > 0 },
)

private fun BreathingSessionEntity.toGame() = BreathingSession(
    type = when (type) {
        BreathingPattern.BOX -> BreathingSession.Type.BOX
        BreathingPattern.FOUR_SEVEN_EIGHT -> BreathingSession.Type.FOUR_SEVEN_EIGHT
    },
    startedAt = Instant.ofEpochMilli(startedAt),
    duration = Duration.ofSeconds(durationSeconds.coerceAtLeast(0)),
    rounds = roundsCompleted.coerceAtLeast(0),
    completed = ranToEnd,
)
