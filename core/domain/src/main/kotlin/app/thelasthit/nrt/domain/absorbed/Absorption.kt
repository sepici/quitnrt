package app.thelasthit.nrt.domain.absorbed

import app.thelasthit.nrt.domain.product.ProductForm
import java.time.LocalDate

/**
 * How much of a product's labelled nicotine reaches the blood, in thousandths: 500 is half. It's
 * a rough average for showing beside the labelled mg (PLAN §3, ROADMAP P9-1), and never feeds a
 * guide, an allowance, a result or an achievement. Those all use the labelled mg.
 */
@JvmInline
value class AbsorptionFactor(val perMille: Int) {
    init {
        require(perMille in 1..1000) { "An absorption factor is 1 to 1000 thousandths: $perMille" }
    }

    /** As a whole percentage for showing: 530 is 53. */
    val percent: Int get() = (perMille + 5) / 10
}

/** A published page or paper a factor comes from, with the day it was read. */
data class AbsorptionSource(
    val publisher: String,
    /** What the app shows: who wrote it and its title, or the product and the label's section. */
    val title: String,
    val year: Int?,
    val url: String,
    val read: LocalDate,
)

/**
 * One form's factor, with the [low] to [high] range the sources give where they give one, and a
 * note on how the figure was reached from them. [basis] is for whoever checks the figure, not
 * for the screen.
 */
data class FormFactor(
    val factor: AbsorptionFactor,
    val low: AbsorptionFactor?,
    val high: AbsorptionFactor?,
    val sources: List<AbsorptionSource>,
    val basis: String,
) {
    init {
        require(sources.isNotEmpty()) { "A factor needs a source" }
        require((low == null) == (high == null)) { "A range needs both ends" }
        if (low != null && high != null) require(low.perMille <= high.perMille) { "The range runs backwards" }
    }
}

/**
 * The factors for each form, from product leaflets (the emc and HPRA), US labels and
 * pharmacokinetic studies, all read on 24 October 2026. [ProductForm.OTHER] has none, since it
 * could be anything, so it never gets an estimate.
 */
object Absorption {

    private val READ: LocalDate = LocalDate.of(2026, 10, 24)

    val BENOWITZ_2009 = AbsorptionSource(
        publisher = "Handbook of Experimental Pharmacology",
        title = "Benowitz, Hukkanen and Jacob, Nicotine chemistry, metabolism, kinetics and biomarkers",
        year = 2009,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC2953858/",
        read = READ,
    )

    val BENOWITZ_1987 = AbsorptionSource(
        publisher = "Clinical Pharmacology and Therapeutics",
        title = "Benowitz, Jacob and Savanapridi, Determinants of nicotine intake while chewing nicotine polacrilex gum",
        year = 1987,
        url = "https://pubmed.ncbi.nlm.nih.gov/3829583/",
        read = READ,
    )

    val OLSSON_GISLESKOG_2021 = AbsorptionSource(
        publisher = "Clinical Pharmacokinetics",
        title = "Olsson Gisleskog and others, Nicotine population pharmacokinetics in healthy smokers after " +
            "intravenous, oral, buccal and transdermal administration",
        year = 2021,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC8016787/",
        read = READ,
    )

    val CHOI_2003 = AbsorptionSource(
        publisher = "Nicotine and Tobacco Research",
        title = "Choi and others, Pharmacokinetics of a nicotine polacrilex lozenge",
        year = 2003,
        url = "https://pubmed.ncbi.nlm.nih.gov/14577980/",
        read = READ,
    )

    val KRAICZI_2011 = AbsorptionSource(
        publisher = "Nicotine and Tobacco Research",
        title = "Kraiczi, Hansson and Perfekt, Single-dose pharmacokinetics of nicotine when given with a novel mouth spray",
        year = 2011,
        url = "https://pubmed.ncbi.nlm.nih.gov/21849415/",
        read = READ,
    )

    val AZZOPARDI_2022 = AbsorptionSource(
        publisher = "Scientific Reports",
        title = "Azzopardi and others, A randomised study to assess the nicotine pharmacokinetics of an oral " +
            "nicotine pouch and two nicotine replacement therapy products",
        year = 2022,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC9050656/",
        read = READ,
    )

