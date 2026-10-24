package app.thelasthit.nrt.ui.advice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.AdviceIndex
import app.thelasthit.nrt.domain.advice.Interactive
import app.thelasthit.nrt.domain.advice.Meal
import app.thelasthit.nrt.domain.advice.Quitline
import app.thelasthit.nrt.domain.advice.Shift
import app.thelasthit.nrt.domain.advice.Strategy
import app.thelasthit.nrt.domain.advice.TimingTip
import app.thelasthit.nrt.domain.advice.TriggerAdvice
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.format.text
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The full advice list (PLAN §9 and §15): someone's own top triggers first, from the last 14
 * days, then the 4 Ds, then every strategy by the kind of trigger it suits, and free help.
 * Under the top triggers and in the 4 Ds card, a strategy with a timer opens the timer. Every
 * other row opens the strategy's page, with its sources and a button for the timer.
 */
@Composable
fun AdviceScreen(
    onBack: () -> Unit,
    onStrategy: (String) -> Unit,
    onTimer: (Interactive) -> Unit,
    viewModel: AdviceViewModel = graphViewModel { AdviceViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A top trigger's row goes straight to its timer, as the 4 Ds card does.
    val timerOrPage: (Strategy) -> Unit = { strategy -> strategy.interactive?.let(onTimer) ?: onStrategy(strategy.key) }
    AdvicePage(stringResource(R.string.advice_title), onBack) {
        AdviceIntro(stringResource(R.string.advice_intro))
        val loaded = state ?: return@AdvicePage
        val time = rememberTimeFormatter()

        SectionTitle(stringResource(R.string.advice_top_title), stringResource(R.string.advice_top_window), top = 24.dp)
        if (loaded.personal.forTriggers.isEmpty()) {
            Text(
                stringResource(R.string.advice_top_empty),
                style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
                color = LastHit.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
        }
        loaded.personal.forTriggers.forEach { advice -> TriggerBlock(advice, loaded, time, timerOrPage) }

        FourDs(onStrategy, onTimer)

        SectionTitle(stringResource(R.string.advice_every_title), stringResource(R.string.advice_every_body))
        loaded.sections.forEach { section ->
            ListHeading(sectionName(section.group))
            Ruled {
                section.strategies.forEachIndexed { index, strategy ->
                    val tags = AdviceIndex.rowTags(strategy).map { tagName(loaded, it) }
                    StrategyRow(
                        title = strategy.title,
                        sub = AnnotatedString(tags.joinToString(", ")),
                        first = index == 0,
                        timer = strategy.interactive != null,
                        onClick = { onStrategy(strategy.key) },
                    )
                }
            }
        }

        FreeHelp(loaded.country, loaded.quitlines)
        Text(
            stringResource(R.string.advice_fineprint),
            style = LastHit.type.caption,
            color = LastHit.colors.text2,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        )
    }
}

/**
 * One top trigger: its name and share, then its strategies, with a timing tip where there is one.
 * The 4 Ds card comes next, so the 4 Ds and the strategies for any moment aren't rows here too.
 */
@Composable
private fun TriggerBlock(advice: TriggerAdvice, state: AdviceState, time: DateTimeFormatter, open: (Strategy) -> Unit) {
    val colors = LastHit.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            tagName(state, advice.trigger.key),
            style = LastHit.type.headline.copy(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em),
            color = colors.text,
            modifier = Modifier.weight(1f).alignByBaseline().semantics { heading() },
        )
        Text(
            stringResource(R.string.advice_share, (advice.trigger.share * 100).roundToInt()),
            style = LastHit.type.label,
            color = colors.text2,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Ruled {
        AdviceIndex.triggerRows(advice).forEachIndexed { index, match ->
            val strategy = match.strategy
            val tip = state.tipFor(advice.trigger, strategy)
            StrategyRow(
                title = strategy.title,
                sub = if (tip != null) fromLogs(tipShort(tip, time)) else AnnotatedString(strategy.summary),
                subColour = if (tip != null) colors.text else colors.text2,
                first = index == 0,
                timer = strategy.interactive != null,
                onClick = { open(strategy) },
            )
        }
    }
}

/** The 4 Ds, in a card: the two with timers open them, and the other two open the 4 Ds' page. */
@Composable
private fun FourDs(onStrategy: (String) -> Unit, onTimer: (Interactive) -> Unit) {
    AdviceCard(Modifier.padding(top = 36.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.advice_ds_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
                color = LastHit.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.advice_ds_body), style = LastHit.type.label, color = LastHit.colors.text2, modifier = Modifier.padding(top = 2.dp))
        }
        val rows = listOf(
            FourD(R.string.advice_ds_delay, R.string.advice_ds_delay_sub, R.drawable.ic_timer, timer = true) { onTimer(Interactive.DELAY_TIMER) },
            FourD(R.string.advice_ds_breaths, R.string.advice_ds_breaths_sub, R.drawable.ic_wind, timer = true) { onTimer(Interactive.BREATHING_TIMER) },
            FourD(R.string.advice_ds_water, R.string.advice_ds_water_sub, R.drawable.ic_drop, timer = false) { onStrategy(AdviceIndex.FOUR_DS) },
            FourD(R.string.advice_ds_else, R.string.advice_ds_else_sub, R.drawable.ic_swap, timer = false) { onStrategy(AdviceIndex.FOUR_DS) },
        )
        rows.forEachIndexed { index, row ->
            StrategyRow(
                title = stringResource(row.title),
                sub = AnnotatedString(stringResource(row.sub)),
                first = index == 0,
                timer = row.timer,
                lead = row.icon,
                startPadding = 16.dp,
                onClick = row.onClick,
            )
        }
    }
}

