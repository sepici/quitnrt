package app.thelasthit.nrt.data.db

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.product.LabelLimit
import app.thelasthit.nrt.domain.product.RollingLimit
import app.thelasthit.nrt.domain.product.UserProduct
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun ProductEntity.toDomain() = UserProduct(
    id = id,
    catalogueId = catalogueId,
    name = name,
    shortName = shortName,
    form = form,
    unit = unit,
    perUnit = Dose(perUnitMicrograms),
    label = LabelLimit(
        maxUnitsPerDay = labelMaxPerDay,
        window = if (labelWindowMax != null && labelWindowMinutes != null) {
            RollingLimit(labelWindowMax, Duration.ofMinutes(labelWindowMinutes))
        } else {
            null
        },
    ),
    cartridge = cartridgeMicrograms?.let(::Dose),
    puffsPerCartridge = puffsPerCartridge,
    quickLogPosition = quickLogPosition,
    isHidden = isHidden,
)

internal fun UserProduct.toEntity(createdAt: Long) = ProductEntity(
    id = id,
    catalogueId = catalogueId,
    name = name,
    shortName = shortName,
    form = form,
    unit = unit,
    perUnitMicrograms = perUnit.micrograms,
    cartridgeMicrograms = cartridge?.micrograms,
    puffsPerCartridge = puffsPerCartridge,
    labelMaxPerDay = label.maxUnitsPerDay,
    labelWindowMax = label.window?.maxUnits,
    labelWindowMinutes = label.window?.span?.toMinutes(),
    quickLogPosition = quickLogPosition,
    isHidden = isHidden,
    createdAt = createdAt,
)

internal fun EntryWithTags.toDomain() = Entry(
    id = entry.id,
    productId = entry.productId,
    productName = entry.productName,
    form = entry.form,
    unit = entry.unit,
    perUnit = Dose(entry.perUnitMicrograms),
    quantity = entry.quantity,
    total = Dose(entry.totalMicrograms),
    occurredAt = Instant.ofEpochMilli(entry.occurredAt),
    zone = ZoneId.of(entry.zoneId),
    logicalDate = LocalDate.ofEpochDay(entry.logicalEpochDay),
    kind = entry.kind,
    cravingStrength = entry.cravingStrength,
    tagKeys = tags.map { it.tagKey }.toSet(),
    note = entry.note,
    source = entry.source,
)

internal fun Entry.toEntity(createdAt: Long, updatedAt: Long) = EntryEntity(
    id = id,
    productId = productId,
    productName = productName,
    form = form,
    unit = unit,
    perUnitMicrograms = perUnit.micrograms,
    quantity = quantity,
    totalMicrograms = total.micrograms,
    occurredAt = occurredAt.toEpochMilli(),
    zoneId = zone.id,
    logicalEpochDay = logicalDate.toEpochDay(),
    kind = kind,
    cravingStrength = cravingStrength,
    note = note,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)
