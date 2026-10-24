package app.thelasthit.nrt.ui.absorbed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The estimate shows on Today, History and the log sheet, and nowhere it could be taken for a
 * target: the plan, Insights, the game, reminders, the widget and tile, and agents all stay in
 * labelled mg (ROADMAP P9-1, PLAN §3). Nor does any repository read it, or a catalogue line's own
 * factor. The domain's own test checks its rules the same way.
 */
class AbsorbedReachTest {

    private fun sources(vararg candidates: String): File = candidates.map(::File).firstOrNull { it.isDirectory }
        ?: error("can't find ${candidates.first()} from ${File(".").absolutePath}")

    private val main = sources("src/main/kotlin/app/thelasthit/nrt", "app/src/main/kotlin/app/thelasthit/nrt")
    private val data = sources("../core/data/src/main/kotlin/app/thelasthit/nrt/data", "core/data/src/main/kotlin/app/thelasthit/nrt/data")

    private fun File.kotlin(): List<File> = walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun List<File>.paths(from: File) = map { it.relativeTo(from).invariantSeparatorsPath }.sorted()

    @Test
    fun `nothing that sets or judges a target reads the estimate`() {
        val kept = listOf("ui/plan", "ui/insights", "ui/game", "ui/lair", "ui/advice", "widget", "tile", "reminders", "agents", "quicklog", "link")
        val files = kept.flatMap { dir -> File(main, dir).kotlin() }
        assertTrue("only ${files.size} files found", files.size > 40)
        val reading = files.filter { MENTIONS.containsMatchIn(it.readText()) }
        assertEquals(emptyList<String>(), reading.paths(main))
    }

    @Test
    fun `only the absorbed package reads a factor or works out an estimate`() {
        val outside = main.kotlin().filter { it.parentFile.name != "absorbed" }
        val reading = outside.filter { READS.containsMatchIn(it.readText()) }
        assertEquals(emptyList<String>(), reading.paths(main))
    }

    @Test
    fun `no repository reads it, only the setting and its backup mention it`() {
        val files = data.kotlin()
        assertTrue("only ${files.size} files found", files.size > 10)
        assertEquals(listOf("repo/BackupRepository.kt", "settings/SettingsStore.kt"), files.filter { MENTIONS.containsMatchIn(it.readText()) }.paths(data))
        assertEquals(emptyList<String>(), files.filter { READS.containsMatchIn(it.readText()) }.paths(data))
    }

    @Test
    fun `the scans catch a rule that reads a line's own factor`() {
        val rule = "val share = graph.catalogue.find(id)?.absorption?.perMille ?: 1000"
        assertTrue(MENTIONS.containsMatchIn(rule))
        assertTrue(READS.containsMatchIn(rule))
        assertTrue(READS.containsMatchIn("AbsorbedEstimate.of(entry)"))
        assertTrue(READS.containsMatchIn("Absorption.byForm"))
    }

    @Test
    fun `only the screens that show it read it`() {
        val reading = main.kotlin()
            .filter { "absorbedFigures()" in it.readText() }
            .paths(main)
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

    private companion object {
        /** "absorbed", "absorption", "Absorption" and the rest. */
        val MENTIONS = Regex("absor", RegexOption.IGNORE_CASE)

        /** A catalogue line's own factor, the factors themselves, or the estimate worked out. */
        val READS = Regex("[.]absorption(?![A-Za-z])|AbsorptionFactor|AbsorbedEstimate|Absorption[.]")
    }
}
