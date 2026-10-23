package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.catalogue.CatalogueProduct
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.DoseMath
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.UserProduct
import java.util.Locale

/**
 * Works out which of the user's products a plain-language description means, and how many: the
 * matcher behind deep links and AppFunctions. "2mg nicotine gum", "two 4 mg gums", "nicorette 4",
 * "21mg patch", "two sprays of QuickMist" and "inhalator session" each name one product and a count.
 *
 * How a description is read:
 * - **Product IDs.** An exact product ID (or catalogue ID) is that product.
 * - **Numbers.** Digits, number words up to twenty-nine, "a couple", "once" and "twice". A number
 *   before "mg" is the strength, with or without a space ("2mg", "2 mg"), and one before "hour" is
 *   a patch's hours. "x2" and "2x" are how many. Any other number is read from the words around it:
 *   - Before a unit word or a plural it's how many, wherever it sits: "2 gums", "nicorette 2 gums",
 *     "QuickMist, 2 sprays", "inhalator 3 cartridges".
 *   - Two numbers together are how many, then the strength: "2 4 gums".
 *   - At the start it's how many ("two gum", "2 nicorette"), except that a digit above 1, or a
 *     number word above ten, is the strength when the form word is singular: "21 patch", "the 4
 *     gum", "twenty-one patch". Nobody means 21 patches by "21 patch".
 *   - After a name it's the strength ("nicorette 4"), but the word "one" there stands for the
 *     product, as in "the gum one".
 *   Halves and quarters aren't read, so they match nothing.
 * - **Form words** and their synonyms: gum, lozenge, mini, patch, spray, mouth or oral, nasal or
 *   nose, inhalator or inhaler, microtab or tablet, strip or film, and pouch. "Puff" is a mouth
 *   spray, or an inhalator puff when the inhalator is logged in puffs. A lozenge can be a mini
 *   lozenge, and a puff a nasal spray, but only when nothing fits better.
 * - **Inhalator units.** "Session" and "cartridge" are converted to the unit the inhalator is logged
 *   in, so a cartridge of one logged by session is 8 sessions. A session can't be logged on an
 *   inhalator logged in whole cartridges.
 * - **Names.** Every other word has to be in the product's name, or, for a catalogue product, its
 *   brand, line, flavours or catalogue ID. Two words that make a name together count as that name
 *   ("quick mist"). Small words like "a", "of", "nicotine" and "log" are skipped.
 *
 * Every word has to fit, so "boots gum" matches nothing for someone who only has Nicorette gum.
 * When several products fit equally well the phrase is ambiguous, and the candidates come back in
 * quick-log order, then by name, so an agent can ask which one was meant.
 *
 * Hidden products are left out, as they are on the log sheet, but [hidden] finds one by its ID.
 * [catalogue] adds each catalogue product's brand, line and flavours to its words, such as "Rapid
 * Relief" for Nicotinell spray and "Icy White" for Nicorette gum.
 */
class ProductMatcher(products: List<UserProduct>, catalogue: Catalogue? = null) {

    private val profiles = products.filterNot { it.isHidden }.map { product ->
        Profile(product, product.catalogueId?.let { id -> catalogue?.find(id) })
    }

    private val hiddenProducts = products.filter { it.isHidden }

    private val vocabulary: Set<String> = profiles.flatMapTo(HashSet()) { it.words }

    /**
     * The hidden product [id] is the exact ID (or catalogue ID) of, or null. A hidden product
     * matches nothing, but a link or a pinned shortcut made before it was hidden still names it,
     * and that deserves a better answer than "no product matches".
     */
    fun hidden(id: String): UserProduct? {
        val text = id.trim()
        return hiddenProducts.firstOrNull { it.id.equals(text, ignoreCase = true) || it.catalogueId.equals(text, ignoreCase = true) }
    }

