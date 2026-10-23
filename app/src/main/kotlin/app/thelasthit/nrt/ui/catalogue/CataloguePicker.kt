package app.thelasthit.nrt.ui.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.components.SwitchRow
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.catalogue.CatalogueLine
import app.thelasthit.nrt.domain.catalogue.CatalogueProduct
import app.thelasthit.nrt.domain.catalogue.LineKind
import app.thelasthit.nrt.domain.catalogue.ProductStatus
import app.thelasthit.nrt.domain.product.word
import app.thelasthit.nrt.ui.components.Chip
import app.thelasthit.nrt.ui.components.ChipKind
import app.thelasthit.nrt.ui.components.IconTile
import app.thelasthit.nrt.ui.format.countryName
import app.thelasthit.nrt.ui.format.countryShort
import app.thelasthit.nrt.ui.format.formDetail
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.theme.LastHit

/** What the catalogue picker shows: the brands, the chosen brand's lines, or search results. */
data class CatalogueBrowse(
    val brands: List<String>,
    val brand: String?,
    val lines: List<CatalogueLine>,
    /** Search results, or null when the search box is empty. */
    val results: List<CatalogueProduct>?,
)

/**
 * The catalogue as [country] sees it, or every country's with [otherCountries]. With a [query]
 * it's search results: local and generic products only, unless other countries are shown.
 */
fun Catalogue.browseFor(country: String, otherCountries: Boolean, brand: String?, query: String): CatalogueBrowse {
    val browsable = browse(country, otherCountries)
    val brands = browsable.map { it.brand }.distinct()
    val chosen = brand?.takeIf { it in brands } ?: brands.firstOrNull()
    val results = query.takeIf { it.isNotBlank() }?.let { text ->
        search(text, country).filter { product ->
            otherCountries || product.markets.isEmpty() || country.uppercase() in product.markets
        }
    }
    return CatalogueBrowse(brands, chosen, CatalogueLine.group(browsable.filter { it.brand == chosen }), results)
}

/**
 * Search, brand chips and each line's strengths, or search results, then "Show other countries".
 * [picked] marks products already chosen. [order] gives a picked product's place on the quick log
 * to show as a number; without one, a picked product shows a tick.
 */
@Composable
fun CataloguePicker(
    browse: CatalogueBrowse,
    country: String,
    query: String,
    otherCountries: Boolean,
    picked: (String) -> Boolean,
    order: (String) -> Int?,
    onToggle: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBrand: (String) -> Unit,
    onOtherCountries: (Boolean) -> Unit,
) {
    SearchField(query, onChange = onSearch)
    val results = browse.results
    if (results == null) {
        BrandChips(browse.brands, selected = browse.brand, onPick = onBrand)
        browse.brand?.let { brand ->
            ListHeader(brandName(brand), pluralStringResource(R.plurals.onboarding_product_count, browse.lines.size, browse.lines.size))
        }
        browse.lines.forEachIndexed { index, line ->
            LineRow(line, country, first = index == 0, picked = picked, order = order, onToggle = onToggle)
        }
    } else {
        ListHeader(
            pluralStringResource(R.plurals.onboarding_match_count, results.size, results.size),
            if (otherCountries) null else stringResource(countryName(country)),
        )
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.onboarding_no_matches),
                style = LastHit.type.body,
                color = LastHit.colors.text2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        results.forEachIndexed { index, product ->
            ResultRow(product, first = index == 0, picked = picked(product.id), order = order(product.id), onToggle = onToggle)
        }
    }
    Box(Modifier.padding(top = 16.dp)) {
        SwitchRow(
            title = stringResource(R.string.onboarding_other_countries),
            detail = stringResource(R.string.onboarding_other_countries_detail),
            checked = otherCountries,
            onChange = onOtherCountries,
        )
    }
}

@Composable
private fun brandName(brand: String): String = if (brand == GENERIC_BRAND) stringResource(R.string.onboarding_any_brand) else brand

private const val GENERIC_BRAND = "Generic"

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = LastHit.colors
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.onboarding_search_hint), maxLines = 1) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.text2) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.onboarding_clear_search), tint = colors.text2)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = LastHit.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.text,
            unfocusedBorderColor = colors.lineStrong,
            focusedPlaceholderColor = colors.text3,
            unfocusedPlaceholderColor = colors.text3,
            cursorColor = colors.text,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

@Composable
private fun BrandChips(brands: List<String>, selected: String?, onPick: (String) -> Unit) {
    Row(
        Modifier
            .padding(top = 16.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        brands.forEach { brand ->
            Chip(brandName(brand), selected = brand == selected, kind = ChipKind.Choice, onClick = { onPick(brand) })
        }
    }
}

@Composable
private fun ListHeader(title: String, detail: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = LastHit.type.headline.copy(fontSize = 20.sp, lineHeight = 26.sp), color = LastHit.colors.text, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = LastHit.type.label, color = LastHit.colors.text2)
    }
}

