package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.sheetEdge
import app.thelasthit.nrt.ui.components.sheetScrim
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Changing a week before it starts: every step it can take, from the week before's down to the
 * bottom of the ladder, as mixes in a list that scrolls on its own. Under it, what the change
 * does to the week's allowance, and the later weeks that come down with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditWeekSheet(plan: Programme, number: Int, today: LocalDate, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val range = plan.editableSteps(number, today) ?: return
    val week = plan.week(number)
    val planned = week.target.step ?: return
    val colors = LastHit.colors
    val resources = LocalResources.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picked by rememberSaveable(number) { mutableIntStateOf(planned) }
    if (picked !in range) picked = planned
    val edited = plan.edit(number, picked, today)
    val down = edited.weeks.filter { it.number > number && it.target != plan.week(it.number).target }
    val lastTaper = plan.weeks.last { !it.isNrtFree }.number
    val after = (down.lastOrNull()?.number ?: number) + 1
    val rest = if (after <= lastTaper) after..lastTaper else null

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        scrimColor = sheetScrim,
        modifier = Modifier.sheetEdge(colors),
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            SheetHeader(
                title = stringResource(R.string.plan_week_n, number),
                subtitle = resources.longRange(week.startDate, week.endDate),
                closeDescription = stringResource(R.string.action_close),
                onClose = { close(onDismiss) },
            )
            Column(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Text(
                    stringResource(R.string.plan_daily_mix),
                    style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.text2,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                val steps = range.toList()
                val density = LocalDensity.current
                val list = rememberLazyListState(
                    initialFirstVisibleItemIndex = (steps.indexOf(planned) - 1).coerceAtLeast(0),
                    initialFirstVisibleItemScrollOffset = if (steps.indexOf(planned) > 0) with(density) { 34.dp.roundToPx() } else 0,
                )
                val shape = RoundedCornerShape(16.dp)
                LazyColumn(
                    state = list,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.line, shape)
                        .selectableGroup()
                        .semantics { contentDescription = resources.getString(R.string.plan_daily_mix_for, number) },
                ) {
                    itemsIndexed(steps) { index, step ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        val mix = plan.ladder.mixAt(step)
                        val mg = mix.total.mgText()
                        StepOption(
                            title = resources.mixText(mix),
                            detail = if (step == planned) stringResource(R.string.plan_as_planned, mg) else stringResource(R.string.plan_mg_a_day_value, mg),
                            selected = step == picked,
                            onPick = { picked = step },
                        )
                    }
                }
                if (picked == range.first) {
                    val before = plan.weeks.getOrNull(number - 2)
                    val cap = when {
                        before != null -> {
                            val mix = resources.mixText(before.target.mix)
                            boldIn(stringResource(R.string.plan_cap_week, number, before.number, mix), mix)
                        }
                        plan.ladder.top > 0 -> boldIn(stringResource(R.string.plan_cap_first_label), "")
                        else -> boldIn(stringResource(R.string.plan_cap_first), "")
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
                        Meta(cap)
                    }
                }
                val now = edited.week(number).target.weeklyAllowance
                val was = week.target.weeklyAllowance
                val forWeek = stringResource(R.string.plan_mg_for_week, now.mgText())
                val change = when {
                    now > was -> stringResource(R.string.plan_now_up, forWeek, was.mgText())
                    now < was -> stringResource(R.string.plan_now_down, forWeek, was.mgText())
                    else -> stringResource(R.string.plan_now_same, forWeek)
                }
                val restLine = rest?.let { restText(it) }
                val ruled = picked == range.first
                Column(Modifier.padding(top = if (ruled) 16.dp else 14.dp)) {
                    if (ruled) HorizontalDivider(color = colors.line, modifier = Modifier.padding(bottom = 14.dp))
                    val line = if (down.isEmpty() && restLine != null) "$change $restLine" else change
                    Meta(boldIn(line, forWeek))
                }
                if (down.isNotEmpty()) {
                    Knock(picked, plan, down.map { it.number }, restLine)
                }
            }
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 20.dp))
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
                val label = if (down.isEmpty()) {
                    stringResource(R.string.plan_save_week, number)
                } else {
                    stringResource(R.string.plan_save_weeks, number, down.last().number)
                }
                PrimaryButton(onClick = { close { onSave(picked) } }) { content ->
                    Text(label, style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = content)
                }
            }
        }
    }
}

@Composable
private fun restText(rest: IntRange): String = if (rest.first == rest.last) {
    stringResource(R.string.plan_rest_one, rest.first)
} else {
    stringResource(R.string.plan_rest_many, rest.first, rest.last)
}

/** The later weeks a lower week brings down with it, each with its guide before and after. */
@Composable
private fun Knock(step: Int, plan: Programme, weeks: List<Int>, rest: String?) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val mix = resources.mixText(plan.ladder.mixAt(step))
    val newGuide = plan.ladder.mixAt(step).total
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
            val text = if (weeks.size == 1) {
                stringResource(R.string.plan_knock_one, weeks.single(), mix)
            } else {
                stringResource(R.string.plan_knock_many, resources.weeksText(weeks), mix)
            }
            Text(boldIn(text, mix), style = LastHit.type.label, color = colors.text)
        }
        Column(Modifier.padding(start = 30.dp, top = 10.dp)) {
            weeks.forEach { other ->
                HorizontalDivider(color = colors.line)
                val newMg = resources.mg(newGuide)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.plan_week_n, other), style = LastHit.type.label, color = colors.text2)
                    Text(
                        boldIn(stringResource(R.string.plan_knock_row, plan.week(other).target.dailyGuide.mgText(), newMg), newMg),
                        style = LastHit.type.label,
                        color = colors.text2,
                    )
                }
            }
            if (rest != null) {
                HorizontalDivider(color = colors.line)
                Text(rest, style = LastHit.type.caption, color = colors.text2, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** One step the week can take: its mix and mg a day, with the radio on the right. The chosen one sits on the sunk tint. */
@Composable
private fun StepOption(title: String, detail: String, selected: Boolean, onPick: () -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.sunk else colors.surface)
            .selectable(selected, role = Role.RadioButton, onClick = onPick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold), color = colors.text)
            Text(detail, style = LastHit.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.text2, modifier = Modifier.padding(top = 1.dp))
        }
        Radio(selected)
    }
}

@Composable
internal fun Radio(selected: Boolean) {
    val colors = LastHit.colors
    Box(
        Modifier
            .size(24.dp)
            .border(2.dp, if (selected) colors.chipSelected else colors.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(colors.chipSelected))
    }
}

/** A sheet's title and line under it, with the close button beside the title. */
@Composable
internal fun SheetHeader(title: String, subtitle: String?, closeDescription: String, onClose: () -> Unit) {
    val titleStyle = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em)
    val titleLine = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(end = 48.dp)) {
            Text(title, style = titleStyle, color = LastHit.colors.text)
            if (subtitle != null) Text(subtitle, style = LastHit.type.label, color = LastHit.colors.text2)
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (titleLine - 48.dp) / 2)
                .size(48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = closeDescription, tint = LastHit.colors.text)
        }
    }
}