    fun match(description: String): ProductMatch {
        val id = description.trim()
        profiles.firstOrNull { it.hasId(id) }?.let { return ProductMatch.Found(ProductCandidate(it.product), count = null) }

        val phrase = Phrase.parse(description, vocabulary) ?: return ProductMatch.Unknown
        val fits = profiles.mapNotNull { it.fit(phrase) }
        if (fits.isEmpty()) return ProductMatch.Unknown
        val best = fits.maxOf { it.score }
        val top = fits.filter { it.score == best }.map { it.candidate }.sortedWith(RANK)
        return if (top.size == 1) {
            ProductMatch.Found(top.single(), phrase.count)
        } else {
            ProductMatch.Ambiguous(top, phrase.count)
        }
    }

    private class Fit(val candidate: ProductCandidate, val score: Int)

    /** A product with the words it answers to. */
    private class Profile(val product: UserProduct, item: CatalogueProduct?) {

        val words: Set<String> = buildSet {
            val sources = listOfNotNull(product.name, product.shortName, product.catalogueId) +
                listOfNotNull(item?.brand, item?.line) + item?.flavours.orEmpty()
            sources.forEach { source -> addAll(Phrase.nameWords(source)) }
        }

        fun hasId(id: String): Boolean =
            product.id.equals(id, ignoreCase = true) || product.catalogueId.equals(id, ignoreCase = true)

        /** The strength on the pack. For the inhalator that's the cartridge, not one session. */
        private val packStrength: Dose = product.cartridge ?: product.perUnit

        fun fit(phrase: Phrase): Fit? {
            if (phrase.strength != null && phrase.strength != packStrength) return null
            if (phrase.hours != null && phrase.hours != product.form.ratedHours) return null
            if (phrase.names.any { it !in words }) return null
            var score = 0
            for (keyword in phrase.keywords) {
                val forms = keyword.forms ?: continue
                when {
                    product.form in forms || keyword.word in words -> score++
                    product.form in keyword.fallback -> Unit
                    else -> return null
                }
            }
            val asked = phrase.units
            val unitsPerCount = if (asked == null) 1 else asked.firstNotNullOfOrNull { unitsPer(it) } ?: return null
            return Fit(ProductCandidate(product, unitsPerCount), score)
        }

        /** How many of this product's units one [asked] unit is, or null when it can't be logged that way. */
        private fun unitsPer(asked: DoseUnit): Int? {
            if (asked == product.unit) return 1
            if (product.form != ProductForm.INHALATOR) return null
            val have = perCartridge(product.unit) ?: return null
            val want = perCartridge(asked) ?: return null
            return if (have % want == 0) have / want else null
        }

        private fun perCartridge(unit: DoseUnit): Int? = when (unit) {
            DoseUnit.CARTRIDGE -> 1
            DoseUnit.SESSION -> DoseMath.SESSIONS_PER_CARTRIDGE
            DoseUnit.PUFF -> product.puffsPerCartridge?.takeIf { it > 0 }
            else -> null
        }
    }

    private companion object {
        val RANK: Comparator<ProductCandidate> = compareBy<ProductCandidate>(
            { it.product.quickLogPosition ?: Int.MAX_VALUE },
            { it.product.name.lowercase(Locale.ROOT) },
            { it.product.id },
        )
    }
}

/**
 * A product a description could mean. [unitsPerCount] is how many of the product's own units one
 * of what was asked for is: 8 when a cartridge is asked for and the inhalator is logged by session,
 * otherwise 1.
 */
data class ProductCandidate(val product: UserProduct, val unitsPerCount: Int = 1) {
    /** What to log, in the product's own unit, for [count] of what was asked for. */
    fun quantityFor(count: Int): Int = count * unitsPerCount
}

sealed interface ProductMatch {
    /** The number the description gave ("two 4 mg gums" gives 2), or null when it gave none. */
    val count: Int?

    data class Found(val candidate: ProductCandidate, override val count: Int?) : ProductMatch {
        val product: UserProduct get() = candidate.product

