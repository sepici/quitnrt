package app.thelasthit.nrt.domain.catalogue

import java.util.Locale

/** The countries the catalogue covers. Anywhere else gets the generic products. */
object Markets {
    val supported: List<String> = listOf("GB", "IE", "US", "CA", "AU", "NZ")

    /** The country stored for "somewhere else". No product is sold there, so only generic ones show. */
    const val ELSEWHERE = "ZZ"

    /** The market for the phone's region, such as "GB", or [ELSEWHERE] when it isn't covered. */
    fun forRegion(region: String?): String = region?.uppercase(Locale.ROOT)?.takeIf { it in supported } ?: ELSEWHERE
}
