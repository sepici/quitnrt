package app.thelasthit.nrt.data

import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.data.repo.LogResult
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.TagOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagRepositoryTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun builtInTagsAreSeededOnceAndKeepUserChanges() = runTest {
        data.tags.ensureBuiltIns()
        val bored = data.tags.all.first().first { it.key == "bored" }
        data.tags.rename(bored, "Nothing to do")
        data.tags.ensureBuiltIns()

        val tags = data.tags.all.first()
        assertEquals(BuiltInTags.all.size, tags.size)
        assertEquals("Nothing to do", tags.first { it.key == "bored" }.customName)
    }

    @Test
    fun customTagsGoAtTheEnd() = runTest {
        data.tags.ensureBuiltIns()
        val custom = data.tags.addCustom("Gaming", TagGroup.ROUTINE)
        assertTrue(custom.key.startsWith("custom_"))
        assertEquals(BuiltInTags.all.size, custom.sortOrder)
        assertEquals(custom, data.tags.all.first().last())
    }

    @Test
    fun usesCountRecentLiveEntriesOnly() = runTest {
        data.tags.ensureBuiltIns()
        val gum = data.products.addFromCatalogue("nicorette-gum-uk-2mg")
        val now = data.clock.instant()
        suspend fun log(minutesAgo: Long, vararg tags: String) =
            data.entries.log(LogRequest(gum.id, at = now.minus(Duration.ofMinutes(minutesAgo)), tagKeys = tags.toSet())) as LogResult.Saved

        log(10, "after_meal", "bored")
        log(200, "after_meal")
        log(Duration.ofDays(40).toMinutes(), "bored") // older than the window
        val deleted = log(400, "happy")
        data.entries.delete(deleted.entry.id)

        val uses = data.tags.observeUses(now.minus(TagOrder.USE_WINDOW)).first()
        assertEquals(mapOf("after_meal" to 2, "bored" to 1), uses)
    }

    @Test
    fun allUsesCountEveryLiveEntryHoweverOld() = runTest {
        data.tags.ensureBuiltIns()
        val gum = data.products.addFromCatalogue("nicorette-gum-uk-2mg")
        val now = data.clock.instant()
        suspend fun log(daysAgo: Long, vararg tags: String) =
            data.entries.log(LogRequest(gum.id, at = now.minus(Duration.ofDays(daysAgo)), tagKeys = tags.toSet())) as LogResult.Saved

        log(1, "after_meal", "bored")
        log(400, "bored")
        log(25_000, "bored") // in 1958, before the epoch
        val deleted = log(3, "happy")
        data.entries.delete(deleted.entry.id)

        assertEquals(mapOf("after_meal" to 1, "bored" to 3), data.tags.observeAllUses().first())
    }

    @Test
    fun editingRenamesMovesAndHides() = runTest {
        data.tags.ensureBuiltIns()
        val coffee = data.tags.all.first().first { it.key == "coffee_tea" }
        data.tags.edit(coffee, "Morning coffee", TagGroup.HABIT, hidden = true)
        val edited = data.tags.all.first().first { it.key == "coffee_tea" }
        assertEquals("Morning coffee", edited.customName)
        assertEquals(TagGroup.HABIT, edited.group)
        assertTrue(edited.isHidden)

        data.tags.edit(edited, "", TagGroup.ROUTINE, hidden = false)
        assertEquals(null, data.tags.all.first().first { it.key == "coffee_tea" }.customName)
    }
}
