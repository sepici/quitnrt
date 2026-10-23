package app.thelasthit.nrt.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE isHidden = 0 ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE quickLogPosition IS NOT NULL AND isHidden = 0 ORDER BY quickLogPosition")
    fun observeQuickLog(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE quickLogPosition IS NOT NULL ORDER BY quickLogPosition")
    suspend fun quickLog(): List<ProductEntity>

    @Query("UPDATE products SET quickLogPosition = :position WHERE id = :id")
    suspend fun setQuickLogPosition(id: String, position: Int?)

    @Query("UPDATE products SET quickLogPosition = NULL")
    suspend fun clearQuickLog()

    @Query("SELECT * FROM products")
    suspend fun allIncludingHidden(): List<ProductEntity>

    @Query("SELECT * FROM products")
    fun observeAllIncludingHidden(): Flow<List<ProductEntity>>

    @Query("UPDATE products SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT createdAt FROM products WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(products: List<ProductEntity>): List<Long>

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
abstract class EntryDao {
    @Insert
    abstract suspend fun insert(entry: EntryEntity)

    @Update
    abstract suspend fun update(entry: EntryEntity)

    @Insert
    abstract suspend fun insertTags(tags: List<EntryTagEntity>)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    abstract suspend fun clearTags(entryId: String)

    @Transaction
    open suspend fun insertWithTags(entry: EntryEntity, tagKeys: Collection<String>) {
        insert(entry)
        insertTags(tagKeys.map { EntryTagEntity(entry.id, it) })
    }

    @Transaction
    open suspend fun updateWithTags(entry: EntryEntity, tagKeys: Collection<String>) {
        update(entry)
        clearTags(entry.id)
        insertTags(tagKeys.map { EntryTagEntity(entry.id, it) })
    }

    @Query("UPDATE entries SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    abstract suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE entries SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    abstract suspend fun restore(id: String, at: Long)

    @Query("DELETE FROM entries WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    abstract suspend fun purgeDeletedBefore(before: Long): Int

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    abstract suspend fun get(id: String): EntryWithTags?

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND logicalEpochDay BETWEEN :fromDay AND :toDay ORDER BY occurredAt DESC")
    abstract fun observeDays(fromDay: Long, toDay: Long): Flow<List<EntryWithTags>>

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND logicalEpochDay = :day ORDER BY occurredAt DESC")
    abstract suspend fun onDay(day: Long): List<EntryWithTags>

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND occurredAt BETWEEN :fromMs AND :toMs ORDER BY occurredAt DESC")
    abstract suspend fun between(fromMs: Long, toMs: Long): List<EntryWithTags>

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND productId = :productId AND occurredAt >= :sinceMs ORDER BY occurredAt DESC")
    abstract suspend fun recentForProduct(productId: String, sinceMs: Long): List<EntryWithTags>

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL")
    abstract suspend fun allLive(): List<EntryEntity>

    @Query("SELECT MIN(logicalEpochDay) FROM entries WHERE deletedAt IS NULL")
    abstract fun observeFirstDay(): Flow<Long?>

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND productId = :productId ORDER BY occurredAt DESC")
    abstract fun observeWithProduct(productId: String): Flow<List<EntryWithTags>>

    @Transaction
    @Query(
        """
        SELECT entries.* FROM entries JOIN entry_tags ON entry_tags.entryId = entries.id
        WHERE entries.deletedAt IS NULL AND entry_tags.tagKey = :tagKey
        ORDER BY entries.occurredAt DESC
        """,
    )
    abstract fun observeWithTag(tagKey: String): Flow<List<EntryWithTags>>

    @Query("SELECT productId AS productId, COUNT(*) AS uses FROM entries WHERE deletedAt IS NULL GROUP BY productId")
    abstract fun observeProductUses(): Flow<List<ProductUse>>

    @Query(
        """
        SELECT logicalEpochDay AS epochDay, COUNT(*) AS doses FROM entries
        WHERE deletedAt IS NULL AND logicalEpochDay BETWEEN :fromDay AND :toDay
        GROUP BY logicalEpochDay
        """,
    )
    abstract fun observeDosesPerDay(fromDay: Long, toDay: Long): Flow<List<DayDoses>>

    @Query("SELECT COUNT(*) FROM entries WHERE deletedAt IS NULL")
    abstract suspend fun countLive(): Int

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY occurredAt")
    abstract suspend fun allLiveWithTags(): List<EntryWithTags>

    @Query("SELECT id FROM entries")
    abstract suspend fun allIds(): List<String>

    @Query("DELETE FROM entry_tags")
    abstract suspend fun deleteAllTags()

    @Query("DELETE FROM entries")
    abstract suspend fun deleteAll()

    @Query("UPDATE entries SET logicalEpochDay = :day WHERE id = :id")
    abstract suspend fun setLogicalDay(id: String, day: Long)
}

/** How many live entries carry a tag. */
data class TagUse(val tagKey: String, val uses: Int)

/** How many live entries a product has. */
data class ProductUse(val productId: String, val uses: Int)

/** How many live entries count toward a logical day. */
data class DayDoses(val epochDay: Long, val doses: Int)

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(tags: List<TagEntity>): List<Long>

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY sortOrder")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY sortOrder")
    suspend fun all(): List<TagEntity>

    @Query(
        """
        SELECT entry_tags.tagKey AS tagKey, COUNT(*) AS uses
        FROM entry_tags JOIN entries ON entries.id = entry_tags.entryId
        WHERE entries.deletedAt IS NULL AND entries.occurredAt >= :sinceMs
        GROUP BY entry_tags.tagKey
        """,
    )
    fun observeUses(sinceMs: Long): Flow<List<TagUse>>

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
