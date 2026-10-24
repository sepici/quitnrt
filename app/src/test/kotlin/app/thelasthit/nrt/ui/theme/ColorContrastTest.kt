package app.thelasthit.nrt.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The token pairs text and controls are drawn in, held to WCAG's contrast: 4.5:1 for text and 3:1
 * for a control's edge or mark. Beside Color.kt, so a change of token that drops a pair under its
 * line fails here, not on someone's phone.
 */
class ColorContrastTest {

    private val both = listOf("light" to LightColors, "dark" to DarkColors)

    private fun ratio(fg: Color, bg: Color): Double {
        val shown = if (fg.alpha < 1f) fg.compositeOver(bg) else fg
        val a = shown.luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private fun assertAtLeast(min: Double, what: String, fg: (LastHitColors) -> Color, bg: (LastHitColors) -> Color) {
        for ((mode, colors) in both) {
            val r = ratio(fg(colors), bg(colors))
            assertTrue("$what in $mode is %.2f:1, under %.1f:1".format(r, min), r >= min)
        }
    }

    @Test
    fun `dialog buttons are in the link colour, which reads on a dialog in both modes`() {
        // Material's own text button takes the primary colour: 3:1 on a dark dialog.
        assertTrue(ratio(DarkColors.primary, DarkColors.surface) < 4.5)
        assertAtLeast(4.5, "a dialog button", { it.link }, { it.surface })
    }

    @Test
    fun `the date picker's today reads in the text colour, inside a ring a control can be seen by`() {
        assertAtLeast(4.5, "today's number", { it.text }, { it.surface })
        assertAtLeast(3.0, "today's ring", { it.lineStrong }, { it.surface })
        assertAtLeast(4.5, "the picked day", { it.onChipSelected }, { it.chipSelected })
    }

    @Test
    fun `the second line on a primary button and a picked tile is in the full colour`() {
        // A tint of it, as the buttons had, falls under 4.5:1 on the dark-mode green.
        assertTrue(ratio(DarkColors.onPrimary.copy(alpha = 0.82f), DarkColors.primary) < 4.5)
        assertAtLeast(4.5, "a primary button's text", { it.onPrimary }, { it.primary })
        assertAtLeast(4.5, "a picked tile's text", { it.onChipSelected }, { it.chipSelected })
    }

    @Test
    fun `a day letter in its at-or-under circle reads`() {
        assertAtLeast(4.5, "a day letter on the green", { it.onSuccess }, { it.success })
    }

    @Test
    fun `body text, secondary text and links read on the page and on a sheet`() {
        for (bg in listOf<(LastHitColors) -> Color>({ it.bg }, { it.surface })) {
            assertAtLeast(4.5, "body text", { it.text }, bg)
            assertAtLeast(4.5, "secondary text", { it.text2 }, bg)
            assertAtLeast(4.5, "a link", { it.link }, bg)
        }
        assertAtLeast(4.5, "an error", { it.error }, { it.surface })
    }
}
