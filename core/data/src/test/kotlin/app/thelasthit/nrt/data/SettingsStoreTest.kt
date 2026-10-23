package app.thelasthit.nrt.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {

    private lateinit var data: TestData

    @Before
    fun setUp() {
        data = TestData()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun defaultsAreSensible() = runTest {
        val settings = data.settings.current()
        assertEquals(LocalTime.of(4, 0), settings.dayStart)
        assertNull(settings.country)
        assertFalse(settings.onboardingComplete)
    }

    @Test
    fun changesAreKept() = runTest {
        data.settings.setDayStart(LocalTime.of(3, 30))
        data.settings.setCountry("gb")
        data.settings.acceptDisclaimer(data.clock.now)
        data.settings.completeOnboarding()

        val settings = data.settings.current()
        assertEquals(LocalTime.of(3, 30), settings.dayStart)
        assertEquals("GB", settings.country)
        assertEquals(data.clock.now, settings.disclaimerAcceptedAt)
        assertTrue(settings.onboardingComplete)
    }
}
