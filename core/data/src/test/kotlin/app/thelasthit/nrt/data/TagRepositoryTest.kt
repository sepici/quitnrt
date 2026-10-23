package app.thelasthit.nrt.data

import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
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
}
