package app.thelasthit.nrt.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.catalogue.CataloguePicker
import app.thelasthit.nrt.ui.catalogue.OrderBadge
import app.thelasthit.nrt.ui.format.pillLabelText
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
        CataloguePicker(
            browse = state.browse,
            country = draft.country,
            query = draft.query,
            otherCountries = draft.otherCountries,
            picked = { it in draft.picked },
            order = { id -> draft.picked.indexOf(id).takeIf { it >= 0 }?.plus(1) },
            onToggle = viewModel::togglePick,
            onSearch = viewModel::search,
            onBrand = viewModel::pickBrand,
            onOtherCountries = viewModel::showOtherCountries,
        )
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