    val NIQUITIN_MINIS_LEAFLET = AbsorptionSource(
        publisher = "NCSCT",
        title = "NiQuitin Minis 4 mg Lozenges, summary of product characteristics, section 5.2",
        year = 2023,
        url = "https://www.ncsct.co.uk/library/view/pdf/NiQuitin-Minis-4mg-Lozenges.pdf",
        read = READ,
    )

    val QUICKMIST_LEAFLET = AbsorptionSource(
        publisher = "emc",
        title = "Nicorette QuickMist 1 mg mouth spray, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/5956/smpc",
        read = READ,
    )

    val NICOTROL_NS_LABEL = AbsorptionSource(
        publisher = "Pfizer, US prescribing information",
        title = "Nicotrol NS nasal spray, clinical pharmacology",
        year = 2024,
        url = "https://labeling.pfizer.com/ShowLabeling.aspx?id=674",
        read = READ,
    )

    val NICOTROL_INHALER_LABEL = AbsorptionSource(
        publisher = "Pfizer, US prescribing information",
        title = "Nicotrol Inhaler, clinical pharmacology",
        year = 2024,
        url = "https://labeling.pfizer.com/ShowLabeling.aspx?id=633",
        read = READ,
    )

    val INHALATOR_LEAFLET = AbsorptionSource(
        publisher = "emc",
        title = "Nicorette 15 mg Inhalator, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/4707/smpc",
        read = READ,
    )

    val MICROTAB_LEAFLET = AbsorptionSource(
        publisher = "emc",
        title = "Nicorette Microtab 2 mg, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/6182/smpc",
        read = READ,
    )

    val STRIPS_LEAFLET = AbsorptionSource(
        publisher = "HPRA",
        title = "NiQuitin Strips Mint 2.5 mg, summary of product characteristics, section 5.2",
        year = 2015,
        url = "https://assets.hpra.ie/products/Human/25393/LicenseSPC_PA0678-071-018_27112015134038.pdf",
        read = READ,
    )

    val INVISI_LEAFLET = AbsorptionSource(
        publisher = "emc",
        title = "Nicorette Invisi 25 mg Patch, summary of product characteristics, sections 2 and 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/6435/smpc",
        read = READ,
    )

    val NICOTINELL_PATCH_LEAFLET = AbsorptionSource(
        publisher = "emc",
        title = "Nicotinell TTS 30 patch, summary of product characteristics, sections 2 and 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/388/smpc",
        read = READ,
    )

    private fun factor(perMille: Int, low: Int?, high: Int?, vararg sources: AbsorptionSource, basis: String) =
        FormFactor(AbsorptionFactor(perMille), low?.let(::AbsorptionFactor), high?.let(::AbsorptionFactor), sources.toList(), basis)

    private val GUM = factor(
        500, 430, 530, BENOWITZ_2009, BENOWITZ_1987, OLSSON_GISLESKOG_2021,
        basis = "Benowitz 2009 puts the systemic dose at 2 mg for a 4 mg gum. Chewers extract only 53% (2 mg) " +
            "and 72% (4 mg) on average, more than twofold apart between people (Benowitz 1987). The 2021 model " +
            "releases 64 to 79% and has 55% of that swallowed, at 40% oral bioavailability: 43 to 53% of the label.",
    )

    private val LOZENGE = factor(
        600, 500, 790, OLSSON_GISLESKOG_2021, CHOI_2003, BENOWITZ_2009,
        basis = "A lozenge dissolves completely. The 2021 model has 69% swallowed at 40% and the rest absorbed in " +
            "the mouth: about 59% of the label. Choi 2003 found 25 to 27% more exposure than gum of the same " +
            "strength. Benowitz 2009 lists 50% for a 2 mg lozenge and 79% for a 4 mg one.",
    )

    private val MINI_LOZENGE = factor(
        600, 500, 790, NIQUITIN_MINIS_LEAFLET, OLSSON_GISLESKOG_2021, BENOWITZ_2009,
        basis = "The NiQuitin Minis leaflet says a mini dissolves completely in about 10 minutes, with all its " +
            "nicotine there to absorb or swallow, as a standard lozenge's is. So it takes the lozenge's figure.",
    )

