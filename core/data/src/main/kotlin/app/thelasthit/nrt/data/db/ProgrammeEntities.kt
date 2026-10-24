package app.thelasthit.nrt.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult

// The programme (PLAN §6), from version 2.

/**
 * A programme, from its baseline to its end. Dates are logical days as epoch days. [endedAt] is
 * set when it's closed, after it's complete or when it's abandoned; until then it's the current
 * one. The baseline's details are set once the usual day is kept, and the plan's once it starts.
 */
@Entity(tableName = "programmes", indices = [Index("endedAt")])
data class ProgrammeEntity(
    @PrimaryKey val id: String,
    val status: ProgrammeStatus,
    val createdAt: Long,
    val endedAt: Long?,
    /** The day the baseline started on, or null when the usual day was typed. */
    val baselineStartedOn: Long?,
    /** The seven days a measured usual day came from. */
    val baselineStart: Long?,
    val baselineEnd: Long?,
    val baselineSource: BaselineSource?,
    /** The average a day the logs showed, which can differ from the usual day's whole units. */
    val baselineMeasuredMicrograms: Long?,
    /** The usual day's mg. */
    val baselineDailyMicrograms: Long?,
    val lengthWeeks: Int?,
    val nrtFreeWeeks: Int?,
    val startDate: Long?,
)

/**
 * Whole units a day of one product in a mix, with the product as it was when the mix was made,
 * so a plan still reads if the product is gone.
 */
data class UnitsColumns(
    val productId: String,
    val productName: String,
    val form: ProductForm,
    val unit: DoseUnit,
    val perUnitMicrograms: Long,
    val unitsPerDay: Int,
)

/** The usual day: a row for each product. */
@Entity(
    tableName = "baseline_units",
    primaryKeys = ["programmeId", "productId"],
    foreignKeys = [
        ForeignKey(entity = ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BaselineUnitEntity(
    val programmeId: String,
    @Embedded val units: UnitsColumns,
)

/** The steps of the plan's ladder below the usual day, from 1: a row for each product at each step. */
@Entity(
    tableName = "ladder_units",
    primaryKeys = ["programmeId", "step", "productId"],
    foreignKeys = [
        ForeignKey(entity = ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LadderUnitEntity(
    val programmeId: String,
    val step: Int,
    @Embedded val units: UnitsColumns,
)

/**
 * One week of a programme, by its number in the run. [ladderStep] is null for an NRT-free week.
 * The extra goals (PLAN §6.3) are null while they're off. [choice] is what was picked after the
 * week was missed, if anything.
 *
 * [maxUnitsPerDay] is no longer used: the cap on units a day became one for each product, in
 * `programme_week_caps` from version 3, and this column stays null.
 */
@Entity(
    tableName = "programme_weeks",
    primaryKeys = ["programmeId", "number"],
    foreignKeys = [
        ForeignKey(entity = ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ProgrammeWeekEntity(
    val programmeId: String,
    val number: Int,
    val startDate: Long,
    val endDate: Long,
    val kind: WeekKind,
    val ladderStep: Int?,
    val dailyGuideMicrograms: Long,
    val weeklyAllowanceMicrograms: Long,
    val isNrtFree: Boolean,
    val maxUnitsPerDay: Int?,
    val minGapMinutes: Int?,
    /** The wall-clock time the day's first dose waits for, as minutes after midnight. */
    val firstDoseNotBeforeMinuteOfDay: Int?,
    val result: WeekResult,
    val resultAt: Long?,
    val choice: MissedWeekChoice?,
    val choiceAt: Long?,
    /**
     * The total the result was judged on, written once with the result, from version 4. Null
     * while the result is to come, and for results that came in before version 4.
     */
    val resultTotalMicrograms: Long? = null,
)

/** A week's daily mix: a row for each product. */
@Entity(
    tableName = "programme_week_units",
    primaryKeys = ["programmeId", "weekNumber", "productId"],
    foreignKeys = [
        ForeignKey(
            entity = ProgrammeWeekEntity::class,
            parentColumns = ["programmeId", "number"],
            childColumns = ["programmeId", "weekNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgrammeWeekUnitEntity(
    val programmeId: String,
    val weekNumber: Int,
    @Embedded val units: UnitsColumns,
)

/** A week's extra goal of the most units a day of one product (PLAN §6.3), from version 3. */
@Entity(
    tableName = "programme_week_caps",
    primaryKeys = ["programmeId", "weekNumber", "productId"],
    foreignKeys = [
        ForeignKey(
            entity = ProgrammeWeekEntity::class,
            parentColumns = ["programmeId", "number"],
            childColumns = ["programmeId", "weekNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgrammeWeekCapEntity(
    val programmeId: String,
    val weekNumber: Int,
    val productId: String,
    val maxUnitsPerDay: Int,
)

// The game (PLAN §7 and §8), from version 2.

/** The demon a programme fights. Its HP comes from [maxHp] and the hit ledger. */
@Entity(
    tableName = "demons",
    foreignKeys = [
        ForeignKey(entity = ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("programmeId", unique = true)],
)
data class DemonEntity(
    @PrimaryKey val id: String,
    val programmeId: String,
    val name: String?,
    val creature: Creature,
    val variant: String?,
    val maxHp: Int,
    val createdAt: Long,
    val defeatedAt: Long?,
)

/**
 * One row of the hit ledger: [hits] earned by a week's result (positive) or spent (negative).
 * A week earns at most one hit, so ([programmeId], [week]) is unique. Spends have no week.
 */
@Entity(
    tableName = "hit_ledger",
    foreignKeys = [
        ForeignKey(entity = ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["programmeId", "week"], unique = true)],
)
data class HitLedgerEntity(
    @PrimaryKey val id: String,
    val programmeId: String,
    val kind: LedgerKind,
    val hits: Int,
    /** The week an earned hit pays for. */
    val week: Int?,
    /** The met week whose result brought it. */
    val earnedBy: Int?,
    val createdAt: Long,
)

/** An achievement unlocked. The definitions live in code. */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
)

/** A piece of gear unlocked. Which one is equipped is a setting. */
@Entity(tableName = "gear")
data class GearEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
)

// Advice (PLAN §9), from version 2.

/** One run of the breathing timer. */
@Entity(tableName = "breathing_sessions")
data class BreathingSessionEntity(
    @PrimaryKey val id: String,
    val type: BreathingPattern,
    val startedAt: Long,
    val durationSeconds: Long,
    val roundsCompleted: Int,
    val ranToEnd: Boolean,
)

/** A programme with everything stored about it. */
data class ProgrammeWithParts(
    @Embedded val programme: ProgrammeEntity,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val usualDay: List<BaselineUnitEntity>,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val ladder: List<LadderUnitEntity>,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val weeks: List<ProgrammeWeekEntity>,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val weekUnits: List<ProgrammeWeekUnitEntity>,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val demons: List<DemonEntity>,
    @Relation(parentColumn = "id", entityColumn = "programmeId")
    val weekCaps: List<ProgrammeWeekCapEntity> = emptyList(),
)

/** A programme's weeks as rows: each week, its mix, and its caps on units a day. */
data class WeekRows(
    val weeks: List<ProgrammeWeekEntity>,
    val units: List<ProgrammeWeekUnitEntity>,
    val caps: List<ProgrammeWeekCapEntity>,
)

/** A demon with its ledger's sums. */
data class DemonWithLedger(
    @Embedded val demon: DemonEntity,
    val earned: Int,
    val spent: Int,
)
