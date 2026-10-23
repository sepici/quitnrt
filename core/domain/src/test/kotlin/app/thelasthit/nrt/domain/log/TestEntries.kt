package app.thelasthit.nrt.domain.log

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal val LONDON: ZoneId = ZoneId.of("Europe/London")

internal fun london(text: String): Instant = LocalDateTime.parse(text).atZone(LONDON).toInstant()

internal fun entry(
    at: String,
    productId: String = "nicorette-gum-uk-2mg",
    form: ProductForm = ProductForm.GUM,
    perUnit: Dose = Dose.mg(2),
    quantity: Int = 1,
    source: EntrySource = EntrySource.APP,
): Entry {
    val instant = london(at)
    return Entry(
        id = UUID.randomUUID().toString(),
        productId = productId,
        productName = productId,
        form = form,
        unit = form.defaultUnit,
        perUnit = perUnit,
        quantity = quantity,
        total = perUnit * quantity,
        occurredAt = instant,
        zone = LONDON,
        logicalDate = DayBoundary().logicalDate(instant, LONDON),
        source = source,
    )
}