        /** What to log, in the product's own unit. One when the description gave no number. */
        val quantity: Int get() = candidate.quantityFor(count ?: 1)
    }

    /** Several products fit equally well, most likely first. */
    data class Ambiguous(val candidates: List<ProductCandidate>, override val count: Int?) : ProductMatch

    /** No product fits every word, or the description couldn't be read. */
    data object Unknown : ProductMatch {
        override val count: Int? get() = null
    }
}

/**
 * A form word from a description. A product fits it when its form is in [forms] (or the word is in
 * its name), and, less well, when its form is in [fallback]. Null [forms] means any form. [units]
 * are what the word counts in, when it names a unit.
 */
internal class Keyword(
    val word: String,
    val forms: Set<ProductForm>?,
    val fallback: Set<ProductForm> = emptySet(),
    val units: Set<DoseUnit>? = null,
)

/** A description taken apart: how many, the strength and hours, form words, and name words. */
internal class Phrase(
    val count: Int?,
    val strength: Dose?,
    val hours: Int?,
    val keywords: List<Keyword>,
    /** The units every unit word in the phrase allows, or null when it names none. */
    val units: Set<DoseUnit>?,
    val names: List<String>,
) {
    /** A word of a description, stemmed. [plural] when stemming took a plural off; [spelled] for a number written as a word. */
    private class Token(val word: String, val plural: Boolean = false, val spelled: Boolean = false)

    /** A number with no "mg", "hour" or "x" beside it, at [index] in the description's tokens. */
    private class Bare(val index: Int, val token: Token, val leading: Boolean)

    /**
     * What a bare number is taken for. A [sure] reading is settled by the words around it; one that
     * isn't is a guess, and gives way to the other reading when a surer number has taken its place.
     */
    private class Reading(val number: String, val isCount: Boolean, val sure: Boolean)

    companion object {

        /** A product name's own words, without its numbers: "Nicorette gum 2 mg" gives nicorette and gum. */
        fun nameWords(text: String): List<String> =
            Words.split(text).filterNot { Words.hasDigit(it) }.map(Words::stem).filterNot { it in MG || it in HOURS }

        /** Null when the description can't be read, or names nothing but a number. */
        fun parse(text: String, vocabulary: Set<String>): Phrase? {
            if (FRACTION.containsMatchIn(text)) return null
            val words = tokens(Words.split(text), vocabulary)

            var count: Int? = null
            var strength: Dose? = null
            var hours: Int? = null
            val bare = mutableListOf<Bare>()
            val keywords = mutableListOf<Keyword>()
            val names = mutableListOf<String>()
            var described = false

            var i = 0
            while (i < words.size) {
                val token = words[i]
                val word = token.word
                val next = words.getOrNull(i + 1)?.word
                when {
                    word in REFUSED -> return null
                    Words.isNumber(word) -> when {
                        next in MG -> {
                            if (strength != null) return null
                            strength = Dose.parseMg(word) ?: return null
                            described = true
                            i++
                        }
                        next in HOURS -> {
                            if (hours != null) return null
                            hours = word.toIntOrNull() ?: return null
                            described = true
                            i++
                        }
                        next in TIMES -> {
                            if (count != null) return null
                            count = countOf(word) ?: return null
                            i++
                        }
                        else -> bare += Bare(i, token, leading = !described)
                    }
                    word in MULTIPLIERS -> {
                        if (count != null) return null
                        count = MULTIPLIERS.getValue(word)
                    }
                    word in TIMES && next != null && Words.isNumber(next) && !isUnit(words.getOrNull(i + 2)?.word) -> {
                        if (count != null) return null
                        count = countOf(next) ?: return null
                        i++
                    }
                    word in MG || word in TIMES -> Unit
                    else -> {
                        val keyword = KEYWORDS[word]
                        if (keyword != null) keywords += keyword else names += word
                        described = true
                    }
                }
                i++
            }
            if (!described) return null

            // The sure readings go first, so a guess gives way to them.
            val readings = readingsOf(bare, words)?.sortedByDescending { it.sure } ?: return null
            for (reading in readings) {
                val isCount = when {
                    reading.isCount && count == null -> true
                    !reading.isCount && strength == null -> false
                    reading.sure -> return null
                    else -> !reading.isCount
                }
                if (isCount) {
                    if (count != null) return null
                    count = countOf(reading.number) ?: return null
                } else {
                    if (strength != null) return null
                    strength = Dose.parseMg(reading.number) ?: return null
                }
            }

            val units = keywords.mapNotNull { it.units }.reduceOrNull { a, b -> a intersect b }
            if (units != null && units.isEmpty()) return null
            return Phrase(count, strength, hours, keywords, units, names)
        }

        /**
         * How each bare number reads, from the words around it (see [ProductMatcher] for the rules),
         * leaving out a filler "one". Null when a number is too long to be either.
         */
        private fun readingsOf(bare: List<Bare>, words: List<Token>): List<Reading>? {
            val positions = bare.mapTo(HashSet()) { it.index }
            val singular = words.any { it.word in FORM_NOUNS } && words.none(::isCountable)
            return bare.mapNotNull { number ->
                val text = number.token.word
                if ('.' in text) return@mapNotNull Reading(text, isCount = false, sure = true)
                val value = text.toIntOrNull() ?: return null
                when {
                    number.index + 1 in positions -> Reading(text, isCount = true, sure = true)
                    number.index - 1 in positions -> Reading(text, isCount = false, sure = true)
                    isCountable(words.getOrNull(number.index + 1)) -> Reading(text, isCount = true, sure = true)
                    number.token.spelled && value == 1 && !number.leading -> null
                    number.leading -> {
                        val strengthLike = value > 1 && singular && (!number.token.spelled || value > COUNT_WORDS_UP_TO)
                        Reading(text, isCount = !strengthLike, sure = false)
                    }
                    else -> Reading(text, isCount = false, sure = false)
                }
            }
        }

        /**
         * The description's words, stemmed, with two words joined when together they're a name
         * ("quick mist" is QuickMist), number words as digits ("twenty-one" is 21), and small words
         * like "a" and "of" left out.
         */
        private fun tokens(split: List<String>, vocabulary: Set<String>): List<Token> {
            val out = mutableListOf<Token>()
            var i = 0
            while (i < split.size) {
                val word = split[i]
                val next = split.getOrNull(i + 1)
                val stem = Words.stem(word)
                val ones = next?.let { ONES[Words.stem(it)] }
                when {
                    Words.hasDigit(word) -> out += Token(word)
                    next != null && !Words.hasDigit(next) && Words.stem(word + next) in vocabulary -> {
                        out += Token(Words.stem(word + next))
                        i++
                    }
                    stem == TWENTY && ones != null -> {
                        out += Token((20 + ones).toString(), spelled = true)
                        i++
                    }
                    stem in NUMBER_WORDS -> out += Token(NUMBER_WORDS.getValue(stem), spelled = true)
                    stem in FILLERS -> Unit
                    else -> out += Token(stem, plural = stem != word)
                }
                i++
            }
            return out
        }

        /** "mg" or "hour", which make the number before them a strength or hours rather than a count. */
        private fun isUnit(word: String?): Boolean = word in MG || word in HOURS

        /** A unit word ("pieces", "spray") or a plural form word ("gums"): what a number before it counts. */
        private fun isCountable(token: Token?): Boolean =
            token != null && (token.word in UNIT_WORDS || (token.plural && token.word in FORM_NOUNS))

        private fun countOf(number: String): Int? = number.toIntOrNull()?.takeIf { it in 1..MAX_COUNT }

        /** Beyond any sensible dose. Callers set their own, lower limit on what's logged. */
        private const val MAX_COUNT = 999

        private fun stems(words: String): Set<String> = words.split(' ').map(Words::stem).toSet()

        private val MG = stems("mg milligram milligramme")
        private val HOURS = stems("h hr hrs hour")
        private val TIMES = stems("x times")
        private val REFUSED = stems("half halves quarter quarters")
        private val FRACTION = Regex("[0-9]\\s*/\\s*[0-9]")

        private val FILLERS = stems(
            "a an the of my some any another more nicotine nrt replacement therapy and with for me please " +
                "log logged record add i ive im had have took take taken used use using just now today " +
                "chewing chew chewed dose",
        )

        /** One to twenty, as digits, plus "a couple". "Twenty" and one of [ONES] make 21 to 29. */
        private val NUMBER_WORDS: Map<String, String> =
            (
                "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen " +
                    "sixteen seventeen eighteen nineteen twenty"
                )
                .split(' ')
                .mapIndexed { index, word -> Words.stem(word) to (index + 1).toString() }
                .toMap() + (Words.stem("couple") to "2")

        private val TWENTY = Words.stem("twenty")

        private val ONES: Map<String, Int> = "one two three four five six seven eight nine"
            .split(' ')
            .mapIndexed { index, word -> Words.stem(word) to index + 1 }
            .toMap()

        /** Counts with no number before them: "2mg gum twice". */
        private val MULTIPLIERS: Map<String, Int> = mapOf(Words.stem("once") to 1, Words.stem("twice") to 2)

        /**
         * Number words this high or lower stay how many before a singular form word, as in "two gum".
         * Higher ones read as the strength, the way digits do: "twenty-one patch" is the 21 mg patch.
         */
        private const val COUNT_WORDS_UP_TO = 10

        /** Words that name what's counted, so a number before them is how many. */
        private val UNIT_WORDS = stems("piece spray puff session cartridge")

        /** Form words that are things, rather than "mouth" or "nasal" which only describe one. */
        private val FORM_NOUNS = stems("gum lozenge mini patch inhalator inhaler microtab tablet tab strip film pouch")

        private val KEYWORDS: Map<String, Keyword> = buildMap {
            fun add(
                words: String,
                forms: Set<ProductForm>?,
                fallback: Set<ProductForm> = emptySet(),
                units: Set<DoseUnit>? = null,
            ) = stems(words).forEach { put(it, Keyword(it, forms, fallback, units)) }

            val sprays = setOf(ProductForm.MOUTH_SPRAY, ProductForm.NASAL_SPRAY)
            add("gum", setOf(ProductForm.GUM))
            add("piece", null, units = setOf(DoseUnit.PIECE))
            add("lozenge", setOf(ProductForm.LOZENGE), fallback = setOf(ProductForm.MINI_LOZENGE))
            add("mini", setOf(ProductForm.MINI_LOZENGE))
            add("patch", setOf(ProductForm.PATCH_16H, ProductForm.PATCH_24H))
            add("spray", sprays, units = setOf(DoseUnit.SPRAY))
            add("mouth oral", setOf(ProductForm.MOUTH_SPRAY))
            add("nasal nose", setOf(ProductForm.NASAL_SPRAY))
            add(
                "puff",
                setOf(ProductForm.MOUTH_SPRAY, ProductForm.INHALATOR),
                fallback = setOf(ProductForm.NASAL_SPRAY),
                units = setOf(DoseUnit.SPRAY, DoseUnit.PUFF),
            )
            add("inhalator inhaler", setOf(ProductForm.INHALATOR))
            add("session", setOf(ProductForm.INHALATOR), units = setOf(DoseUnit.SESSION))
            add("cartridge", setOf(ProductForm.INHALATOR), units = setOf(DoseUnit.CARTRIDGE))
            add("microtab tablet tab sublingual", setOf(ProductForm.MICROTAB))
            add("strip film", setOf(ProductForm.ORAL_FILM))
            add("pouch", setOf(ProductForm.POUCH))
        }
    }
}
