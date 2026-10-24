package app.thelasthit.nrt.ui.advice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.DelayRun
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.RoundButton
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The delay timer (PLAN §9): 5 to 10 minutes while a craving peaks and passes. It counts from its
 * start, so it's right after the app has been closed, buzzes at the end, keeps the screen on
 * while it runs, and carries on under the breathing timer.
 */
@Composable
fun DelayScreen(
    onBack: () -> Unit,
    onBreathe: () -> Unit,
    viewModel: DelayViewModel = timerViewModel { graph, saved, context ->
        DelayViewModel(graph.clock, saved, VibratorBuzzer(context), PartialWakeLock(context))
    },
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    KeepScreenOnWhileTimersRun()
    TimerTicks(running = stage is DelayStage.Running, everyFrame = false, onTick = viewModel::tick)
    val time = rememberTimeFormatter()
    when (val current = stage) {
        is DelayStage.SetUp -> DelaySetUp(current.minutes, viewModel, onBack, onBreathe)
        is DelayStage.Running -> {
            val stop = {
                viewModel.stop()
                onBack()
            }
            BackHandler(onBack = stop)
            DelayRunning(current.run, now, time, onClose = stop, onStop = viewModel::stop, onBreathe = onBreathe)
        }
        is DelayStage.Done -> DelayDone(current.run, time, onClose = onBack, onAgain = viewModel::start)
    }
}

@Composable
private fun DelaySetUp(minutes: Int, viewModel: DelayViewModel, onBack: () -> Unit, onBreathe: () -> Unit) {
    val colors = LastHit.colors
    TimerPage(
        top = { AdviceBackBar(stringResource(R.string.delay_title), onBack) },
        foot = {
            PrimaryButton(onClick = viewModel::start) { colour ->
                PrimaryLabel(pluralStringResource(R.plurals.delay_start, minutes, minutes), colour, R.drawable.ic_timer)
            }
        },
    ) {
        AdviceIntro(stringResource(R.string.delay_intro))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Text(
                stringResource(R.string.delay_length),
                style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
                color = colors.text2,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.sunk)
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        stringResource(R.string.timer_min, minutes),
                        style = LastHit.type.title.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                        color = colors.text,
                    )
                    Text(stringResource(R.string.delay_range), style = LastHit.type.caption, color = colors.text2)
                }
                RoundButton(R.drawable.ic_minus, stringResource(R.string.delay_less), minutes > DelayRun.MINUTES.first, viewModel::less)
                Box(Modifier.size(8.dp))
                RoundButton(R.drawable.ic_plus, stringResource(R.string.delay_more), minutes < DelayRun.MINUTES.last, viewModel::more)
            }
        }
        WhileYouWait(onBreathe)
    }
}

@Composable
private fun DelayRunning(
    run: DelayRun,
    now: Instant,
    time: DateTimeFormatter,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onBreathe: () -> Unit,
) {
    val colors = LastHit.colors
    val left = run.remaining(now)
    TimerPage(
        top = { TimerTopBar(stringResource(R.string.delay_title), stringResource(R.string.timer_stop_back), onClose) },
        foot = { LineButton(stringResource(R.string.delay_stop), onStop, Modifier.fillMaxWidth()) },
    ) {
        val spoken = stringResource(
            R.string.delay_left_description,
            spokenDuration(left),
            pluralStringResource(R.plurals.timer_minutes_spoken, run.minutes, run.minutes),
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                Text(
                    stringResource(R.string.delay_time_left),
                    style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = colors.text2,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lineHeight = fixedSp(100.dp)
                    Text(
                        clockText(left),
                        style = LastHit.type.hero.copy(fontSize = fixedSp(112.dp), lineHeight = lineHeight, letterSpacing = (-0.036).em),
                        color = colors.text,
                        modifier = Modifier.cssLineHeight(lineHeight).alignByBaseline(),
                    )
                    Text(
                        stringResource(R.string.delay_of, run.minutes),
                        style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        color = colors.text2,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            // The track fills with the time gone, in the neutral ink: waiting isn't a dose, and it isn't the guide.
            Box(
                Modifier
                    .padding(top = 26.dp)
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.track),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(run.progress(now).toFloat())
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.text),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val ends = LastHit.type.caption
                Text(stringResource(R.string.delay_started, time.formatAt(run.startedAt)), style = ends, color = colors.text2, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.delay_ends, time.formatAt(run.endsAt)), style = ends, color = colors.text2)
            }
            Text(
                stringResource(R.string.delay_say),
                style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = colors.text,
                modifier = Modifier.padding(top = 26.dp),
            )
        }
        WhileYouWait(onBreathe)
    }
}

/** Three things to do while the timer runs, and the way into the breathing timer. */
@Composable
private fun WhileYouWait(onBreathe: () -> Unit) {
    val colors = LastHit.colors
    ListHeading(stringResource(R.string.delay_wait_title), top = 32.dp)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
        listOf(
            Triple(R.drawable.ic_drop, R.string.delay_tip_water_lead, R.string.delay_tip_water),
            Triple(R.drawable.ic_swap, R.string.delay_tip_else_lead, R.string.delay_tip_else),
            Triple(R.drawable.ic_wind, R.string.delay_tip_breaths_lead, R.string.delay_tip_breaths),
        ).forEach { (icon, lead, rest) ->
            val leadText = stringResource(lead)
            val restText = stringResource(rest)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.text2, modifier = Modifier.size(22.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.text)) { append(leadText) }
                        append(" ")
                        append(restText)
                    },
                    style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.text2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AdviceLink(stringResource(R.string.delay_breathe), onBreathe, Modifier.padding(start = 32.dp, top = 2.dp))
    }
}

@Composable
private fun DelayDone(run: DelayRun, time: DateTimeFormatter, onClose: () -> Unit, onAgain: () -> Unit) {
    val colors = LastHit.colors
    val minutes = run.minutes
    TimerPage(
        top = { TimerTopBar(stringResource(R.string.delay_title), stringResource(R.string.timer_close), onClose) },
        foot = {
            PrimaryButton(onClick = onClose) { colour -> PrimaryLabel(stringResource(R.string.timer_done), colour) }
            LineButton(pluralStringResource(R.plurals.delay_again, minutes, minutes), onAgain, Modifier.fillMaxWidth(), icon = R.drawable.ic_timer)
        },
        centred = true,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(colors.sunk), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.text, modifier = Modifier.size(44.dp))
            }
            Text(
                pluralStringResource(R.plurals.delay_done_title, minutes, minutes),
                style = LastHit.type.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.035).em),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp).cssLineHeight(38.sp),
            )
            Text(
                stringResource(R.string.delay_done_when, time.formatAt(run.startedAt), time.formatAt(run.endsAt)),
                style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.delay_done_say, pluralStringResource(R.plurals.timer_minutes, minutes, minutes)),
                style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp, lineBreak = LineBreak.Heading),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}
