package app.thelasthit.nrt.domain.absorbed

import app.thelasthit.nrt.domain.catalogue.Catalogue
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
        assertEquals(50, percent(ProductForm.ORAL_FILM))
        assertEquals(60, percent(ProductForm.POUCH))
        assertEquals(80, percent(ProductForm.PATCH_24H))
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
            assertTrue(source.publisher.isNotBlank() && source.title.isNotBlank(), source.toString())
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
        val shown = Absorption.sources.flatMap { listOf(it.publisher, it.title) }
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
     * may read it. The catalogue only carries a line's own factor as data.
     */
    @Test
    fun `no guide, allowance, result or achievement rule reads the estimate`() {
        val main = File("src/main/kotlin/app/thelasthit/nrt/domain")
        assertTrue(main.isDirectory, "run from the module: ${main.absolutePath}")
        val outside = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "absorbed" }
            .toList()
        assertTrue(outside.size > 50, "only ${outside.size} files found")

        val usesEstimate = outside.filter { "AbsorbedEstimate" in it.readText() || "Absorption." in it.readText() }
        assertEquals(emptyList<String>(), usesEstimate.map { it.name })

        val usesPackage = outside.filter { "domain.absorbed" in it.readText() }.map { it.relativeTo(main).invariantSeparatorsPath }
        assertEquals(listOf("catalogue/Catalogue.kt"), usesPackage)

        val rules = listOf("programme", "game", "insights", "reminders", "agent", "log")
        val mentions = outside.filter { file -> file.parentFile.name in rules && Regex("absorb", RegexOption.IGNORE_CASE).containsMatchIn(file.readText()) }
        assertEquals(emptyList<String>(), mentions.map { it.name })
    }
}
