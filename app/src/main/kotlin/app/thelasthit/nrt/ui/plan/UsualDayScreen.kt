package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.domain.programme.BaselineDays
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.ui.components.AppSheet
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * Your usual day: the one the logs measured, to confirm or change, or one typed in. Each product
 * has a stepper of whole units, and "Add a product" brings in another of the user's own.
 */
@Composable
fun UsualDayScreen(
    mode: UsualDayMode,
    onBack: () -> Unit,
    onKept: () -> Unit,
    onOpenCatalogue: () -> Unit,
    viewModel: UsualDayViewModel = graphViewModel(key = "usual-day-$mode") { UsualDayViewModel(it, mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().background(LastHit.colors.bg))
        return
    }
    val resources = LocalResources.current
    val measured = current.mode == UsualDayMode.MEASURED && current.days != null
    PlanPage(
        title = stringResource(R.string.plan_usual_title),
        onBack = onBack,
        footer = {
            MainButton(stringResource(R.string.plan_use_usual), onClick = { viewModel.keep(onKept) }, enabled = !current.mix.isEmpty)
            FootNote(stringResource(R.string.plan_next_length_after))
        },
    ) {
        if (!measured) PageIntro(stringResource(R.string.plan_usual_intro))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (measured) 4.dp else 16.dp)) {
            Fig(current.total.mgText(), stringResource(R.string.plan_mg_a_day))
            val days = current.days
            if (measured && days != null) {
                Meta(stringResource(R.string.plan_from_logs, resources.longRange(days.start, days.end)), Modifier.padding(top = 4.dp))
            }
            UnitList(current, onStep = viewModel::step, onAdd = { adding = true }, modifier = Modifier.padding(top = 18.dp))
            if (!measured) Hint(stringResource(R.string.plan_typical_hint), Modifier.padding(top = 14.dp))
        }
        val days = current.days
        val usual = current.measured
        if (measured && days != null && usual != null) LogsShow(days, usual)
    }

    if (adding) {
        AddProductSheet(
            products = current.others,
            onPick = {
                viewModel.add(it)
                adding = false
            },
            onCatalogue = {
                adding = false
                onOpenCatalogue()
            },
            onDismiss = { adding = false },
        )
    }
}

/** The products, each with whole units a day and a stepper, then "Add a product". */
@Composable
private fun UnitList(state: UsualDayState, onStep: (String, Int) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.line)
        state.rows.forEach { row ->
            UnitRowView(row, onStep)
            HorizontalDivider(color = colors.line)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).border(1.5.dp, colors.line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = colors.link, modifier = Modifier.size(22.dp))
            }
            Text(stringResource(R.string.plan_add_product), style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.link)
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
private fun UnitRowView(row: UnitRow, onStep: (String, Int) -> Unit) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val product = row.product
    val off = row.units == 0
    val title = resources.productTitle(product)
    val unit = stringResource(unitWord(product.unit))
    val count = pluralStringResource(product.unit.plural, row.units, row.units)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.sunk), contentAlignment = Alignment.Center) {
            Icon(painterResource(product.form.icon), contentDescription = null, tint = if (off) colors.text3 else colors.text2, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = if (off) colors.text2 else colors.text)
            Text(
                if (off) stringResource(R.string.plan_not_in_usual) else stringResource(R.string.plan_units_detail, count, row.total.mgText()),
                style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
                color = colors.text2,
            )
        }
        val group = stringResource(R.string.plan_units_a_day_description, title, count)
        Row(
            Modifier.semantics(mergeDescendants = false) { contentDescription = group },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StepButton(R.drawable.ic_minus, stringResource(R.string.plan_one_fewer, unit), enabled = !off, onClick = { onStep(product.id, -1) })
            Text(
                row.units.toString(),
                style = LastHit.type.title.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.018).em),
                color = if (off) colors.text3 else colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 34.dp).clearAndSetSemantics {},
            )
            StepButton(
                R.drawable.ic_plus,
                stringResource(R.string.plan_one_more, unit),
                enabled = row.units < UsualDayViewModel.MAX_UNITS,
                onClick = { onStep(product.id, 1) },
            )
        }
    }
}

/** What the logs showed: the seven days, the average and why a day with nothing logged doesn't count. */
@Composable
private fun LogsShow(days: BaselineDays, usual: UsualDay) {
    val resources = LocalResources.current
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        Kicker(stringResource(R.string.plan_logs_show))
        val columns = days.columns(today = null)
        PlanColumns(columns, guide = null, description = resources.columnsText(columns), modifier = Modifier.padding(top = 34.dp))
        val nothing = days.days.filter { it.isOver && it.doses == 0 }
        PlanKey(
            buildList {
                add(KeyItem.Used(stringResource(R.string.plan_key_used)))
                if (nothing.isNotEmpty()) add(KeyItem.Nothing(stringResource(R.string.plan_key_nothing)))
            },
            Modifier.padding(top = 14.dp),
        )
        val average = usual.measured ?: days.averageSoFar
        if (average != null) {
            val value = resources.averageMg(average)
            Meta(
                boldIn(stringResource(R.string.plan_logs_average, days.daysWithDoses, value, resources.mixText(usual.mix)), value, LastHit.colors.text),
                Modifier.padding(top = 18.dp),
            )
        }
        if (nothing.isNotEmpty()) {
            val title = if (nothing.size == 1) {
                stringResource(R.string.plan_why_one, nothing.single().date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK))
            } else {
                stringResource(R.string.plan_why_many)
            }
            PlanNote(title, listOf(stringResource(R.string.plan_why_body)), Modifier.padding(top = 20.dp))
        }
    }
}

/** The user's other products to add to the usual day, and a way to the catalogue for one not there yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProductSheet(products: List<UserProduct>, onPick: (String) -> Unit, onCatalogue: () -> Unit, onDismiss: () -> Unit) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SheetHeader(
                title = stringResource(R.string.plan_add_product),
                subtitle = stringResource(if (products.isEmpty()) R.string.plan_add_none else R.string.plan_add_intro),
                closeDescription = stringResource(R.string.action_close),
                onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            )
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = colors.line)
                products.forEach { product ->
                    AddRow(product.form.icon, resources.productTitle(product), null) { onPick(product.id) }
                    HorizontalDivider(color = colors.line)
                }
                AddRow(R.drawable.ic_search, stringResource(R.string.plan_add_catalogue), stringResource(R.string.plan_add_catalogue_detail), onCatalogue)
                HorizontalDivider(color = colors.line)
            }
        }
    }
}

@Composable
private fun AddRow(icon: Int, title: String, detail: String?, onClick: () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.sunk), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.text2, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            if (detail != null) Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        }
    }
}
