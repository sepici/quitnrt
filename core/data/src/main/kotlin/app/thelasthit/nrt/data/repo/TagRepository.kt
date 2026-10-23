package app.thelasthit.nrt.data.repo

import app.thelasthit.nrt.data.db.TagDao
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TagRepository(private val dao: TagDao) {

    val all: Flow<List<TagEntity>> = dao.observeAll()

    /** Adds any built-in tags that aren't stored yet. Safe to call on every start. */
    suspend fun ensureBuiltIns() {
        dao.insertIgnoringExisting(
            BuiltInTags.all.mapIndexed { index, tag ->
                TagEntity(tag.key, tag.group, customName = null, isBuiltIn = true, isHidden = false, sortOrder = index)
            },
        )
    }

    suspend fun addCustom(name: String, group: TagGroup): TagEntity {
        require(name.isNotBlank()) { "A tag needs a name" }
        val tag = TagEntity(
            key = "custom_${UUID.randomUUID().toString().replace("-", "")}",
            group = group,
            customName = name.trim(),
            isBuiltIn = false,
            isHidden = false,
            sortOrder = (dao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        dao.upsert(tag)
        return tag
    }

    suspend fun rename(tag: TagEntity, name: String?) = dao.upsert(tag.copy(customName = name?.trim()?.ifBlank { null }))

    suspend fun setHidden(tag: TagEntity, hidden: Boolean) = dao.upsert(tag.copy(isHidden = hidden))
}