private class FourD(@StringRes val title: Int, @StringRes val sub: Int, val icon: Int, val timer: Boolean, val onClick: () -> Unit)

/** The free lines for the phone's country, each with a way to call or open it. */
@Composable
private fun FreeHelp(country: String?, quitlines: List<Quitline>) {
    val colors = LastHit.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 28.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.sunk)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Text(
            stringResource(R.string.advice_help_title),
            style = LastHit.type.headline.copy(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em),
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (country == "GB") R.string.advice_help_body_gb else R.string.advice_help_body),
            style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = colors.text,
            modifier = Modifier.padding(top = 6.dp),
        )
        quitlines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (colors.isDark) colors.bg else colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(if (line.phone != null) R.drawable.ic_phone else R.drawable.ic_globe), contentDescription = null, tint = colors.text)
                }
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    val detail = listOfNotNull(line.region, line.phone)
                    if (detail.isNotEmpty()) {
                        Text(
                            if (detail.size == 2) stringResource(R.string.advice_help_region, detail[0], detail[1]) else detail[0],
                            style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
                            color = colors.text2,
                        )
                    }
                }
                val phone = line.phone
                val web = line.web
                when {
                    phone != null -> AdviceLink(
                        stringResource(R.string.advice_help_call),
                        onClick = { context.open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) },
                        description = stringResource(R.string.advice_help_call_description, line.name),
                    )
                    web != null -> AdviceLink(
                        stringResource(R.string.advice_help_open),
                        onClick = { context.open(Intent(Intent.ACTION_VIEW, web.toUri())) },
                        description = stringResource(R.string.advice_help_open_description, line.name),
                    )
                }
            }
        }
    }
}

/** Hands a call or a page to the phone's own app. The app itself never goes online. */
private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app to dial or browse with, as on some tablets: nothing to do.
    }
}

/** A tag's name: the user's own, or the built-in one's. */
@Composable
internal fun tagName(state: AdviceState, key: String): String = state.tags[key]?.let { tagText(it) } ?: tagText(key)

@StringRes
internal fun Meal.word(): Int = when (this) {
    Meal.BREAKFAST -> R.string.advice_meal_breakfast
    Meal.LUNCH -> R.string.advice_meal_lunch
    Meal.DINNER -> R.string.advice_meal_dinner
}

/** A timing tip in a row: "try 12:30, before lunch", from the slot with the most doses. */
@Composable
internal fun tipShort(tip: TimingTip, time: DateTimeFormatter): String {
    val lead = tip.lead
    val at = time.format(lead.suggested)
    return when {
        tip.shift == Shift.LATER -> stringResource(R.string.advice_try_later, at)
        else -> lead.meal?.let { stringResource(R.string.advice_try_before_meal, at, stringResource(it.word())) }
            ?: stringResource(R.string.advice_try_before_eating, at)
    }
}

/** A section's name in the list, or a strategy's eyebrow. */
@Composable
internal fun sectionName(group: TagGroup?): String = group?.let { stringResource(it.text) } ?: stringResource(R.string.advice_any_time)