/** How a line is counted, and that every flavour logs the same or that it's another country's pack. */
@Composable
private fun lineDetail(product: CatalogueProduct, country: String): String {
    var detail = stringResource(formDetail(product.form))
    if (product.flavours.size > 1) detail = stringResource(R.string.onboarding_every_flavour, detail)
    val foreign = product.markets.isNotEmpty() && country.uppercase() !in product.markets
    if (foreign) {
        product.markets.firstNotNullOfOrNull { countryShort(it) }?.let { detail = stringResource(R.string.onboarding_foreign_pack, detail, stringResource(it)) }
    }
    return detail
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineRow(
    line: CatalogueLine,
    country: String,
    first: Boolean,
    picked: (String) -> Boolean,
    order: (String) -> Int?,
    onToggle: (String) -> Unit,
) {
    val colors = LastHit.colors
    val product = line.first
    Column {
        if (!first) HorizontalDivider(color = colors.line)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(product.form.icon, size = 40.dp)
                Column {
                    Text(
                        if (product.kind == LineKind.GENERIC) product.shortName else product.line,
                        style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.text,
                    )
                    Text(lineDetail(product, country), style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
                }
            }
            FlowRow(
                Modifier.padding(start = 54.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                line.products.forEach { strength ->
                    StrengthChip(strength, picked = picked(strength.id), order = order(strength.id), onToggle = onToggle)
                }
            }
        }
    }
}

/** A strength to pick. Once picked it shows its place on the quick log, or a tick. */
@Composable
private fun StrengthChip(product: CatalogueProduct, picked: Boolean, order: Int?, onToggle: (String) -> Unit) {
    val colors = LastHit.colors
    val name = stringResource(R.string.dose_named, product.shortName, product.strength.mgText())
    val description = if (picked && order != null) stringResource(R.string.onboarding_pick_description, name, order) else name
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (picked) colors.chipSelected else colors.sunk)
            .toggleable(picked, role = Role.Checkbox, onValueChange = { onToggle(product.id) })
            .semantics { contentDescription = description }
            .padding(start = if (picked) 10.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (picked) PickedMark(order, background = colors.onChipSelected, content = colors.chipSelected)
        Text(
            stringResource(R.string.dose_mg, product.strength.mgText()),
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = if (picked) colors.onChipSelected else colors.text,
        )
    }
}

/** A picked product's place on the quick log, or a tick when it isn't numbered. */
@Composable
private fun PickedMark(order: Int?, background: Color, content: Color) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        if (order != null) {
            Text(order.toString(), style = LastHit.type.caption.copy(fontWeight = FontWeight.ExtraBold), color = content)
        } else {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
    }
}

/** A number in a circle: a product's place on the quick log. */
@Composable
fun OrderBadge(order: Int, background: Color, content: Color) = PickedMark(order, background, content)

@Composable
private fun ResultRow(product: CatalogueProduct, first: Boolean, picked: Boolean, order: Int?, onToggle: (String) -> Unit) {
    val colors = LastHit.colors
    val detail = when {
        product.status == ProductStatus.DISCONTINUED -> stringResource(R.string.onboarding_discontinued)
        else -> product.form.word?.let { stringResource(it.text) } ?: stringResource(formDetail(product.form))
    }
    Column {
        if (!first) HorizontalDivider(color = colors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(product.form.icon, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dose_named, product.shortName, product.strength.mgText()),
                    style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.text,
                )
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
            val name = stringResource(R.string.dose_named, product.shortName, product.strength.mgText())
            val description = when {
                picked && order != null -> stringResource(R.string.onboarding_pick_description, name, order)
                picked -> stringResource(R.string.onboarding_added_description, name)
                else -> stringResource(R.string.onboarding_add_description, name)
            }
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (picked) Modifier.background(colors.chipSelected) else Modifier.border(1.5.dp, colors.lineStrong, RoundedCornerShape(22.dp)))
                    .toggleable(picked, role = Role.Checkbox, onValueChange = { onToggle(product.id) })
                    .semantics { contentDescription = description }
                    .padding(start = if (picked) 8.dp else 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (picked) {
                    PickedMark(order, background = colors.onChipSelected, content = colors.chipSelected)
                } else {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(if (picked) R.string.onboarding_added else R.string.onboarding_add),
                    style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (picked) colors.onChipSelected else colors.text,
                )
            }
        }
    }
}

