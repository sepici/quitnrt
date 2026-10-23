package app.thelasthit.nrt.domain.matcher

import java.util.Locale

/** Turns free text into words that compare: "Sainsbury's 2mg gums" becomes sainsburys, 2, mg, gums. */
internal object Words {

    /**
     * Lower case, apostrophes dropped, a number split from the letters beside it ("2mg", "x2"), and
     * a decimal comma read as a point. Dots stay inside a word, so "0.5" stays one number. A hyphen
     * between digits just before "mg" is a decimal point too, the way catalogue IDs write one
     * ("nasal-spray-0-5mg").
     */
    fun split(text: String): List<String> {
        val cleaned = text.lowercase(Locale.ROOT)
            .replace(APOSTROPHES, "")
            .replace('×', 'x')
            .replace(DECIMAL_COMMA) { "${it.groupValues[1]}.${it.groupValues[2]}" }
            .replace(BARE_DECIMAL) { "${it.groupValues[1]}0.${it.groupValues[2]}" }
            .replace(HYPHEN_DECIMAL) { "${it.groupValues[1]}.${it.groupValues[2]}" }
            .replace(DIGIT_LETTER) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .replace(LETTER_DIGIT) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        return cleaned.split(SEPARATORS).map { it.trim('.') }.filter { it.isNotEmpty() }
    }

    /** Words for comparing names exactly: "After a meal" and "after-a-meal" are both "after a meal". */
    fun plain(text: String): String =
        text.lowercase(Locale.ROOT).replace(APOSTROPHES, "").split(NOT_WORD).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Plural to singular, done the same way to the phrase and the product names, so "patches"
     * meets "patch" and "minis" meets "mini". It only has to agree with itself, not be good English.
     */
    fun stem(word: String): String = when {
        word.length > 4 && ES_ENDINGS.any { word.endsWith(it) } -> word.dropLast(2)
        word.length > 3 && word.endsWith('s') && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    fun isNumber(word: String): Boolean = NUMBER.matches(word)

    fun hasDigit(word: String): Boolean = word.any { it in '0'..'9' }

    private val APOSTROPHES = Regex("['’‘`]")
    private val DECIMAL_COMMA = Regex("([0-9]),([0-9])")
    private val BARE_DECIMAL = Regex("(^|[^0-9])\\.([0-9])")
    private val HYPHEN_DECIMAL = Regex("([0-9]+)-([0-9]+)(?=\\s*(mg|milligram))")
    private val DIGIT_LETTER = Regex("([0-9])(\\p{L})")
    private val LETTER_DIGIT = Regex("(\\p{L})([0-9])")
    private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}.]+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val NUMBER = Regex("[0-9]+(\\.[0-9]+)?")
    private val ES_ENDINGS = listOf("ches", "shes", "xes", "sses")
}
