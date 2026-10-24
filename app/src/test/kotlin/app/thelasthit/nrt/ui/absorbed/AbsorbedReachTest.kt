package app.thelasthit.nrt.ui.absorbed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The estimate shows on Today's hero, History and the log sheet, and nowhere it could be taken
 * for a target: the plan, Insights, the game, reminders, the widget and tile, and agents all stay
 * in labelled mg (ROADMAP P9-1, PLAN §3). The domain's own test checks its rules the same way.
 */
class AbsorbedReachTest {

    private val main: File = listOf(File("src/main/kotlin/app/thelasthit/nrt"), File("app/src/main/kotlin/app/thelasthit/nrt"))
        .firstOrNull { it.isDirectory } ?: error("can't find the app's sources from ${File(".").absolutePath}")

    @Test
    fun `nothing that sets or judges a target reads the estimate`() {
        val kept = listOf("ui/plan", "ui/insights", "ui/game", "ui/lair", "ui/advice", "widget", "tile", "reminders", "agents", "quicklog", "link")
        val files = kept.flatMap { dir -> File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue("only ${files.size} files found", files.size > 40)
        val reading = files.filter { Regex("absorb", RegexOption.IGNORE_CASE).containsMatchIn(it.readText()) }
        assertEquals(emptyList<String>(), reading.map { it.relativeTo(main).invariantSeparatorsPath })
    }

    @Test
    fun `only the screens that show it read it`() {
        val reading = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "absorbedFigures()" in it.readText() }
            .map { it.relativeTo(main).invariantSeparatorsPath }
            .sorted()
            .toList()
        assertEquals(
            listOf(
                "ui/absorbed/AbsorbedFigures.kt",
                "ui/history/HistoryViewModel.kt",
                "ui/log/LogSheetViewModel.kt",
                "ui/today/TodayViewModel.kt",
            ),
            reading,
        )
    }
}
