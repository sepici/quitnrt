package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.programme.HP_PER_HIT
import app.thelasthit.nrt.domain.programme.MissedWeekChoice
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeClock
import app.thelasthit.nrt.domain.programme.ProgrammeWeek
import app.thelasthit.nrt.domain.programme.plannedWeeks
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.sheetEdge
import app.thelasthit.nrt.ui.components.sheetScrim
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch

/**
 * The choice after a missed week, as it comes in: carry on (picked), or repeat the week, with
 * what that costs in HP shown before it's confirmed. Closing the sheet counts as carrying on.
 * Nothing resets either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MissedWeekSheet(
    plan: Programme,
    missed: ProgrammeWeek,
    total: Dose,
    hp: Int,
    demonName: String?,
    clock: ProgrammeClock,
    initial: MissedWeekChoice,
    onChoose: (MissedWeekChoice) -> Unit,
) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val next = plan.weeks.getOrNull(missed.number) ?: return
    val canRepeat = plan.canRepeat(missed.number, clock)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picked by rememberSaveable(missed.number) { mutableStateOf(if (canRepeat) initial else MissedWeekChoice.CARRY_ON) }
    val name = demonName ?: stringResource(R.string.demon_default_name)

    fun close(choice: MissedWeekChoice) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onChoose(choice) }
    }

    ModalBottomSheet(
        onDismissRequest = { onChoose(MissedWeekChoice.CARRY_ON) },
        sheetState = sheetState,
        containerColor = colors.surface,
        scrimColor = sheetScrim,
        modifier = Modifier.sheetEdge(colors),
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val allowance = missed.target.weeklyAllowance
                SheetHeader(
                    title = stringResource(R.string.plan_missed_title),
                    subtitle = stringResource(R.string.plan_missed_detail, missed.number, total.mgText(), total.apartFrom(allowance).mgText(), allowance.mgText()),
                    closeDescription = stringResource(R.string.plan_close_carry_on),
                    onClose = { close(MissedWeekChoice.CARRY_ON) },
                )
                Text(
                    stringResource(R.string.plan_missed_back, next.number),
                    style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.text,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.line, shape)
                        .selectableGroup()
                        .semantics { contentDescription = resources.getString(R.string.plan_how_week_goes, next.number) },
                ) {
                    val carryOn = if (next.isNrtFree) {
                        stringResource(R.string.plan_carry_on_free_detail, next.number)
                    } else {
                        stringResource(
                            R.string.plan_carry_on_detail,
                            next.number,
                            next.target.weeklyAllowance.mgText(),
                            stringResource(R.string.plan_mix_a_day, mixText(next.target.mix)),
                        )
                    }
                    ChoiceOption(stringResource(R.string.plan_carry_on), picked == MissedWeekChoice.CARRY_ON, onPick = { picked = MissedWeekChoice.CARRY_ON }) {
                        OptionDetail(carryOn)
                    }
                    if (canRepeat) {
                        HorizontalDivider(color = colors.line)
                        val repeated = plan.repeat(missed.number, clock)
                        val end = LongDate.format(repeated.weeks.last().endDate)
                        val repeat = if (missed.isNrtFree) {
                            stringResource(R.string.plan_repeat_free_detail, next.number, repeated.plannedWeeks, end)
                        } else {
                            stringResource(
                                R.string.plan_repeat_detail,
                                next.number,
                                allowance.mgText(),
                                stringResource(R.string.plan_mix_a_day, mixText(missed.target.mix)),
                                repeated.plannedWeeks,
                                end,
                            )
                        }
                        ChoiceOption(stringResource(R.string.plan_repeat), picked == MissedWeekChoice.REPEAT, onPick = { picked = MissedWeekChoice.REPEAT }) {
                            OptionDetail(repeat)
                            HpCost(hp = hp, maxHp = plan.maxHp)
                            OptionDetail(stringResource(R.string.plan_repeat_hp, name), Modifier.padding(top = 6.dp))
                        }
                    }
                }
                Text(
                    stringResource(R.string.plan_missed_small),
                    style = LastHit.type.label,
                    color = colors.text2,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                )
            }
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 20.dp))
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
                PrimaryButton(onClick = { close(picked) }) { content ->
                    Text(
                        stringResource(if (picked == MissedWeekChoice.REPEAT) R.string.plan_repeat else R.string.plan_carry_on),
                        style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        color = content,
                    )
                }
            }
        }
    }
}

/** One of the two ways on, with the radio on the right. */
@Composable
private fun ChoiceOption(title: String, selected: Boolean, onPick: () -> Unit, detail: @Composable () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onPick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            Column(Modifier.padding(top = 2.dp)) { detail() }
        }
        Radio(selected)
    }
}

@Composable
private fun OptionDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = LastHit.colors.text2, modifier = modifier)
}

/** What a repeat costs: the demon's HP with the 10 a repeat adds, ringed in marigold. */
@Composable
private fun HpCost(hp: Int, maxHp: Int) {
    val colors = LastHit.colors
    val after = hp + HP_PER_HIT
    val afterMax = maxHp + HP_PER_HIT
    val description = stringResource(R.string.plan_hp_cost_description, after, afterMax)
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(colors.stageSky)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HpBlocks(
            blocks = afterMax / HP_PER_HIT,
            full = after / HP_PER_HIT,
            added = hp / HP_PER_HIT,
            height = 10.dp,
            gap = 2.dp,
            edge = 2.dp,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.plan_hp_value, after, afterMax),
                style = LastHit.type.pixel.copy(fontSize = 16.sp, lineHeight = 16.sp),
                color = colors.stageText,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.demon_hp),
                style = LastHit.type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.stageText2,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
