package app.thelasthit.nrt.domain.catalogue

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.DoseUnit
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.product.RollingLimit
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.Locale

/**
 * The products the app knows about out of the box. The data comes from
 * `docs/research/catalogue/` and is bundled as `catalogue/products.json`.
 */
class Catalogue internal constructor(val products: List<CatalogueProduct>) {

    private val byId = products.associateBy { it.id }

    fun find(id: String): CatalogueProduct? = byId[id]

    /**
     * Products sold in [country] (an ISO code such as "GB"), plus the generic ones. Big brands
     * come first, then shop brands, then generic. Discontinued packs are left out unless asked for.
     */
    fun forMarket(country: String, includeDiscontinued: Boolean = false): List<CatalogueProduct> =
        products.filter { product ->
            (product.markets.isEmpty() || country.uppercase(Locale.ROOT) in product.markets) &&
                (includeDiscontinued || product.status != ProductStatus.DISCONTINUED)
        }.sortedBy { it.kind.ordinal }

    /**
     * Products where every word of [query] matches: a word of the brand, line or short name, or a
     * number matching the strength ("niquitin minis 4"). Matches in [country] come first.
     */
    fun search(query: String, country: String? = null): List<CatalogueProduct> {
        val words = query.lowercase(Locale.ROOT)
            .replace(Regex("(\\d)mg\\b"), "$1 mg")
            .split(Regex("[^a-z0-9.]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val local = country?.uppercase(Locale.ROOT)
        return products
            .filter { product -> words.all { word -> product.matches(word) } }
            .sortedWith(
                compareBy<CatalogueProduct> { if (local != null && local in it.markets) 0 else 1 }
                    .thenBy { it.status == ProductStatus.DISCONTINUED }
                    .thenBy { it.kind.ordinal },
            )
    }

    companion object {
        private const val RESOURCE = "/catalogue/products.json"

        private val json = Json { ignoreUnknownKeys = false }

        /** Loads the catalogue bundled with the app. */
        fun bundled(): Catalogue {
            val text = Catalogue::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Catalogue resource $RESOURCE is missing")
            return parse(text)
        }

        internal fun parse(text: String): Catalogue {
            val file = json.decodeFromString(CatalogueFile.serializer(), text)
            require(file.version == 1) { "Unsupported catalogue version ${file.version}" }
            val products = file.lines.flatMap { line ->
                line.strengths.map { strength -> CatalogueProduct.from(line, strength) }
            }
            val duplicates = products.groupBy { it.id }.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) { "Duplicate product ids: $duplicates" }
            return Catalogue(products)
        }
    }
}

data class CatalogueProduct(
    val id: String,
    val lineId: String,
    val brand: String,
    val line: String,
    val shortName: String,
    val kind: LineKind,
    val form: ProductForm,
    /** The unit the label counts in. The inhalator's is a cartridge, though it's logged in sessions. */
    val unit: DoseUnit,
    val strength: Dose,
    val label: LabelLimit,
    /** ISO country codes. Empty means generic: sold anywhere. */
    val markets: Set<String>,
    val status: ProductStatus,
    val flavours: List<String>,
) {
    /** For example "Nicorette gum 2 mg" or "NiQuitin Minis 1.5 mg". */
    val displayName: String get() = "$shortName ${formatMg(strength)} mg"

    internal fun matches(word: String): Boolean {
        word.toDoubleOrNull()?.let { number -> return number == strength.milligrams }
        if (word == "mg") return true
        return searchWords.any { it.startsWith(word) }
    }

    private val searchWords: Set<String> by lazy {
        listOf(brand, line, shortName)
            .flatMap { it.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    internal companion object {
        fun from(line: CatalogueFile.LineEntry, strength: CatalogueFile.StrengthEntry): CatalogueProduct {
            require(strength.mg > 0) { "${line.id} has a strength of ${strength.mg} mg" }
            val dose = Dose.mg(strength.mg)
            return CatalogueProduct(
                id = "${line.id}-${formatMg(dose).replace('.', '-')}mg",
                lineId = line.id,
                brand = line.brand,
                line = line.line,
                shortName = line.shortName,
                kind = line.kind,
                form = line.form,
                unit = line.unit,
                strength = dose,
                label = LabelLimit(
                    maxUnitsPerDay = strength.maxPerDay,
                    window = line.window?.let { RollingLimit(it.count, Duration.ofHours(it.hours.toLong())) },
                ),
                markets = line.markets.toSet(),
                status = line.status,
                flavours = line.flavours,
            )
        }

        /** 2.0 becomes "2", 1.5 stays "1.5", 0.5 stays "0.5". */
        fun formatMg(dose: Dose): String {
            val mg = dose.milligrams
            return if (mg == Math.floor(mg)) mg.toLong().toString() else mg.toString()
        }
    }
}

/** Order matters: big brands are listed before shop brands, and generic products come last. */
enum class LineKind { BRAND, OWN_BRAND, GENERIC }

enum class ProductStatus { CURRENT, UNCERTAIN, DISCONTINUED }
