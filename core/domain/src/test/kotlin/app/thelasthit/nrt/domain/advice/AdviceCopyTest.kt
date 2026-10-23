package app.thelasthit.nrt.domain.advice

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The advice is app text, so it follows docs/STYLE.md. checkCopy only reads strings.xml, so these
 * tests apply its rules, and a few more of STYLE's, to the bundled strategies.
 */
class AdviceCopyTest {

    private val library = AdviceLibrary.bundled()

    /** Every piece of a strategy that shows on screen, labelled for failure messages. */
    private val texts: List<Pair<String, String>> = library.strategies.flatMap { strategy ->
        listOf("${strategy.key} title" to strategy.title, "${strategy.key} body" to strategy.body) +
            strategy.steps.mapIndexed { index, step -> "${strategy.key} step ${index + 1}" to step }
    } + library.quitlines.map { "${it.country} ${it.region ?: ""} quitline" to it.name }

    private fun problems(check: (String) -> String?): List<String> =
        texts.mapNotNull { (where, text) -> check(text)?.let { "$where: $it" } }

    private fun assertNone(problems: List<String>) =
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))

    @Test
    fun `no em or en dashes`() {
        assertNone(
            problems { text ->
                when {
                    Char(0x2014) in text -> "em dash"
                    Char(0x2013) in text -> "en dash"
                    else -> null
                }
            },
        )
    }

    @Test
    fun `none of the banned phrases`() {
        val list = javaClass.getResourceAsStream("/copy-lint/banned.txt")?.bufferedReader()?.use { it.readLines() }
            ?: error("copy-lint/banned.txt isn't on the test classpath")
        val banned = list.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(banned.size > 20, "only ${banned.size} banned phrases")
        val patterns = banned.map { it to Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE) }
        assertNone(problems { text -> patterns.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.let { "\"${it.first}\"" } })
    }

    @Test
    fun `titles are in sentence case, with no full stop`() {
        // Acronyms (NRT, GP) are fine anywhere. So is the 4 Ds, which names the letters.
        val allowed = setOf("Ds")
        val wrong = library.strategies.mapNotNull { strategy ->
            val words = strategy.title.split(' ', Char(0xA0))
            val first = words.first().first()
            val capitalised = words.drop(1).filter { word ->
                word.first().isUpperCase() && word.any { it.isLowerCase() } && word !in allowed
            }
            when {
                !(first.isUpperCase() || first.isDigit()) -> "${strategy.key}: starts with \"$first\""
                capitalised.isNotEmpty() -> "${strategy.key}: $capitalised"
                strategy.title.endsWith('.') -> "${strategy.key}: ends with a full stop"
                else -> null
            }
        }
        assertNone(wrong)
    }

    @Test
    fun `sentences start with a capital or a number and end with a full stop`() {
        val sentences = library.strategies.flatMap { s ->
            listOf("${s.key} body" to s.body) + s.steps.mapIndexed { i, step -> "${s.key} step ${i + 1}" to step }
        }
        val wrong = sentences.mapNotNull { (where, text) ->
            val first = text.first()
            when {
                !(first.isUpperCase() || first.isDigit()) -> "$where starts with \"$first\""
                !(text.endsWith('.') || text.endsWith(".${Char(0x201D)}")) -> "$where doesn't end with a full stop"
                else -> null
            }
        }
        assertNone(wrong)
    }

    @Test
    fun `no exclamation marks or questions`() {
        assertNone(problems { text -> if ('!' in text || '?' in text) "has ! or ?" else null })
    }

    @Test
    fun `a number and its unit are held together by a no-break space`() {
        val loose = Regex("\\d (mg|minutes?|seconds?|hours?|days?|weeks?)\\b")
        assertNone(problems { text -> loose.find(text)?.let { "\"${it.value}\" needs a no-break space" } })
    }

    @Test
    fun `the file keeps its no-break spaces escaped, so they show in review`() {
        val raw = AdviceLibrary::class.java.getResourceAsStream("/advice/strategies.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(Char(0xA0) !in raw, "a raw no-break space in strategies.json: write it as \\u00a0")
        assertTrue("\\u00a0" in raw)
    }

    @Test
    fun `British spellings`() {
        val american = listOf(
            "color", "colors", "favorite", "favorites", "center", "realize", "organize", "behavior", "toward",
            "program", "programs", "apologize", "gray", "fiber", "analyze", "flavor", "flavors", "honor", "humor",
            "neighbor", "neighbors", "mom", "pacifier", "candy",
        ).map { Regex("\\b$it\\b", RegexOption.IGNORE_CASE) }
        assertNone(problems { text -> american.firstOrNull { it.containsMatchIn(text) }?.let { "\"${it.pattern}\"" } })
    }

    @Test
    fun `nothing suggests taking more nicotine`() {
        val more = listOf(
            "more nicotine", "take more", "use more", "have more", "extra dose", "extra piece", "another dose",
            "another piece", "higher dose", "higher strength", "stronger dose", "stronger product", "increase your dose",
            "add a dose", "double up", "top up",
        ).map { Regex("\\b$it\\b", RegexOption.IGNORE_CASE) }
        assertNone(problems { text -> more.firstOrNull { it.containsMatchIn(text) }?.let { "\"${it.pattern}\"" } })
    }

    @Test
    fun `each strategy fits a card`() {
        val wrong = library.strategies.mapNotNull { s ->
            when {
                s.title.length > 40 -> "${s.key}: title is ${s.title.length} characters"
                s.body.length > 200 -> "${s.key}: body is ${s.body.length} characters"
                s.steps.size > 5 -> "${s.key}: ${s.steps.size} steps"
                s.steps.any { it.length > 160 } -> "${s.key}: a step over 160 characters"
                else -> null
            }
        }
        assertNone(wrong)
    }
}
