package app.thelasthit.nrt.domain.absorbed

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

/** Every factor is sourced, and nothing but the screens' estimate ever reads one. */
class AbsorptionTest {

    @Test
    fun `every form but other has a sourced factor`() {
        ProductForm.entries.filter { it != ProductForm.OTHER }.forEach { form ->
            val factor = Absorption.byForm[form]
            assertNotNull(factor, "$form has no factor")
            factor!!
            assertTrue(factor.sources.isNotEmpty(), "$form has no source")
            assertTrue(factor.basis.length > 40, "$form doesn't say how its figure was reached")
        }
        assertNull(Absorption.byForm[ProductForm.OTHER])
    }

    @Test
    fun `every product in the catalogue gets an estimate`() {
        val missing = Catalogue.bundled().products.filter { AbsorbedEstimate.factorFor(it.form, it.absorption) == null }
        assertEquals(emptyList<String>(), missing.map { it.id })
    }

    @Test
    fun `each factor sits in its range`() {
        Absorption.byForm.forEach { (form, factor) ->
            val low = factor.low ?: return@forEach
            val high = requireNotNull(factor.high)
            assertTrue(factor.factor.perMille in low.perMille..high.perMille, "$form: ${factor.factor} outside $low to $high")
        }
    }

    @Test
    fun `the figures the research settled on`() {
        fun percent(form: ProductForm) = Absorption.byForm.getValue(form).factor.percent
        assertEquals(50, percent(ProductForm.GUM))
        assertEquals(60, percent(ProductForm.LOZENGE))
        assertEquals(60, percent(ProductForm.MOUTH_SPRAY))
        assertEquals(53, percent(ProductForm.NASAL_SPRAY))
        assertEquals(20, percent(ProductForm.INHALATOR))
        assertEquals(50, percent(ProductForm.MICROTAB))
        assertEquals(48, percent(ProductForm.ORAL_FILM))
        assertEquals(60, percent(ProductForm.POUCH))
        assertEquals(80, percent(ProductForm.PATCH_24H))
    }

    @Test
    fun `a strip shows what the 2 mg lozenge it's bioequivalent to does`() {
        // The NiQuitin Strips leaflet, section 5.1: the 2.5 mg strip is bioequivalent to the 2 mg lozenge.
        val strip = AbsorbedEstimate.of(Dose(2500), ProductForm.ORAL_FILM)
        assertEquals(AbsorbedEstimate.of(Dose.mg(2), ProductForm.LOZENGE), strip)
        assertEquals(Dose(1200), strip)
    }

    @Test
    fun `every catalogue line with its own factor has a sourced entry that matches it`() {
        val products = Catalogue.bundled().products
        val own = products.filter { it.absorption != null }
        assertTrue(own.isNotEmpty(), "no line has its own factor")
        own.forEach { product ->
            val entry = Absorption.byLine[product.lineId]
            assertNotNull(entry, "${product.lineId} sets absorptionPerMille with no sourced entry in Absorption.byLine")
            assertEquals(entry!!.factor, product.absorption, product.id)
        }
        Absorption.byLine.forEach { (lineId, entry) ->
            val inLine = products.filter { it.lineId == lineId }
            assertTrue(inLine.isNotEmpty(), "$lineId isn't in the catalogue")
            inLine.forEach { assertEquals(entry.factor, it.absorption, "${it.id} doesn't carry its line's factor") }
            assertTrue(entry.sources.isNotEmpty() && entry.basis.length > 40, "$lineId isn't sourced")
            entry.low?.let { low -> assertTrue(entry.factor.perMille in low.perMille..entry.high!!.perMille, lineId) }
        }
    }

    @Test
    fun `a 1 mg bitartrate lozenge shows close to the 2 mg gum it's bioequivalent to`() {
        val catalogue = Catalogue.bundled()
        fun estimate(id: String): Dose {
            val product = requireNotNull(catalogue.find(id)) { id }
            return requireNotNull(AbsorbedEstimate.of(product.strength, product.form, product.absorption))
        }
        assertEquals(Dose(800), estimate("nicotinell-lozenge-uk-1mg"))
        assertEquals(Dose(1600), estimate("nicotinell-lozenge-uk-2mg"))
        assertEquals(Dose(800), estimate("habitrol-lozenge-nz-1mg"))
        // Dautzenberg 2007: within a fifth of 2 mg gum's 1 mg, where the polacrilex lozenge's 60% gave 0.6 mg.
        val gum = estimate("nicorette-gum-uk-2mg")
        assertTrue(estimate("nicotinell-lozenge-uk-1mg").micrograms * 5 >= gum.micrograms * 4)
        // A polacrilex lozenge keeps its form's figure.
        assertEquals(Dose(1200), estimate("niquitin-lozenges-uk-2mg"))
    }

