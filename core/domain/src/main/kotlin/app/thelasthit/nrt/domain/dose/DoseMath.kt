package app.thelasthit.nrt.domain.dose

import java.time.Duration

object DoseMath {

    /**
     * The UK Nicorette Inhalator leaflet says a 15 mg cartridge gives about eight 5-minute
     * sessions. It gives no figure for puffs, so a session is the default unit.
     */
    const val SESSIONS_PER_CARTRIDGE = 8

    fun total(perUnit: Dose, quantity: Int): Dose {
        require(quantity >= 1) { "Quantity must be at least 1: $quantity" }
        return perUnit * quantity
    }

    /**
     * A patch counts in full toward the day it went on. If it came off early, it counts in
     * proportion to the time worn, capped at the patch's rated hours.
     */
    fun patch(label: Dose, ratedHours: Int, worn: Duration? = null): Dose {
        require(ratedHours > 0) { "Rated hours must be positive: $ratedHours" }
        if (worn == null) return label
        require(!worn.isNegative) { "Time worn can't be negative: $worn" }
        val ratedMinutes = ratedHours * 60L
        val wornMinutes = worn.toMinutes().coerceAtMost(ratedMinutes)
        return Dose(roundedDiv(label.micrograms * wornMinutes, ratedMinutes))
    }

    fun inhalatorPerUnit(cartridge: Dose, unit: InhalatorUnit, puffsPerCartridge: Int? = null): Dose =
        when (unit) {
            InhalatorUnit.CARTRIDGE -> cartridge
            InhalatorUnit.SESSION -> Dose(roundedDiv(cartridge.micrograms, SESSIONS_PER_CARTRIDGE.toLong()))
            InhalatorUnit.PUFF -> {
                requireNotNull(puffsPerCartridge) { "Logging puffs needs your own puffs-per-cartridge figure" }
                require(puffsPerCartridge > 0) { "Puffs per cartridge must be positive: $puffsPerCartridge" }
                Dose(roundedDiv(cartridge.micrograms, puffsPerCartridge.toLong()))
            }
        }

    /** Division rounded to the nearest whole number, halves rounded up. Both arguments are non-negative. */
    private fun roundedDiv(numerator: Long, denominator: Long): Long = (numerator * 2 + denominator) / (denominator * 2)
}

enum class InhalatorUnit { SESSION, CARTRIDGE, PUFF }
