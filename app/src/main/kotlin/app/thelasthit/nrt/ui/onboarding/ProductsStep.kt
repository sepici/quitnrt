package app.thelasthit.nrt.ui.onboarding

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

/**
 * Step 4: the products the user takes, picked by brand or by search. Picks go on the quick log
 * in the order they're made, which the tray above the button shows.
 */
@Composable
fun ProductsStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val draft = state.draft
    StepScaffold(
        step = OnboardingStep.PRODUCTS,
        onBack = viewModel::back,
        footer = {
            QuickLogTray(state)
            NextButton(stringResource(R.string.onboarding_next_demon), viewModel::next)
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_products_title), stringResource(R.string.onboarding_products_body))
        SearchField(draft.query, onChange = viewModel::search)
        val results = state.results
        if (results == null) {
            BrandChips(state.brands, selected = state.brand, onPick = viewModel::pickBrand)
            state.brand?.let { brand ->
                ListHeader(brandName(brand), pluralStringResource(R.plurals.onboarding_product_count, state.lines.size, state.lines.size))
            }
            state.lines.forEachIndexed { index, line ->
                LineRow(line, state, first = index == 0, onToggle = viewModel::togglePick)
            }
        } else {
            ListHeader(
                pluralStringResource(R.plurals.onboarding_match_count, results.size, results.size),
                if (draft.otherCountries) null else stringResource(countryName(draft.country)),
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
                ResultRow(product, state, first = index == 0, onToggle = viewModel::togglePick)
            }
        }
        Box(Modifier.padding(top = 16.dp)) {
            SwitchRow(
                title = stringResource(R.string.onboarding_other_countries),
                detail = stringResource(R.string.onboarding_other_countries_detail),
                checked = draft.otherCountries,
                onChange = viewModel::showOtherCountries,
            )
        }
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
private fun LineRow(line: CatalogueLine, state: OnboardingState, first: Boolean, onToggle: (String) -> Unit) {
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
                    Text(lineDetail(product, state.draft.country), style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
                }
            }
            FlowRow(
                Modifier.padding(start = 54.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                line.products.forEach { strength ->
                    StrengthChip(strength, order = state.draft.picked.indexOf(strength.id) + 1, onToggle = onToggle)
                }
            }
        }
    }
}

/** A strength to pick. Once picked it shows its place on the quick log. */
@Composable
private fun StrengthChip(product: CatalogueProduct, order: Int, onToggle: (String) -> Unit) {
    val colors = LastHit.colors
    val picked = order > 0
    val name = stringResource(R.string.dose_named, product.shortName, product.strength.mgText())
    val description = if (picked) stringResource(R.string.onboarding_pick_description, name, order) else name
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
        if (picked) OrderBadge(order, background = colors.onChipSelected, content = colors.chipSelected)
        Text(
            stringResource(R.string.dose_mg, product.strength.mgText()),
            style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = if (picked) colors.onChipSelected else colors.text,
        )
    }
}

@Composable
private fun OrderBadge(order: Int, background: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(order.toString(), style = LastHit.type.caption.copy(fontWeight = FontWeight.ExtraBold), color = content)
    }
}

@Composable
private fun ResultRow(product: CatalogueProduct, state: OnboardingState, first: Boolean, onToggle: (String) -> Unit) {
    val colors = LastHit.colors
    val order = state.draft.picked.indexOf(product.id) + 1
    val picked = order > 0
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
            val description = if (picked) stringResource(R.string.onboarding_pick_description, name, order) else stringResource(R.string.onboarding_add)
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
                    OrderBadge(order, background = colors.onChipSelected, content = colors.chipSelected)
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

/** The quick log as it will be, in pick order, above the step's button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickLogTray(state: OnboardingState) {
    val colors = LastHit.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.onboarding_tray_title),
                style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.text2,
                modifier = Modifier.weight(1f),
            )
            if (state.pickedProducts.isNotEmpty()) {
                val count = state.pickedProducts.size
                Text(pluralStringResource(R.plurals.onboarding_tray_count, count, count), style = LastHit.type.label, color = colors.text2)
            }
        }
        if (state.pickedProducts.isEmpty()) {
            Text(
                stringResource(R.string.onboarding_tray_empty),
                style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
                color = colors.text2,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.pickedProducts.forEachIndexed { index, product ->
                    val label = state.pickLabels[product.id] ?: return@forEachIndexed
                    Row(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.hero)
                            .padding(start = 6.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        OrderBadge(index + 1, background = colors.heroChip, content = colors.onHero)
                        Text(pillLabelText(label), style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold), color = colors.onHero)
                    }
                }
            }
        }
    }
}
