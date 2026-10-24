package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.programme.BaselineSource
import app.thelasthit.nrt.domain.programme.LedgerKind
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekKind
import app.thelasthit.nrt.domain.programme.WeekResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A full backup: everything needed to restore the app on a new phone. Doses are whole
 * micrograms and times are ISO-8601, so the file reads sensibly in any text editor.
 *
 * Version 2 adds the programmes, demons, hit ledger, achievements, gear and breathing sessions.
 * A version 1 file has none of them, so it reads as a backup with those lists empty.
 */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val settings: Settings,
    val products: List<Product>,
    val tags: List<Tag>,
    val entries: List<Entry>,
    val programmes: List<Programme> = emptyList(),
    val demons: List<Demon> = emptyList(),
    val hitLedger: List<LedgerRow> = emptyList(),
    val achievements: List<Unlock> = emptyList(),
    val gear: List<Unlock> = emptyList(),
    val breathingSessions: List<BreathingSession> = emptyList(),
) {
    /** The settings worth carrying to a new phone. Version 1 has only the first three. */
    @Serializable
    data class Settings(
        val dayStart: String,
        val country: String? = null,
        val stoppedSmokingOn: String? = null,
        /** The demon picked in onboarding, until a plan starts and it gets a row of its own. */
        val demonName: String? = null,
        val demonCreature: String? = null,
        val demonVariant: String? = null,
        val soundOn: Boolean? = null,
        val equippedGear: String? = null,
        val showAbsorbed: Boolean? = null,
    )

    @Serializable
    data class Product(
        val id: String,
        val catalogueId: String? = null,
        val name: String,
        val shortName: String,
        val form: ProductForm,
        val unit: DoseUnit,
        @SerialName("perUnitUg") val perUnitMicrograms: Long,
        @SerialName("cartridgeUg") val cartridgeMicrograms: Long? = null,
        val puffsPerCartridge: Int? = null,
        val labelMaxPerDay: Int? = null,
        val labelWindowMax: Int? = null,
        val labelWindowMinutes: Long? = null,
        val quickLogPosition: Int? = null,
        val isHidden: Boolean = false,
    )

    @Serializable
    data class Tag(
        val key: String,
        val group: TagGroup,
        val customName: String? = null,
        val isBuiltIn: Boolean,
        val isHidden: Boolean = false,
        val sortOrder: Int,
    )

    @Serializable
    data class Entry(
        val id: String,
        val productId: String,
        val productName: String,
        val form: ProductForm,
        val unit: DoseUnit,
        @SerialName("perUnitUg") val perUnitMicrograms: Long,
        val quantity: Int,
        @SerialName("totalUg") val totalMicrograms: Long,
        val occurredAt: String,
        val zone: String,
        val logicalDate: String,
        val kind: EntryKind = EntryKind.UNSPECIFIED,
        val cravingStrength: Int? = null,
        val tags: List<String> = emptyList(),
        val note: String? = null,
        val source: EntrySource = EntrySource.APP,
    )

    /**
     * A programme with its usual day, the ladder down from it and its weeks. Dates are logical
     * days. Before a plan starts it has only the baseline's details.
     */
    @Serializable
    data class Programme(
        val id: String,
        val status: ProgrammeStatus,
        val createdAt: String,
        val endedAt: String? = null,
        val baselineStartedOn: String? = null,
        val baselineStart: String? = null,
        val baselineEnd: String? = null,
        val baselineSource: BaselineSource? = null,
        @SerialName("baselineMeasuredUg") val baselineMeasuredMicrograms: Long? = null,
        val lengthWeeks: Int? = null,
        val nrtFreeWeeks: Int? = null,
        val startDate: String? = null,
        /** The usual day: whole units a day of each product. */
        val usualDay: List<Units> = emptyList(),
        /** The steps below the usual day, from 1. */
        val ladder: List<Step> = emptyList(),
        val weeks: List<Week> = emptyList(),
    )

    /** Whole units a day of one product, with the product as it was when the plan was made. */
    @Serializable
    data class Units(
        val productId: String,
        val productName: String,
        val form: ProductForm,
        val unit: DoseUnit,
        @SerialName("perUnitUg") val perUnitMicrograms: Long,
        val unitsPerDay: Int,
    )

    @Serializable
    data class Step(val step: Int, val units: List<Units>)

    @Serializable
    data class Week(
        val number: Int,
        val startDate: String,
        val kind: WeekKind = WeekKind.PLANNED,
        /** Null for an NRT-free week. */
        val step: Int? = null,
        @SerialName("dailyGuideUg") val dailyGuideMicrograms: Long,
        @SerialName("weeklyAllowanceUg") val weeklyAllowanceMicrograms: Long,
        val units: List<Units> = emptyList(),
        val result: WeekResult = WeekResult.PENDING,
        val resultAt: String? = null,
        val choice: MissedWeekChoice? = null,
        val choiceAt: String? = null,
        /** The extra goals, each absent while it's off. The cap on units a day is one for each product. */
        val caps: List<Cap> = emptyList(),
        val minGapMinutes: Int? = null,
        /** A time on the wall clock, such as "07:30". */
        val firstDoseNotBefore: String? = null,
    )

    /** The most units a day of one product, an extra goal. */
    @Serializable
    data class Cap(val productId: String, val maxUnitsPerDay: Int)

    @Serializable
    data class Demon(
        val id: String,
        val programmeId: String,
        val name: String? = null,
        /** The creature's key, such as "dragon". */
        val creature: String,
        val variant: String? = null,
        val maxHp: Int,
        val createdAt: String,
        val defeatedAt: String? = null,
    )

    /** One row of the hit ledger: hits earned (positive) or spent (negative). */
    @Serializable
    data class LedgerRow(
        val id: String,
        val programmeId: String,
        val kind: LedgerKind,
        val hits: Int,
        /** The week an earned hit pays for, and the met week that brought it. */
        val week: Int? = null,
        val earnedBy: Int? = null,
        val at: String,
    )

    /** An achievement or a piece of gear, and when it was unlocked. */
    @Serializable
    data class Unlock(val key: String, val unlockedAt: String)

    @Serializable
    data class BreathingSession(
        val id: String,
        val pattern: BreathingPattern,
        val startedAt: String,
        val durationSeconds: Long,
        val rounds: Int,
        val ranToEnd: Boolean,
    )

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "the-last-hit-backup"
        const val VERSION = 2

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /** Reads a backup, refusing files that aren't ours or come from a newer version of the app. */
        fun decode(text: String): Backup {
            val backup = try {
                json.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw BackupException(BackupException.Reason.NOT_A_BACKUP, "This file isn't a The Last Hit backup.", e)
            }
            if (backup.format != FORMAT) {
                throw BackupException(BackupException.Reason.NOT_A_BACKUP, "This file isn't a The Last Hit backup.")
            }
            if (backup.version > VERSION) {
                throw BackupException(BackupException.Reason.NEWER_VERSION, "This backup is from a newer version of the app.")
            }
            return backup
        }
    }
}

/** Why a file can't be restored. The app shows its own words for each [reason]. */
class BackupException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION }
}