    @Test
    fun `forms that behave alike share a figure`() {
        assertEquals(Absorption.byForm[ProductForm.LOZENGE]?.factor, Absorption.byForm[ProductForm.MINI_LOZENGE]?.factor)
        assertEquals(Absorption.byForm[ProductForm.PATCH_16H], Absorption.byForm[ProductForm.PATCH_24H])
    }

    @Test
    fun `every source says who, what, where and when it was read`() {
        assertTrue(Absorption.sources.size >= 10, "only ${Absorption.sources.size} sources")
        Absorption.sources.forEach { source ->
            assertTrue(source.publisher.isNotBlank() && source.title.isNotBlank() && source.short.isNotBlank(), source.toString())
            if (source.kind == SourceKind.STUDY) assertNotNull(source.year, "${source.title} has no year")
            assertTrue(source.url.startsWith("https://"), "${source.title}: ${source.url}")
            assertTrue(source.read <= LocalDate.of(2026, 10, 24) && source.read >= LocalDate.of(2026, 10, 1), "${source.title} read ${source.read}")
            source.year?.let { assertTrue(it in 1980..source.read.year, "${source.title}: $it") }
        }
        assertEquals(Absorption.sources.size, Absorption.sources.map { it.url }.toSet().size, "a source listed twice")
        assertEquals(LocalDate.of(2026, 10, 24), Absorption.checked)
    }

    @Test
    fun `the source lines on screen follow the copy rules`() {
        val banned = javaClass.getResourceAsStream("/copy-lint/banned.txt")?.bufferedReader()?.use { it.readLines() }
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?: error("copy-lint/banned.txt isn't on the test classpath")
        val shown = Absorption.sources.flatMap { listOf(it.short, it.publisher, it.title) }
        shown.forEach { text ->
            assertTrue(Char(0x2014) !in text && Char(0x2013) !in text, "dash in $text")
            banned.forEach { phrase ->
                assertTrue(!Regex("\\b" + Regex.escape(phrase) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text), "\"$phrase\" in $text")
            }
        }
    }

    /**
     * The estimate is only for showing (PLAN §3: "Goals never use them"). The rules for guides,
     * allowances, results, achievements and Insights live outside this package, and none of them
     * may read it, or a catalogue line's own factor. Only the catalogue (which carries a line's
     * factor as data) and the backup (which carries the setting) may mention absorption at all.
     */
    @Test
    fun `no guide, allowance, result or achievement rule reads the estimate`() {
        val main = File("src/main/kotlin/app/thelasthit/nrt/domain")
        assertTrue(main.isDirectory, "run from the module: ${main.absolutePath}")
        val outside = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "absorbed" }
            .toList()
        assertTrue(outside.size > 50, "only ${outside.size} files found")
        fun List<File>.paths() = map { it.relativeTo(main).invariantSeparatorsPath }.sorted()

        val mentions = outside.filter { MENTIONS.containsMatchIn(it.readText()) }
        assertEquals(listOf("backup/Backup.kt", "catalogue/Catalogue.kt", "catalogue/CatalogueFile.kt"), mentions.paths())

        val usesEstimate = outside.filter { "AbsorbedEstimate" in it.readText() || "Absorption." in it.readText() }
        assertEquals(emptyList<String>(), usesEstimate.paths())

        val readsOwnFactor = outside.filter { READS_FACTOR.containsMatchIn(it.readText()) }
        assertEquals(listOf("catalogue/Catalogue.kt"), readsOwnFactor.paths())
    }

    @Test
    fun `the scan catches a rule that reads a line's own factor`() {
        val rule = "val share = catalogue.find(id)?.absorption?.perMille ?: 1000"
        assertTrue(MENTIONS.containsMatchIn(rule))
        assertTrue(READS_FACTOR.containsMatchIn(rule))
        assertTrue(MENTIONS.containsMatchIn("val estimate = AbsorbedEstimate.of(entry)"))
        assertTrue(MENTIONS.containsMatchIn("import app.thelasthit.nrt.domain.absorbed.AbsorptionFactor"))
    }

    private companion object {
        /** "absorbed", "absorption", "Absorption", "absorptionPerMille" and the rest. */
        val MENTIONS = Regex("absor", RegexOption.IGNORE_CASE)
        val READS_FACTOR = Regex("""\.absorption\b|AbsorptionFactor""")
    }
}
