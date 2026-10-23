package app.thelasthit.nrt.domain.matcher

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.matcher.LogLinkError.AmbiguousProduct
import app.thelasthit.nrt.domain.matcher.LogLinkError.AmbiguousTags
import app.thelasthit.nrt.domain.matcher.LogLinkError.BadEncoding
import app.thelasthit.nrt.domain.matcher.LogLinkError.BadQuantity
import app.thelasthit.nrt.domain.matcher.LogLinkError.BadTime
import app.thelasthit.nrt.domain.matcher.LogLinkError.ConflictingQuantity
import app.thelasthit.nrt.domain.matcher.LogLinkError.MissingProduct
import app.thelasthit.nrt.domain.matcher.LogLinkError.NotALogLink
import app.thelasthit.nrt.domain.matcher.LogLinkError.RepeatedParameter
import app.thelasthit.nrt.domain.matcher.LogLinkError.TimeInFuture
import app.thelasthit.nrt.domain.matcher.LogLinkError.TooMany
import app.thelasthit.nrt.domain.matcher.LogLinkError.UnknownParameter
import app.thelasthit.nrt.domain.matcher.LogLinkError.UnknownProduct
import app.thelasthit.nrt.domain.matcher.LogLinkError.UnknownTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class LogLinkParserTest {

    private val catalogue = Catalogue.bundled()
    private fun item(id: String, quickLog: Int? = null) = requireNotNull(catalogue.find(id)) { "missing $id" }.toUserProduct(quickLog)

    private val gum2 = item("nicorette-gum-uk-2mg", 0)
    private val gum4 = item("nicorette-gum-uk-4mg", 1)
    private val mist = item("nicorette-quickmist-uk-1mg", 2)
    private val inhalator = item("nicorette-inhalator-uk-15mg")
    private val hiddenPatch = item("niquitin-clear-patch-uk-21mg").copy(isHidden = true)

    private val parser = LogLinkParser(
        ProductMatcher(listOf(gum2, gum4, mist, inhalator, hiddenPatch), catalogue),
        TagMatcher(
            BuiltInTags.all.map { TagName(it.key) } +
                TagName("after_meal", listOf("After a meal")) +
                TagName("custom_1a2b", listOf("Gaming")),
        ),
    )

    private val london = ZoneId.of("Europe/London")

    /** Friday 23 October 2026, 15:05 in London, as in the app's tests. */
    private val now = Instant.parse("2026-10-23T14:05:00Z")

    private fun parse(link: String, at: Instant = now, zone: ZoneId = london) = parser.parse(link, at, zone)

    private fun valid(link: String, at: Instant = now, zone: ZoneId = london): LogLinkRequest {
        val result = parse(link, at, zone)
        return (result as? LogLinkResult.Valid)?.request ?: fail("$link was refused: $result")
    }

    private fun refused(link: String, at: Instant = now, zone: ZoneId = london): LogLinkError {
        val result = parse(link, at, zone)
        return (result as? LogLinkResult.Invalid)?.error ?: fail("$link was accepted: $result")
    }

    @Test
    fun `the link from the plan`() {
        assertEquals(
            LogLinkRequest(
                product = gum2,
                quantity = 1,
                at = Instant.parse("2026-10-23T14:00:00Z"),
                tagKeys = setOf("stressed", "after_meal"),
            ),
            valid("lasthit://log?product=nicorette-gum-2mg&qty=1&at=2026-10-23T15:00&tags=stressed,after-meal"),
        )
    }

    @Test
    fun `only the product is needed`() {
        assertEquals(LogLinkRequest(gum4, 1, at = null, tagKeys = emptySet()), valid("lasthit://log?product=nicorette-gum-uk-4mg"))
        assertEquals(gum4, valid("LastHit://LOG/?Product=nicorette-gum-uk-4mg#from-nfc").product)
    }

    @Test
    fun `the product can be a description`() {
        val sprays = valid("lasthit://log?product=two%20sprays%20of%20QuickMist")
        assertEquals(mist, sprays.product)
        assertEquals(2, sprays.quantity)
        assertEquals(mist, valid("lasthit://log?product=quick+mist&qty=3").product)
        assertEquals(3, valid("lasthit://log?product=quick+mist&qty=3").quantity)
        assertEquals(gum4, valid("lasthit://log?product=nicorette-4").product)
    }

    @Test
    fun `numbers in the product are read the way the matcher reads them`() {
        val patch = item("niquitin-clear-patch-uk-21mg")
        val nasal = item("nicorette-nasal-spray-uk-0-5mg")
        val parser = LogLinkParser(ProductMatcher(listOf(gum2, gum4, patch, nasal), catalogue), TagMatcher.builtIns())
        fun read(link: String) = parser.parse(link, now, london)

        // "The 21 patch" is the 21 mg patch, not 21 patches.
        assertEquals(LogLinkResult.Valid(LogLinkRequest(patch, 1, null, emptySet())), read("lasthit://log?product=21-patch"))
        assertEquals(LogLinkResult.Valid(LogLinkRequest(patch, 2, null, emptySet())), read("lasthit://log?product=21-patch&qty=2"))
        // A number before a unit is how many, so this asks which strength.
        assertEquals(
            LogLinkResult.Invalid(AmbiguousProduct("nicorette gum 2 pieces", listOf(gum2, gum4))),
            read("lasthit://log?product=nicorette%20gum%202%20pieces"),
        )
        assertEquals(
            LogLinkResult.Invalid(ConflictingQuantity(inProduct = 2, qty = 3)),
            read("lasthit://log?product=4mg-gum-2-pieces&qty=3"),
        )
        // The catalogue's way of writing 0.5 mg, with or without the country.
        assertEquals(LogLinkResult.Valid(LogLinkRequest(nasal, 1, null, emptySet())), read("lasthit://log?product=nicorette-nasal-spray-0-5mg"))
        assertEquals(LogLinkResult.Valid(LogLinkRequest(nasal, 1, null, emptySet())), read("lasthit://log?product=nicorette-nasal-spray-uk-0-5mg"))
    }

    @Test
    fun `qty counts what the product names`() {
        val cartridges = valid("lasthit://log?product=inhalator-cartridge&qty=2")
        assertEquals(inhalator, cartridges.product)
        assertEquals(16, cartridges.quantity)
        assertEquals(TooMany(104), refused("lasthit://log?product=inhalator-cartridge&qty=13"))
        assertEquals(2, valid("lasthit://log?product=two-4mg-gums&qty=2").quantity)
        assertEquals(ConflictingQuantity(inProduct = 2, qty = 3), refused("lasthit://log?product=two-4mg-gums&qty=3"))
    }

    @Test
    fun `qty runs from 1 to 99`() {
        assertEquals(99, valid("lasthit://log?product=nicorette-gum-uk-2mg&qty=99").quantity)
        assertEquals(1, valid("lasthit://log?product=nicorette-gum-uk-2mg&qty=01").quantity)
        listOf("0", "100", "-1", "1.5", "two", "", "99999999999").forEach { qty ->
            assertEquals(BadQuantity(qty), refused("lasthit://log?product=nicorette-gum-uk-2mg&qty=$qty"), qty)
        }
        // A plus sign is a space in a link, so it takes %2B to send one.
        assertEquals(1, valid("lasthit://log?product=nicorette-gum-uk-2mg&qty=+1").quantity)
        assertEquals(BadQuantity("+1"), refused("lasthit://log?product=nicorette-gum-uk-2mg&qty=%2B1"))
    }

    @Test
    fun `products that aren't one of yours, or could be several`() {
        assertEquals(MissingProduct, refused("lasthit://log"))
        assertEquals(MissingProduct, refused("lasthit://log?product=&qty=1"))
        assertEquals(MissingProduct, refused("lasthit://log?qty=1"))
        assertEquals(UnknownProduct("nicorette-gum-6mg"), refused("lasthit://log?product=nicorette-gum-6mg"))
        assertEquals(UnknownProduct("niquitin-clear-patch-uk-21mg"), refused("lasthit://log?product=niquitin-clear-patch-uk-21mg"))
        assertEquals(AmbiguousProduct("nicorette gum", listOf(gum2, gum4)), refused("lasthit://log?product=nicorette+gum"))
    }

    @Test
    fun `the time is read in the phone's zone`() {
        val link = "lasthit://log?product=nicorette-gum-uk-2mg&at=2026-10-23T15:00"
        assertEquals(Instant.parse("2026-10-23T14:00:00Z"), valid(link).at)
        assertEquals(
            Instant.parse("2026-10-23T19:00:00Z"),
            valid(link, at = Instant.parse("2026-10-23T20:00:00Z"), zone = ZoneId.of("America/New_York")).at,
        )
        assertEquals(Instant.parse("2026-10-23T14:00:30Z"), valid("$link:30").at)
        assertEquals(Instant.parse("2026-10-23T14:00:00Z"), valid("lasthit://log?product=nicorette-gum-uk-2mg&at=2026-10-23%2015:00").at)
    }

    @Test
    fun `clock changes`() {
        val later = Instant.parse("2027-04-01T12:00:00Z")
        // 01:30 on 28 March 2027 doesn't happen in London, as the clocks go forward an hour at 01:00,
        // so it's read as 02:30 BST.
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), valid("lasthit://log?product=gum-2&at=2027-03-28T01:30", at = later).at)
        // 01:30 on 25 October 2026 happens twice: the first, in BST, is taken.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), valid("lasthit://log?product=gum-2&at=2026-10-25T01:30", at = later).at)
    }

    @Test
    fun `a time in the future is refused, after a few minutes' leeway`() {
        assertEquals(Instant.parse("2026-10-23T14:10:00Z"), valid("lasthit://log?product=gum-2&at=2026-10-23T15:10").at)
        assertEquals(
            TimeInFuture(LocalDateTime.parse("2026-10-23T15:11")),
            refused("lasthit://log?product=gum-2&at=2026-10-23T15:11"),
        )
        assertEquals(TimeInFuture(LocalDateTime.parse("2026-10-24T09:00")), refused("lasthit://log?product=gum-2&at=2026-10-24T09:00"))
    }

    @Test
    fun `times that can't be read`() {
        listOf("", "yesterday", "15:00", "2026-10-23", "2026-13-01T10:00", "2026-10-23T15:00Z", "23/10/2026 15:00").forEach { at ->
            assertEquals(BadTime(at), refused("lasthit://log?product=gum-2&at=$at"), at)
        }
    }

    @Test
    fun `tags by key or name`() {
        assertEquals(
            setOf("stressed", "after_meal", "custom_1a2b"),
            valid("lasthit://log?product=gum-2&tags=stressed,,After%20a%20meal,gaming,after_meal,").tagKeys,
        )
        assertEquals(emptySet<String>(), valid("lasthit://log?product=gum-2&tags=").tagKeys)
    }

    @Test
    fun `unknown tags are refused, every one named`() {
        assertEquals(UnknownTags(listOf("vaping", "after meals")), refused("lasthit://log?product=gum-2&tags=stressed,vaping,after+meals"))
    }

    private val ownTags = LogLinkParser(
        ProductMatcher(listOf(gum2), catalogue),
        TagMatcher(
            listOf(
                TagName("stressed"),
                TagName("custom_a", listOf("Gym")),
                TagName("custom_b", listOf("gym")),
                TagName("custom_c", listOf("Coffee, tea")),
            ),
        ),
    )

    @Test
    fun `a name two tags share is refused as ambiguous, not unknown`() {
        assertEquals(
            LogLinkResult.Invalid(AmbiguousTags(mapOf("Gym" to listOf("custom_a", "custom_b")))),
            ownTags.parse("lasthit://log?product=gum-2&tags=stressed,Gym", now, london),
        )
        // A tag that names nothing is the first problem to report.
        assertEquals(
            LogLinkResult.Invalid(UnknownTags(listOf("vaping"))),
            ownTags.parse("lasthit://log?product=gum-2&tags=gym,vaping", now, london),
        )
    }

    @Test
    fun `a comma sent as %2C stays inside a tag's name`() {
        assertEquals(
            setOf("custom_c", "stressed"),
            (ownTags.parse("lasthit://log?product=gum-2&tags=Coffee%2C%20tea,stressed", now, london) as LogLinkResult.Valid).request.tagKeys,
        )
        assertEquals(
            LogLinkResult.Invalid(UnknownTags(listOf("Coffee", "tea"))),
            ownTags.parse("lasthit://log?product=gum-2&tags=Coffee,%20tea", now, london),
        )
        assertEquals(LogLinkResult.Invalid(BadEncoding), ownTags.parse("lasthit://log?product=gum-2&tags=stressed,%ZZ", now, london))
    }

    @Test
    fun `links that aren't log links`() {
        listOf(
            "",
            "https://thelasthit.app/log?product=gum-2",
            "lasthit://open?product=gum-2",
            "lasthit://logs?product=gum-2",
            "lasthit://log/now?product=gum-2",
            "lasthit:log?product=gum-2",
            "lasthit//log?product=gum-2",
        ).forEach { assertEquals(NotALogLink, refused(it), it) }
    }

    @Test
    fun `parameters that are misspelt, repeated or badly encoded`() {
        assertEquals(UnknownParameter("quantity"), refused("lasthit://log?product=gum-2&quantity=2"))
        assertEquals(RepeatedParameter("qty"), refused("lasthit://log?product=gum-2&qty=1&qty=2"))
        assertEquals(RepeatedParameter("product"), refused("lasthit://log?product=gum-2&PRODUCT=gum-4"))
        assertEquals(BadEncoding, refused("lasthit://log?product=gum%2"))
        assertEquals(BadEncoding, refused("lasthit://log?product=gum%ZZ"))
        assertEquals(gum2, valid("lasthit://log?&product=gum-2&&").product)
    }
}
