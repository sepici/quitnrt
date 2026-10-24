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

/** What kind of source a factor comes from, which is how the Settings page groups them. */
enum class SourceKind { LEAFLET, LABEL, STUDY }

/** A published page or paper a factor comes from, with the day it was read. */
data class AbsorptionSource(
    val kind: SourceKind,
    /**
     * The name the Settings page gives it in its line of sources: the product for a leaflet or
     * label, such as "QuickMist", and the first author for a study, such as "Benowitz", with its
     * [year] beside it.
     */
    val short: String,
    val publisher: String,
    /** The full citation: who wrote it and its title, or the product and the label's sections. */
    val title: String,
    val year: Int?,
    val url: String,
    val read: LocalDate,
)

/**
 * One form's factor, or one catalogue line's, with the [low] to [high] range the sources give
 * where they give one, and a note on how the figure was reached from them. [basis] is for
 * whoever checks the figure, not for the screen.
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
 * The factors for each form, and for the few catalogue lines that differ from their form, from
 * product leaflets (the emc, HPRA and Medsafe), US labels and pharmacokinetic studies, all read
 * on 24 October 2026. [ProductForm.OTHER] has none, since it could be anything, so it never gets
 * an estimate.
 */
object Absorption {

    private val READ: LocalDate = LocalDate.of(2026, 10, 24)

