package app.thelasthit.nrt.data.repo

import androidx.room.withTransaction
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.data.db.DemonEntity
import app.thelasthit.nrt.data.db.EntryEntity
import app.thelasthit.nrt.data.db.HitLedgerEntity
import app.thelasthit.nrt.data.db.ProgrammeEntity
import app.thelasthit.nrt.data.db.balance
import app.thelasthit.nrt.data.db.toDomain
import app.thelasthit.nrt.data.db.toEntities
import app.thelasthit.nrt.data.db.toEntity
import app.thelasthit.nrt.data.db.toRecord
import app.thelasthit.nrt.data.db.toWeekEntities
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.BaselineDays
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.EarnedHit
import app.thelasthit.nrt.domain.programme.ExtraGoals
import app.thelasthit.nrt.domain.programme.Ladder
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.MissedWeekChoices
import app.thelasthit.nrt.domain.programme.Mix
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeClock
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.Settlement
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.domain.programme.WeekChoice
import app.thelasthit.nrt.domain.programme.WeekGoals
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Brings in the programme results that are due. Every write to the entries calls it first, so
 * nothing written after a result is due can reach it (see [Programme.settle]).
 */
fun interface ResultSettler {
    suspend fun settleDue()
}

/**
 * A programme as stored: its baseline, the usual day once it's kept, the plan once it starts,
 * the choices made after missed weeks and the demon.
 */
data class ProgrammeRecord(
    val id: String,
    val status: ProgrammeStatus,
    val createdAt: Instant,
    /** Set once the programme is closed: complete, or abandoned. */
    val endedAt: Instant?,
    /** The day the baseline started, or null when the usual day was typed in. */
    val baselineStartedOn: LocalDate?,
    /** The seven days a measured usual day came from. */
    val baselineDays: ClosedRange<LocalDate>?,
    /** The usual day the plan steps down from, once it's kept. */
    val usualDay: UsualDay?,
    /** The plan, once it starts, with its weeks, mixes and results. */
    val plan: Programme?,
    /** The choices made after missed weeks, by week number. */
    val choices: Map<Int, WeekChoice>,
    /** The demon, once the plan starts. */
    val demon: Demon?,
)

/** A demon as stored. A null [name] or [variant] shows the app's default. */
data class Demon(
    val id: String,
    val programmeId: String,
    val name: String?,
    val creature: Creature,
    val variant: String?,
    val maxHp: Int,
    val createdAt: Instant,
    val defeatedAt: Instant?,
)

/** How a demon looks: what the user named and picked. */
data class DemonLook(val name: String?, val creature: Creature, val variant: String?)

/**
 * The programme (PLAN §6): the baseline, the plan built from it, and the weekly results with
 * the hits they earn. Results are worked out whenever the app looks (ROADMAP P3-3): reading the
 * current programme settles what's due, and so does every write to the entries, through
 * [ResultSettler].
 *
 * A programme is current from its baseline until it's closed. Only one is current at a time.
 */
