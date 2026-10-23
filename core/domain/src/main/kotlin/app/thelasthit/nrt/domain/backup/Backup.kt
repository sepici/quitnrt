package app.thelasthit.nrt.domain.backup

import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A full backup: everything needed to restore the app on a new phone. Doses are whole
 * micrograms and times are ISO-8601, so the file reads sensibly in any text editor.
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
) {
    @Serializable
    data class Settings(
        val dayStart: String,
        val country: String? = null,
        val stoppedSmokingOn: String? = null,
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

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "the-last-hit-backup"
        const val VERSION = 1

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
                throw BackupException("This file isn't a The Last Hit backup.", e)
            }
            if (backup.format != FORMAT) throw BackupException("This file isn't a The Last Hit backup.")
            if (backup.version > VERSION) throw BackupException("This backup is from a newer version of the app.")
            return backup
        }
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