    val BENOWITZ_2009 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Benowitz",
        publisher = "Handbook of Experimental Pharmacology",
        title = "Benowitz, Hukkanen and Jacob, Nicotine chemistry, metabolism, kinetics and biomarkers",
        year = 2009,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC2953858/",
        read = READ,
    )

    val BENOWITZ_1987 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Benowitz",
        publisher = "Clinical Pharmacology and Therapeutics",
        title = "Benowitz, Jacob and Savanapridi, Determinants of nicotine intake while chewing nicotine polacrilex gum",
        year = 1987,
        url = "https://pubmed.ncbi.nlm.nih.gov/3829583/",
        read = READ,
    )

    val OLSSON_GISLESKOG_2021 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Olsson Gisleskog",
        publisher = "Clinical Pharmacokinetics",
        title = "Olsson Gisleskog and others, Nicotine population pharmacokinetics in healthy smokers after " +
            "intravenous, oral, buccal and transdermal administration",
        year = 2021,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC8016787/",
        read = READ,
    )

    val CHOI_2003 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Choi",
        publisher = "Nicotine and Tobacco Research",
        title = "Choi and others, Pharmacokinetics of a nicotine polacrilex lozenge",
        year = 2003,
        url = "https://pubmed.ncbi.nlm.nih.gov/14577980/",
        read = READ,
    )

    val KRAICZI_2011 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Kraiczi",
        publisher = "Nicotine and Tobacco Research",
        title = "Kraiczi, Hansson and Perfekt, Single-dose pharmacokinetics of nicotine when given with a novel mouth spray",
        year = 2011,
        url = "https://pubmed.ncbi.nlm.nih.gov/21849415/",
        read = READ,
    )

    val AZZOPARDI_2022 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Azzopardi",
        publisher = "Scientific Reports",
        title = "Azzopardi and others, A randomised study to assess the nicotine pharmacokinetics of an oral " +
            "nicotine pouch and two nicotine replacement therapy products",
        year = 2022,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC9050656/",
        read = READ,
    )

    val DAUTZENBERG_2007 = AbsorptionSource(
        kind = SourceKind.STUDY,
        short = "Dautzenberg",
        publisher = "BMC Clinical Pharmacology 7:11",
        title = "Dautzenberg, Nides, Kienzler and Callens, Pharmacokinetics, safety and efficacy from randomized " +
            "controlled trials of 1 and 2 mg nicotine bitartrate lozenges (Nicotinell)",
        year = 2007,
        url = "https://pmc.ncbi.nlm.nih.gov/articles/PMC2194660/",
        read = READ,
    )

    val NIQUITIN_MINIS_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "NiQuitin Minis",
        publisher = "NCSCT",
        title = "NiQuitin Minis 4 mg Lozenges, summary of product characteristics, section 5.2",
        year = 2023,
        url = "https://www.ncsct.co.uk/library/view/pdf/NiQuitin-Minis-4mg-Lozenges.pdf",
        read = READ,
    )

    val QUICKMIST_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "QuickMist",
        publisher = "emc",
        title = "Nicorette QuickMist 1 mg mouth spray, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/5956/smpc",
        read = READ,
    )

    val NICOTROL_NS_LABEL = AbsorptionSource(
        kind = SourceKind.LABEL,
        short = "Nicotrol NS",
        publisher = "Pfizer, US prescribing information",
        title = "Nicotrol NS nasal spray, clinical pharmacology",
        year = 2024,
        url = "https://labeling.pfizer.com/ShowLabeling.aspx?id=674",
        read = READ,
    )

    val NICOTROL_INHALER_LABEL = AbsorptionSource(
        kind = SourceKind.LABEL,
        short = "Nicotrol Inhaler",
        publisher = "Pfizer, US prescribing information",
        title = "Nicotrol Inhaler, clinical pharmacology",
        year = 2024,
        url = "https://labeling.pfizer.com/ShowLabeling.aspx?id=633",
        read = READ,
    )

    val INHALATOR_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Inhalator",
        publisher = "emc",
        title = "Nicorette 15 mg Inhalator, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/4707/smpc",
        read = READ,
    )

    val MICROTAB_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Microtab",
        publisher = "emc",
        title = "Nicorette Microtab 2 mg, summary of product characteristics, section 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/6182/smpc",
        read = READ,
    )

    val STRIPS_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "NiQuitin Strips",
        publisher = "HPRA",
        title = "NiQuitin Strips Mint 2.5 mg, summary of product characteristics, sections 5.1 and 5.2",
        year = 2015,
        url = "https://assets.hpra.ie/products/Human/25393/LicenseSPC_PA0678-071-018_27112015134038.pdf",
        read = READ,
    )

    val INVISI_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Invisi",
        publisher = "emc",
        title = "Nicorette Invisi 25 mg Patch, summary of product characteristics, sections 2 and 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/6435/smpc",
        read = READ,
    )

    val NICOTINELL_PATCH_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Nicotinell",
        publisher = "emc",
        title = "Nicotinell TTS 30 patch, summary of product characteristics, sections 2 and 5.2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/388/smpc",
        read = READ,
    )

    val NICOTINELL_LOZENGE_1MG_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Nicotinell",
        publisher = "emc",
        title = "Nicotinell Mint 1 mg Lozenge, summary of product characteristics, sections 2 and 5.2",
        year = 2025,
        url = "https://www.medicines.org.uk/emc/product/2254/smpc",
        read = READ,
    )

    val NICOTINELL_LOZENGE_2MG_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Nicotinell",
        publisher = "emc",
        title = "Nicotinell Mint 2 mg Lozenge, summary of product characteristics, sections 2 and 5.2",
        year = 2025,
        url = "https://www.medicines.org.uk/emc/product/1190/smpc",
        read = READ,
    )

    val BOOTS_NICASSIST_LOZENGE_LEAFLET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Boots NicAssist",
        publisher = "emc",
        title = "Boots NicAssist 1 mg compressed lozenge, summary of product characteristics, section 2",
        year = 2026,
        url = "https://www.medicines.org.uk/emc/product/8534/smpc",
        read = READ,
    )

    val HABITROL_LOZENGE_DATA_SHEET = AbsorptionSource(
        kind = SourceKind.LEAFLET,
        short = "Habitrol",
        publisher = "Medsafe",
        title = "Habitrol Lozenge 1 mg and 2 mg, New Zealand data sheet, section 2",
        year = 2019,
        url = "https://www.medsafe.govt.nz/profs/Datasheet/h/Habitrollozenge.pdf",
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
        480, 400, 480, STRIPS_LEAFLET, BENOWITZ_2009,
        basis = "The NiQuitin Strips leaflet calls the 2.5 mg strip bioequivalent to the 2 mg lozenge (section " +
            "5.1), and its single-dose peak of about 4.13 ng/ml (section 5.2) matches a 2 mg lozenge's. So a strip " +
            "takes the 2 mg lozenge's absorbed amount spread over its 2.5 mg: 1.2 mg at the lozenge's 60% is 48%. " +
            "Benowitz 2009 puts the 2 mg lozenge at 50%, which would make it 40%.",
    )

    private val POUCH = factor(
        600, null, null, AZZOPARDI_2022,
        basis = "Azzopardi 2022 found a 4 mg pouch gave the same exposure as a 4 mg lozenge (an AUC ratio of " +
            "101%), with 62% of its nicotine extracted. So it takes the lozenge's figure. Pouches vary a lot " +
            "between makes.",
    )

    private val PATCH = factor(
        800, 680, 1000, INVISI_LEAFLET, NICOTINELL_PATCH_LEAFLET, OLSSON_GISLESKOG_2021, BENOWITZ_2009,
        basis = "Patches are labelled by the nicotine they deliver over 16 or 24 hours (the Invisi leaflet). The " +
            "Nicotinell leaflet reads two ways. Section 2 says the 21 mg patch \"provides an average absorption " +
            "rate of 21 mg nicotine in 24 hours\", which calls the labelled 21 mg the absorbed amount (100%). " +
            "Section 5.2 says \"the absolute bioavailability of the patch, compared to intravenous nicotine " +
            "perfusion, is about 77%\". 80% follows the second reading, with the 2021 model, where 76% of what's " +
            "released reaches the blood, and Benowitz 2009, which gives 68 to 100% across brands. Reading the " +
            "labelled mg as the absorbed amount instead would make it 100%.",
    )

    /**
     * The 1 mg and 2 mg nicotine bitartrate lozenge sold as Nicotinell Mint, Boots NicAssist,
     * Thrive (Canada) and Habitrol (New Zealand). Its salt dissolves into the mouth more fully
     * than the polacrilex lozenges the form's figure is drawn from.
     */
    private val BITARTRATE_LOZENGE = factor(
        800, 730, 900, DAUTZENBERG_2007, NICOTINELL_LOZENGE_1MG_LEAFLET, NICOTINELL_LOZENGE_2MG_LEAFLET,
        BOOTS_NICASSIST_LOZENGE_LEAFLET, HABITROL_LOZENGE_DATA_SHEET,
        basis = "Dautzenberg 2007 found the 1 mg lozenge bioequivalent to 2 mg gum: at steady state its AUC is " +
            "90% of the gum's, which the app shows as 1 mg absorbed, so 0.9 mg (90%). The 2 mg lozenge reached 73% " +
            "of 4 mg gum's AUC at steady state, so 1.46 mg of the gum's 2 mg (73%), and 145% of 2 mg gum's after " +
            "a single dose (72%). The Nicotinell leaflets give the same steady-state peaks (10.6 and 22.5 ng/ml) " +
            "and call the two strengths dose-proportional, so one figure covers both: 80%, between the 2 mg's 73% " +
            "and the 1 mg's 90%. The Boots NicAssist and Habitrol leaflets list the same salt and strengths " +
            "(nicotine bitartrate dihydrate, 3.072 mg for 1 mg), as Thrive's Canadian label does.",
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

    /**
     * The catalogue lines whose own figure differs from their form's, by line ID. Each one's
     * `absorptionPerMille` in `products.json` is this factor, and a test keeps the two the same.
     */
    val byLine: Map<String, FormFactor> = mapOf(
        "nicotinell-lozenge-uk" to BITARTRATE_LOZENGE,
        "boots-nicassist-lozenge" to BITARTRATE_LOZENGE,
        "thrive-lozenge-ca" to BITARTRATE_LOZENGE,
        "habitrol-lozenge-nz" to BITARTRATE_LOZENGE,
    )

    /** The bitartrate lozenge's figure, for the Settings page's line of its own. */
    val bitartrateLozenge: FormFactor get() = BITARTRATE_LOZENGE

    /** Every source, once each, in the order the forms and then the lines first cite them. */
    val sources: List<AbsorptionSource> = (byForm.values + byLine.values).flatMap { it.sources }.distinct()

    /** The latest day any source was read, for "Last checked October 2026". */
    val checked: LocalDate = sources.maxOf { it.read }
}
