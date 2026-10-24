package app.thelasthit.nrt.ui.absorbed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.absorbed.Absorption
import app.thelasthit.nrt.domain.absorbed.FormFactor
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.ui.components.SwitchRow
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.settings.Intro
import app.thelasthit.nrt.ui.settings.RowList
import app.thelasthit.nrt.ui.settings.SectionHeading
import app.thelasthit.nrt.ui.settings.SettingsPage
import app.thelasthit.nrt.ui.settings.SettingsRow
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

class AbsorbedViewModel(private val graph: DataGraph) : ViewModel() {
    /** Null until the setting is read. */
    val on: StateFlow<Boolean?> = graph.settings.settings.map<_, Boolean?> { it.showAbsorbed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOn(on: Boolean) {
        viewModelScope.launch { graph.settings.setShowAbsorbed(on) }
    }
}

/** Settings' row for the estimate, with whether it's on. It opens the page that explains it. */
@Composable
fun AbsorbedSection(onOpen: () -> Unit, viewModel: AbsorbedViewModel = graphViewModel { AbsorbedViewModel(it) }) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val shown = on ?: return
    SectionHeading(stringResource(R.string.absorbed_settings_heading))
    RowList {
        SettingsRow(
            stringResource(R.string.absorbed_settings_row),
            stringResource(R.string.absorbed_settings_row_detail),
            first = true,
            onClick = onOpen,
            value = stringResource(if (shown) R.string.absorbed_on else R.string.absorbed_off),
            chevron = true,
        )
    }
}

/**
 * One line of the shares: the forms it covers share a figure, and the first one's is shown. The
 * bitartrate lozenges are the catalogue lines with a figure of their own.
 */
private enum class ShareRow(@StringRes val label: Int, private val form: ProductForm?) {
    GUM(R.string.absorbed_form_gum, ProductForm.GUM),
    LOZENGE(R.string.absorbed_form_lozenge, ProductForm.LOZENGE),
    BITARTRATE_LOZENGE(R.string.absorbed_form_bitartrate_lozenge, null),
    MOUTH_SPRAY(R.string.absorbed_form_mouth_spray, ProductForm.MOUTH_SPRAY),
    NASAL_SPRAY(R.string.absorbed_form_nasal_spray, ProductForm.NASAL_SPRAY),
    INHALATOR(R.string.absorbed_form_inhalator, ProductForm.INHALATOR),
    MICROTAB(R.string.absorbed_form_microtab, ProductForm.MICROTAB),
    STRIP(R.string.absorbed_form_strip, ProductForm.ORAL_FILM),
    POUCH(R.string.absorbed_form_pouch, ProductForm.POUCH),
    PATCH(R.string.absorbed_form_patch, ProductForm.PATCH_24H),
    ;

    val figure: FormFactor get() = form?.let(Absorption.byForm::getValue) ?: Absorption.bitartrateLozenge
}

/**
 * What the estimate is, the switch, each form's rough share and where the shares come from. The
 * switch sits under the explanation's first line, so it's read before it's turned on.
 */
@Composable
fun AbsorbedScreen(onBack: () -> Unit, viewModel: AbsorbedViewModel = graphViewModel { AbsorbedViewModel(it) }) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val colors = LastHit.colors
    SettingsPage(stringResource(R.string.absorbed_title), stringResource(R.string.settings_back), onBack) {
        Intro(stringResource(R.string.absorbed_intro))
        Box(Modifier.padding(top = 20.dp)) {
            on?.let { checked ->
                SwitchRow(
                    title = stringResource(R.string.absorbed_switch),
                    detail = stringResource(R.string.absorbed_switch_detail),
                    checked = checked,
                    onChange = viewModel::setOn,
                )
            }
        }

        SectionHeading(stringResource(R.string.absorbed_how_heading))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(R.string.absorbed_how, R.string.absorbed_varies, R.string.absorbed_labelled, R.string.absorbed_not_advice).forEach {
                Text(stringResource(it), style = LastHit.type.body, color = colors.text)
            }
        }

        SectionHeading(stringResource(R.string.absorbed_shares_heading))
        RowList {
            ShareRow.entries.forEachIndexed { index, row ->
                val factor = row.figure.factor
                if (index > 0) HorizontalDivider(color = colors.line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(row.label), style = LastHit.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.absorbed_percent, factor.percent),
                        style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text2,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.absorbed_form_other),
            style = LastHit.type.caption,
            color = colors.text2,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )

        SectionHeading(stringResource(R.string.absorbed_sources_heading))
        // The full citations, with their links, are in Absorption, for checking.
        Text(
            stringResource(R.string.absorbed_sources, Absorption.checked.format(CHECKED)),
            style = LastHit.type.body,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

private val CHECKED: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
