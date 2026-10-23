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
    }
}

fun Iterable<Dose>.sum(): Dose = fold(Dose.ZERO, Dose::plus)
