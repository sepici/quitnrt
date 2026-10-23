package app.thelasthit.nrt.ui.onboarding

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.ui.FULL_PAGE
import app.thelasthit.nrt.ui.LARGE_FONT
import app.thelasthit.nrt.ui.captureLooks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Every set-up step as the mockups show it, filled in with the mockups' choices. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FULL_PAGE)
class OnboardingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    private val chosen = OnboardingDraft(
        stoppedOn = LocalDate.parse("2026-09-07"),
        country = "GB",
        picked = listOf(GUM_2, GUM_4, QUICKMIST),
        demonName = "Gary",
    )

    private fun capture(name: String, step: OnboardingStep, ready: String) {
        val viewModel = OnboardingViewModel(app.graph, region = "GB", initial = chosen.copy(step = step))
        compose.captureLooks(name, ready = hasText(ready)) { OnboardingScreen(viewModel) }
    }

    @Test fun welcome() = capture("onboarding-1-welcome", OnboardingStep.WELCOME, "Accept and set up")

    @Test @Config(fontScale = LARGE_FONT)
    fun welcomeLarge() = capture("onboarding-1-welcome", OnboardingStep.WELCOME, "Accept and set up")

    @Test fun stopped() = capture("onboarding-2-stopped", OnboardingStep.STOPPED, "Next: your country")

    @Test fun country() = capture("onboarding-3-country", OnboardingStep.COUNTRY, "Next: your products")

    @Test fun products() = capture("onboarding-4-products", OnboardingStep.PRODUCTS, "3 picked")

    @Test @Config(fontScale = LARGE_FONT)
    fun productsLarge() = capture("onboarding-4-products", OnboardingStep.PRODUCTS, "3 picked")

    @Test fun demon() = capture("onboarding-5-demon", OnboardingStep.DEMON, "Next: when your day starts")

    @Test @Config(fontScale = LARGE_FONT)
    fun demonLarge() = capture("onboarding-5-demon", OnboardingStep.DEMON, "Next: when your day starts")

    @Test fun dayStart() = capture("onboarding-6-day-start", OnboardingStep.DAY_START, "Finish set-up")

    @Test fun done() = capture("onboarding-7-done", OnboardingStep.DONE, "Start logging")
}
