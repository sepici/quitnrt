package app.thelasthit.nrt.ui.lair

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.AppShell
import app.thelasthit.nrt.ui.PHONE
import app.thelasthit.nrt.ui.game.ChipPlayer
import app.thelasthit.nrt.ui.settings.SettingsLinks
import app.thelasthit.nrt.ui.settings.SettingsScreen
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The game layer outside the lair: the sound switch, the Lair tab's badge and silent mode. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class LairSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private fun soundOn() = runBlocking { app.graph.settings.settings.first().soundOn }

    @Test
    fun `the sound switch in Settings is on by default, and turns the lair's sound off and on`() {
        app.setUp()
        compose.setContent {
            LastHitTheme {
                SettingsScreen(
                    snackbars = remember { SnackbarHostState() },
                    onBack = {},
                    links = SettingsLinks(products = {}, quickLog = {}, tags = {}, dayStart = {}, about = {}),
                )
            }
        }

        val sound = hasText("Sound")
        compose.waitUntilExactlyOneExists(sound and isOn(), timeoutMillis = 5_000)
        compose.onNode(hasText("8-bit sounds for hits and the defeat. Quiet while the phone is on silent.")).assertExists()
        compose.onNode(sound).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(sound and isOff(), timeoutMillis = 5_000)
        assertFalse(soundOn())

        compose.onNode(sound).performClick()
        compose.waitUntilExactlyOneExists(sound and isOn(), timeoutMillis = 5_000)
        assertTrue(soundOn())
    }

    @Test
    fun `the Lair tab shows the hits ready, and loses the badge once they're spent`() {
        app.battleAsMockup()
        compose.setContent { LastHitTheme { AppShell() } }

        val badge = hasContentDescription("1 hit ready", substring = true)
        compose.waitUntilExactlyOneExists(badge, timeoutMillis = 5_000)
        runBlocking { app.graph.game.spendHit() }
        compose.waitUntilDoesNotExist(badge, timeoutMillis = 5_000)
    }

    @Test
    fun `the Lair tab counts every hit ready`() {
        app.battleWithCombo()
        compose.setContent { LastHitTheme { AppShell() } }

        compose.waitUntilExactlyOneExists(hasContentDescription("5 hits ready", substring = true), timeoutMillis = 5_000)
    }

    @Test
    fun `before a plan starts there's no badge`() {
        app.setUp()
        compose.setContent { LastHitTheme { AppShell() } }

        compose.waitUntilAtLeastOneExists(hasText("Lair"), timeoutMillis = 5_000)
        compose.onNode(hasContentDescription("ready", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the game stays quiet while the phone is on silent or vibrate`() {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val player = ChipPlayer(app)
        try {
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            assertTrue(player.phoneAllowsSound)
            audio.setRingerMode(AudioManager.RINGER_MODE_SILENT)
            assertFalse(player.phoneAllowsSound)
            audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
            assertFalse(player.phoneAllowsSound)
        } finally {
            player.close()
        }
    }
}
