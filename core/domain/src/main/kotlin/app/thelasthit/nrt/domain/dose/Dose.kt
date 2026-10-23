package app.thelasthit.nrt.domain.dose

/**
 * An amount of nicotine. It's held in whole micrograms so that sums and allowance checks are
 * exact: an eighth of a 15 mg inhalator cartridge is exactly 1875 µg, and eight of them add
 * back up to 15 mg.
 */
@JvmInline
value class Dose(val micrograms: Long) : Comparable<Dose> {

    init {
        require(micrograms >= 0) { "A dose can't be negative: $micrograms µg" }
    }

    val milligrams: Double get() = micrograms / 1000.0

    operator fun plus(other: Dose): Dose = Dose(micrograms + other.micrograms)

    operator fun times(quantity: Int): Dose {
        require(quantity >= 0) { "Quantity can't be negative: $quantity" }
        return Dose(micrograms * quantity)
    }

    override fun compareTo(other: Dose): Int = micrograms.compareTo(other.micrograms)

    override fun toString(): String = "$milligrams mg"

    companion object {
        val ZERO = Dose(0)

        fun mg(milligrams: Int): Dose = Dose(milligrams * 1000L)

        fun mg(milligrams: Double): Dose = Dose(Math.round(milligrams * 1000))

        /**
         * A strength typed in mg, such as "2" or "1.5", worked out in whole micrograms without
         * going through floating point. A comma works as the decimal point too. Null for anything
         * that isn't a positive amount of at most [MAX_TYPED_MG], or finer than a microgram.
         */
        fun parseMg(text: String): Dose? {
            val cleaned = text.trim().replace(',', '.')
            if (!TYPED_MG.matches(cleaned)) return null
            val (whole, fraction) = cleaned.split('.').let { it[0] to it.getOrElse(1) { "" } }
            val micrograms = whole.toLong() * 1000 + fraction.padEnd(3, '0').toLong()
            return Dose(micrograms).takeIf { it > ZERO && it <= mg(MAX_TYPED_MG) }
        }

        /** More than any single unit of NRT, so a typo like 200 is refused. */
        const val MAX_TYPED_MG = 100

        private val TYPED_MG = Regex("""\d{1,3}(\.\d{1,3})?""")
    }
}

fun Iterable<Dose>.sum(): Dose = fold(Dose.ZERO, Dose::plus)
