package app.thelasthit.nrt.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm

/** A product in the user's list. Doses are in micrograms and limits are in the logged unit. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val catalogueId: String?,
    val name: String,
    val shortName: String,
    val form: ProductForm,
    val unit: DoseUnit,
    val perUnitMicrograms: Long,
    val cartridgeMicrograms: Long?,
    val puffsPerCartridge: Int?,
    val labelMaxPerDay: Int?,
    val labelWindowMax: Int?,
    val labelWindowMinutes: Long?,
    val quickLogPosition: Int?,
    val isHidden: Boolean,
    val createdAt: Long,
)

/**
 * One logged use. There's deliberately no foreign key to products: entries keep a snapshot of
 * the product, so history survives a product being edited or removed.
 */
@Entity(
    tableName = "entries",
    indices = [Index("logicalEpochDay"), Index("occurredAt"), Index("productId")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val form: ProductForm,
    val unit: DoseUnit,
    val perUnitMicrograms: Long,
    val quantity: Int,
    val totalMicrograms: Long,
    /** Epoch milliseconds, UTC. */
    val occurredAt: Long,
    val zoneId: String,
    val logicalEpochDay: Long,
    val kind: EntryKind,
    val cravingStrength: Int?,
    val note: String?,
    val source: EntrySource,
    val createdAt: Long,
    val updatedAt: Long,
    /** Set when deleted, so undo works. Purged after 30 days. */
    val deletedAt: Long?,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagKey"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagKey")],
)
data class EntryTagEntity(
    val entryId: String,
    val tagKey: String,
)

/** Built-in tags keep their key and get their text from the app's strings unless renamed. */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val key: String,
    val group: TagGroup,
    val customName: String?,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
)

data class EntryWithTags(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val tags: List<EntryTagEntity>,
)