    private val MOUTH_SPRAY = factor(
        600, 580, 630, QUICKMIST_LEAFLET, KRAICZI_2011, OLSSON_GISLESKOG_2021,
        basis = "The QuickMist leaflet calls its bioavailability somewhat higher than gum's: 18.9 against 16.2 " +
            "h·ng/ml for 2 mg each, so about 58% against gum's 50%. Kraiczi 2011 found 4 mg of spray 15% above " +
            "4 mg gum and level with a 4 mg lozenge. The 2021 model has 61% swallowed: about 63% of the label.",
    )

    private val NASAL_SPRAY = factor(
        530, 370, 800, NICOTROL_NS_LABEL, BENOWITZ_2009,
        basis = "The Nicotrol NS label says about 53% of 2 sprays enters the circulation, plus or minus 16% (one " +
            "standard deviation). Benowitz 2009 gives 0.7 mg per 1 mg dose, and 60 to 80%.",
    )

    private val INHALATOR = factor(
        200, 70, 310, NICOTROL_INHALER_LABEL, INHALATOR_LEAFLET,
        basis = "The Nicotrol label: a 10 mg cartridge releases about 4 mg with intensive use, of which about 2 mg " +
            "is absorbed. The Nicorette Inhalator leaflet, from 10 mg data: forced puffing for 20 minutes releases " +
            "1.3 to 6.2 mg, so with about half absorbed, 7 to 31% of a 10 mg cartridge. It depends on how hard " +
            "and long you puff, and on the temperature.",
    )

    private val MICROTAB = factor(
        500, 500, 650, MICROTAB_LEAFLET, BENOWITZ_2009,
        basis = "The Microtab leaflet gives an absolute bioavailability of approximately 50%. Benowitz 2009 lists " +
            "65% for a 2 mg sublingual tablet.",
    )

    private val STRIP = factor(
        500, null, null, STRIPS_LEAFLET, BENOWITZ_2009,
        basis = "The NiQuitin Strips leaflet says a strip dissolves completely in about 3 minutes, all of it there " +
            "to absorb or swallow, and a 2.5 mg strip peaks at about 4.1 ng/ml. That's close to a 2 mg lozenge's " +
            "4.4 ng/ml, which Benowitz 2009 puts at 50%, so it takes the lower lozenge figure.",
    )

    private val POUCH = factor(
        600, null, null, AZZOPARDI_2022,
        basis = "Azzopardi 2022 found a 4 mg pouch gave the same exposure as a 4 mg lozenge (an AUC ratio of " +
            "101%), with 62% of its nicotine extracted. So it takes the lozenge's figure. Pouches vary a lot " +
            "between makes.",
    )

    private val PATCH = factor(
        800, 680, 1000, INVISI_LEAFLET, NICOTINELL_PATCH_LEAFLET, OLSSON_GISLESKOG_2021, BENOWITZ_2009,
        basis = "Patches are labelled by the nicotine they release over 16 or 24 hours (the Invisi leaflet; " +
            "Nicotinell's speaks of an average absorption rate of 21 mg in 24 hours). Of what's released, about " +
            "77% reaches the blood (the Nicotinell leaflet), 76% in the 2021 model, and 68 to 100% across brands " +
            "in Benowitz 2009.",
    )

    /** Each form's factor, in the order the Settings page lists them. [ProductForm.OTHER] isn't here. */
    val byForm: Map<ProductForm, FormFactor> = mapOf(
        ProductForm.GUM to GUM,
        ProductForm.LOZENGE to LOZENGE,
        ProductForm.MINI_LOZENGE to MINI_LOZENGE,
        ProductForm.MOUTH_SPRAY to MOUTH_SPRAY,
        ProductForm.NASAL_SPRAY to NASAL_SPRAY,
        ProductForm.INHALATOR to INHALATOR,
        ProductForm.MICROTAB to MICROTAB,
        ProductForm.ORAL_FILM to STRIP,
        ProductForm.POUCH to POUCH,
        ProductForm.PATCH_16H to PATCH,
        ProductForm.PATCH_24H to PATCH,
    )

    /** Every source, once each, in the order the forms first cite them. */
    val sources: List<AbsorptionSource> = byForm.values.flatMap { it.sources }.distinct()

    /** The latest day any source was read, for "Checked October 2026". */
    val checked: LocalDate = sources.maxOf { it.read }
}