class ProgrammeRepository(
    private val database: AppDatabase,
    private val products: ProductRepository,
    private val settings: SettingsStore,
    private val catalogue: () -> Catalogue,
    private val clock: Clock,
    /** The phone's current zone, for working out today and when results are due. */
    private val zone: () -> ZoneId = { clock.zone },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ResultSettler {
    private val dao = database.programmes()
    private val game = database.game()
    private val entries = database.entries()
    private val productDao = database.products()

    /** Keeps settling and the writes that read a programme first from running into each other. */
    private val mutex = Mutex()

    /** The moment the app looks at the programme now: the time, the phone's zone and the day start. */
    suspend fun clock(): ProgrammeClock = ProgrammeClock(clock.instant(), zone(), settings.current().dayBoundary)

    // Reading.

    /**
     * The current programme, or null when there's none. Collecting it settles whatever results
     * are due first, then it follows every change. A result that falls due while it's being
     * collected comes in with the next write or [settle].
     */
    fun observeCurrent(): Flow<ProgrammeRecord?> =
        combine(dao.observeCurrent(), productDao.observeAllIncludingHidden()) { parts, list ->
            parts?.toRecord(list.associate { it.id to it.toDomain() })
        }.distinctUntilChanged().onStart { settleDue() }

    /** The current programme now, with the results that are due settled first. */
    suspend fun current(): ProgrammeRecord? = mutex.withLock {
        settleLocked()
        loadCurrent()
    }

    // The baseline.

    /**
     * Starts measuring a baseline from today, as a new programme. If the last 7 full days are
     * logged well enough, they're the baseline at once (ROADMAP P3-1). Refused while another
     * programme is current: [startNew] closes it first.
     */
    suspend fun startBaseline(): ProgrammeRecord = mutex.withLock {
        check(dao.currentRow() == null) { "A programme is already current" }
        dao.insert(newProgramme(baselineStartedOn = clock().today))
        checkNotNull(loadCurrent())
    }

    /** The baseline's seven days on today, while the current programme is at its baseline. */
    suspend fun measureBaseline(): BaselineDays? {
        val row = dao.currentRow()?.takeIf { it.status == ProgrammeStatus.BASELINE } ?: return null
        val today = clock().today
        return measure(row, today, entries.liveOn(today.minusDays(BaselineDays.DAYS.toLong()).toEpochDay(), today.toEpochDay()))
    }

    /** [measureBaseline], following the logs as they change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBaseline(): Flow<BaselineDays?> = dao.observeCurrentRow().flatMapLatest { row ->
        if (row == null || row.status != ProgrammeStatus.BASELINE) {
            flowOf(null)
        } else {
            flow {
                val today = clock().today
                val from = today.minusDays(BaselineDays.DAYS.toLong()).toEpochDay()
                emitAll(entries.observeLiveOn(from, today.toEpochDay()).map { measure(row, today, it) })
            }
        }
    }

    /** The usual day the baseline measured, to confirm or edit, once it's ready. */
    suspend fun measuredUsualDay(): UsualDay? =
        measureBaseline()?.usualDay(productDao.allIncludingHidden().map { it.toDomain() })

    /**
     * Keeps [usual] as the day the plan steps down from: the measured one confirmed or edited,
     * or one typed in, product by product. A typed day needs no baseline, so it starts a
     * programme when there's none. A measured one keeps the days it came from.
     *
     * Its products that aren't in the user's list yet are added, off the quick log, so the day
     * reads back as the products themselves: a catalogue one keeps its pack limits and the line
     * the plan finds lower strengths in.
     */
    suspend fun keepUsualDay(usual: UsualDay): ProgrammeRecord = mutex.withLock {
        val today = clock().today
        database.withTransaction {
            val row = dao.currentRow() ?: newProgramme(baselineStartedOn = null).also { dao.insert(it) }
            check(row.status == ProgrammeStatus.BASELINE) { "The usual day can't change once the plan has started" }
            val now = clock.millis()
            for (product in usual.mix.products) {
                if (productDao.get(product.id) == null) addProduct(product, hidden = false, now)
            }
            val measured = if (usual.source == BaselineSource.MEASURED) {
                measure(row, today, entries.liveOn(today.minusDays(BaselineDays.DAYS.toLong()).toEpochDay(), today.toEpochDay()))
                    .takeIf { it.isReady }
            } else {
                null
            }
            dao.update(
                row.copy(
                    baselineStart = measured?.start?.toEpochDay(),
                    baselineEnd = measured?.end?.toEpochDay(),
                    baselineSource = usual.source,
                    baselineMeasuredMicrograms = usual.measured?.micrograms,
                    baselineDailyMicrograms = usual.total.micrograms,
                ),
            )
            dao.replaceUsualDay(row.id, usual.toEntities(row.id))
        }
        checkNotNull(loadCurrent())
    }

    // The plan.

    /**
     * The plan the usual day leads to, to show and edit before it starts: [length] weeks from
     * [startDate] (tomorrow unless picked), down a ladder from the usual day through the user's
     * products and the catalogue in their market. Null until a usual day is kept.
     */
    suspend fun buildPlan(length: ProgrammeLength = ProgrammeLength.DEFAULT, startDate: LocalDate? = null): Programme? {
        val record = loadCurrent()?.takeIf { it.status == ProgrammeStatus.BASELINE } ?: return null
        val usual = record.usualDay ?: return null
        val own = productDao.allIncludingHidden().map { it.toDomain() }
        val market = settings.current().country ?: Markets.ELSEWHERE
        val ladder = Ladder.build(usual, own, catalogue(), market)
        return Programme.create(ladder, length, startDate ?: Programme.defaultStart(clock().today))
    }

    /**
     * Starts [plan], from [buildPlan] and perhaps edited week by week. The products its steps
     * switch to are added to the user's products, or shown again if hidden, without joining the
     * quick log. The demon gets a row of its own with 10 HP for each week: [look], or else
     * [nextDemon].
     */
    suspend fun startPlan(plan: Programme, look: DemonLook? = null): ProgrammeRecord = mutex.withLock {
        val now = clock()
        val demon = look ?: nextDemon()
        database.withTransaction {
            val record = checkNotNull(loadCurrent()) { "There's no baseline to start a plan from" }
            check(record.status == ProgrammeStatus.BASELINE) { "The plan has already started" }
            val usual = checkNotNull(record.usualDay) { "Keep a usual day before starting the plan" }
            require(usual.mix.signature() == plan.usualDay.mix.signature()) { "The plan isn't from the usual day kept" }
            require(plan.weeks.all { it.result == WeekResult.PENDING && it.kind == WeekKind.PLANNED }) { "The plan has already run" }
            require(plan.startDate >= now.today) { "A plan can't start before today: ${plan.startDate}" }

            addProductsFor(plan)
            val row = checkNotNull(dao.get(record.id))
            dao.update(
                row.copy(
                    status = ProgrammeStatus.ACTIVE,
                    lengthWeeks = plan.length.weeks,
                    nrtFreeWeeks = ProgrammeLength.NRT_FREE_WEEKS,
                    startDate = plan.startDate.toEpochDay(),
                ),
            )
            dao.replaceLadder(record.id, plan.ladder.toEntities(record.id))
            game.insertDemon(
                DemonEntity(
                    id = newId(),
                    programmeId = record.id,
                    name = demon.name?.trim()?.ifBlank { null },
                    creature = demon.creature,
                    variant = demon.variant,
                    maxHp = plan.maxHp,
                    createdAt = now.now.toEpochMilli(),
                    defeatedAt = null,
                ),
            )
            saveWeeks(record.id, plan, emptyMap())
        }
        checkNotNull(loadCurrent())
    }

    /**
     * The demon a new plan starts with, from settings: the one picked in onboarding, or the last
     * programme's back for a rematch, since closing a programme puts its demon there. A restyle
     * before the plan starts ([GameRepository.restyleDemon]) changes it.
     */
    suspend fun nextDemon(): DemonLook {
        val current = settings.current()
        return DemonLook(current.demonName, current.demonCreature, current.demonVariant)
    }

    /** Moves week [number] of the running plan to [step] on its ladder, before the week starts. */
    suspend fun editWeek(number: Int, step: Int): Programme = mutex.withLock {
        settleLocked()
        val today = clock().today
        database.withTransaction {
            val record = checkNotNull(loadCurrent()) { "There's no programme" }
            val plan = checkNotNull(record.plan) { "The plan hasn't started" }
            val edited = plan.edit(number, step, today)
            saveWeeks(record.id, edited, record.choices)
            edited
        }
    }

    /**
     * Sets the extra goals (PLAN §6.3) for the week running now, or week 1 before the plan
     * starts, and carries them into the weeks after it ([ExtraGoals.apply]). Past weeks keep
     * theirs. False when there's no week to set them for: no plan, or an NRT-free week.
     */
    suspend fun setGoals(goals: WeekGoals): Boolean = mutex.withLock {
        settleLocked()
        val today = clock().today
        database.withTransaction {
            val record = loadCurrent() ?: return@withTransaction false
            val plan = record.plan ?: return@withTransaction false
            if (record.status != ProgrammeStatus.ACTIVE) return@withTransaction false
            val week = ExtraGoals.weekToSet(plan, today) ?: return@withTransaction false
            saveWeeks(record.id, ExtraGoals.apply(plan, week.number, goals), record.choices)
            true
        }
    }

    /** The missed week still waiting for the user's choice, if there is one. */
    suspend fun awaitingChoice(): ProgrammeWeek? {
        val record = current() ?: return null
        val plan = record.plan ?: return null
        return MissedWeekChoices.awaiting(plan, record.choices, clock())
    }

    /**
     * Records the choice after missed week [number]: carry on, or repeat it, which adds a week
     * and 10 HP. False when that choice can't be made now (see [MissedWeekChoices]).
     */
    suspend fun chooseAfterMiss(number: Int, choice: MissedWeekChoice): Boolean = mutex.withLock {
        settleLocked()
        val now = clock()
        database.withTransaction {
            val record = loadCurrent() ?: return@withTransaction false
            val plan = record.plan ?: return@withTransaction false
            if (!MissedWeekChoices.canChoose(plan, number, choice, record.choices, now)) return@withTransaction false
            // Picking carry on again keeps the time it was first picked, and so the day it can change.
            val choices = if (record.choices[number]?.choice == choice) {
                record.choices
            } else {
                record.choices + (number to WeekChoice(number, choice, now.now))
            }
            val after = if (choice == MissedWeekChoice.REPEAT) plan.repeat(number, now) else plan
            saveWeeks(record.id, after, choices)
            true
        }
    }

    // Results.

    override suspend fun settleDue() {
        settle()
    }

    /**
     * Brings in every result that's due, and writes the hits they earned to the ledger, once.
     * Null when nothing new came in.
     */
    suspend fun settle(): Settlement? = mutex.withLock { settleLocked() }

    private suspend fun settleLocked(): Settlement? {
        val now = clock()
        return database.withTransaction {
            val record = loadCurrent() ?: return@withTransaction null
            val plan = record.plan ?: return@withTransaction null
            if (record.status != ProgrammeStatus.ACTIVE && record.status != ProgrammeStatus.OVERTIME) return@withTransaction null
            val next = plan.weeks.firstOrNull { it.result == WeekResult.PENDING } ?: return@withTransaction null
            if (now.now < now.resultDue(next)) return@withTransaction null
            val logged = entries.liveFrom(plan.startDate.toEpochDay()).map { it.toDomain() }
            val settlement = plan.settle(logged, now)
            saveWeeks(record.id, settlement.programme, record.choices)
            writeHits(record.id, settlement.programme.earnedHits)
            settlement
        }
    }

    /** Writes [hits] to the ledger. A week's hit has a fixed ID, so writing it again changes nothing. */
    private suspend fun writeHits(programmeId: String, hits: List<EarnedHit>) {
        game.insertLedgerIgnoringExisting(
            hits.map { hit ->
                val kind = LedgerKind.of(hit.kind)
                HitLedgerEntity(
                    id = "$programmeId-week-${hit.week}",
                    programmeId = programmeId,
                    kind = kind,
                    hits = kind.hits,
                    week = hit.week,
                    earnedBy = hit.earnedBy,
                    createdAt = hit.at.toEpochMilli(),
                )
            },
        )
    }

    // Ending.

    // Ending. A programme that closes leaves its demon in settings, as the one the next plan
    // starts with (PLAN §6.5: the same one back for round two, unless it's restyled first).

    /**
     * Closes the current programme once it's complete and its demon is defeated: every hit
     * spent, so it's at 0 HP (PLAN §6.5). The defeat's time is set here if it isn't yet. False
     * if it isn't complete, or the demon still has HP.
     */
    suspend fun end(): Boolean {
        mutex.withLock {
            settleLocked()
            val closed = database.withTransaction {
                val row = dao.currentRow()?.takeIf { it.status == ProgrammeStatus.COMPLETE } ?: return@withTransaction null
                if (!defeatLocked(row)) return@withTransaction null
                dao.update(row.copy(endedAt = clock.millis()))
                Closed(game.demonOf(row.id))
            } ?: return false
            keepLook(closed.demon)
            return true
        }
    }

    /**
     * Stops the current programme where it is, as abandoned: its weeks, results and hits are
     * kept, and the demon stays as it was. False when there's none, or it's complete.
     */
    suspend fun abandon(): Boolean {
        mutex.withLock {
            settleLocked()
            val row = dao.currentRow()?.takeIf { it.status != ProgrammeStatus.COMPLETE } ?: return false
            dao.update(row.copy(status = ProgrammeStatus.ABANDONED, endedAt = clock.millis()))
            keepLook(game.demonOf(row.id))
            return true
        }
    }

    /**
     * Starts a new programme from current use (PLAN §6.5). The current one closes, abandoned, or
     * complete once its demon is defeated as [end] says, and a baseline starts today, which the
     * last 7 full days can fill at once. Refused while a complete programme's demon has HP left.
     */
    suspend fun startNew(): ProgrammeRecord = mutex.withLock {
        settleLocked()
        val today = clock().today
        val closed = database.withTransaction {
            val closed = dao.currentRow()?.let { row ->
                val ended = clock.millis()
                if (row.status == ProgrammeStatus.COMPLETE) {
                    check(defeatLocked(row)) { "The demon still has HP: spend the hits before starting again" }
                    dao.update(row.copy(endedAt = ended))
                } else {
                    dao.update(row.copy(status = ProgrammeStatus.ABANDONED, endedAt = ended))
                }
                Closed(game.demonOf(row.id))
            }
            dao.insert(newProgramme(baselineStartedOn = today))
            closed
        }
        keepLook(closed?.demon)
        checkNotNull(loadCurrent())
    }

    /** A programme just closed, with its demon if it had one. */
    private class Closed(val demon: DemonEntity?)

    /**
     * Lands the defeat of [row]'s demon if it's at 0 HP, keeping the first time it fell. True
     * when it's defeated, or there's no demon to fight.
     */
    private suspend fun defeatLocked(row: ProgrammeEntity): Boolean {
        val demon = game.currentDemon()?.takeIf { it.demon.programmeId == row.id } ?: return game.demonOf(row.id) == null
        if (demon.balance().hp > 0) return false
        game.setDefeated(demon.demon.id, clock.millis())
        return true
    }

    /** Puts a closed programme's demon in settings, as the one the next plan starts with. */
    private suspend fun keepLook(demon: DemonEntity?) {
        if (demon != null) settings.setDemon(demon.name, demon.creature, demon.variant)
    }

    // Helpers.

    private suspend fun loadCurrent(): ProgrammeRecord? {
        val parts = dao.current() ?: return null
        return parts.toRecord(productDao.allIncludingHidden().associate { it.id to it.toDomain() })
    }

    /** Saves [programme]'s weeks, its status and the demon's max HP, which follow from them. */
    private suspend fun saveWeeks(programmeId: String, programme: Programme, choices: Map<Int, WeekChoice>) {
        dao.replaceWeeks(programmeId, programme.toWeekEntities(programmeId, choices))
        dao.setStatus(programmeId, programme.status)
        game.setMaxHp(programmeId, programme.maxHp)
    }

    /**
     * Makes sure every product [plan] uses is in the user's list. Lower strengths from the
     * catalogue are added, hidden ones the steps use are shown again, and a usual-day product
     * that's gone comes back hidden, so the plan always reads. Each is looked up as it comes, so
     * a product the steps have just added is never added again.
     */
    private suspend fun addProductsFor(plan: Programme) {
        val now = clock.millis()
        for (product in plan.ladder.steps.flatMap { it.products }.distinctBy { it.id }) {
            val existing = productDao.get(product.id)
            when {
                existing == null -> addProduct(product, hidden = false, now)
                existing.isHidden -> productDao.setHidden(product.id, false)
            }
        }
        for (product in plan.usualDay.mix.products) {
            if (productDao.get(product.id) == null) addProduct(product, hidden = true, now)
        }
    }

    /**
     * Adds [product] to the user's list, off the quick log: from the catalogue when it's one of
     * the catalogue's, with its pack limits, or else as it is.
     */
    private suspend fun addProduct(product: UserProduct, hidden: Boolean, now: Long) {
        if (catalogue().find(product.id) != null) {
            products.addFromCatalogue(product.id, addToQuickLog = false)
            if (hidden) productDao.setHidden(product.id, true)
        } else {
            productDao.upsert(product.copy(quickLogPosition = null, isHidden = hidden).toEntity(now))
        }
    }

    private fun newProgramme(baselineStartedOn: LocalDate?) = ProgrammeEntity(
        id = newId(),
        status = ProgrammeStatus.BASELINE,
        createdAt = clock.millis(),
        endedAt = null,
        baselineStartedOn = baselineStartedOn?.toEpochDay(),
        baselineStart = null,
        baselineEnd = null,
        baselineSource = null,
        baselineMeasuredMicrograms = null,
        baselineDailyMicrograms = null,
        lengthWeeks = null,
        nrtFreeWeeks = null,
        startDate = null,
    )

    private fun measure(row: ProgrammeEntity, today: LocalDate, logged: List<EntryEntity>): BaselineDays =
        BaselineDays.measure(today, logged.map { it.toDomain() }, row.baselineStartedOn?.let(LocalDate::ofEpochDay))

    /** A mix by product and units only, since the products' other details can change. */
    private fun Mix.signature(): Map<String, Int> = items.associate { it.product.id to it.units }
}
