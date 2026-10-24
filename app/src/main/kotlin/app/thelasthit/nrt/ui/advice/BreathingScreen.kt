package app.thelasthit.nrt.ui.advice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.BreathStep
import app.thelasthit.nrt.domain.advice.BreathingBadges
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.advice.BreathingPlan
import app.thelasthit.nrt.domain.advice.BreathingRun
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.Segmented
import app.thelasthit.nrt.ui.components.SwitchRow
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.Duration
import java.time.Instant

/**
 * The breathing timer (PLAN §9): box breathing or 4-7-8 for 1 to 3 minutes, a dot round the
 * square with the count, and an end frame that shows the way to the next breathing badge. The
 * screen stays on while a session runs, and leaving the app pauses it.
 */
@Composable
fun BreathingScreen(
    onBack: () -> Unit,
    viewModel: BreathingViewModel = timerViewModel { graph, saved, context ->
        BreathingViewModel(graph.clock, saved, graph.breathing, VibratorBuzzer(context), TimerPrefs(context))
    },
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    KeepScreenOnWhileTimersRun()
    TimerTicks(running = stage is BreathingStage.Running, everyFrame = true, onTick = viewModel::tick)
    // Turning the screen off or leaving the app pauses the session. Pausing is fine.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    when (val current = stage) {
        is BreathingStage.SetUp -> BreathingSetUp(current, sessions, viewModel, onBack)
        is BreathingStage.Running -> {
            val stop = {
                viewModel.stop()
                onBack()
            }
            BackHandler(onBack = stop)
            BreathingRunning(current.run, now, onStop = stop, onPause = viewModel::pause, onResume = viewModel::resume)
        }
        is BreathingStage.Done -> BreathingDone(current, onClose = onBack, onAnother = viewModel::another)
    }
}

@Composable
private fun BreathingSetUp(setUp: BreathingStage.SetUp, sessions: Int?, viewModel: BreathingViewModel, onBack: () -> Unit) {
    val colors = LastHit.colors
    TimerPage(
        top = { AdviceBackBar(stringResource(R.string.breathing_title), onBack) },
        foot = {
            PrimaryButton(onClick = viewModel::start) { colour ->
                PrimaryLabel(
                    stringResource(if (setUp.pattern == BreathingPattern.BOX) R.string.breathing_start_box else R.string.breathing_start_478),
                    colour,
                    R.drawable.ic_play,
                )
            }
        },
    ) {
        AdviceIntro(stringResource(R.string.breathing_intro))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldTitle(stringResource(R.string.breathing_pattern))
            PatternOptions(setUp.pattern, viewModel::pickPattern)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldTitle(stringResource(R.string.breathing_length))
            Segmented(
                options = BreathingPlan.MINUTES.toList(),
                selected = setUp.minutes,
                label = { stringResource(R.string.timer_min, it) },
                describe = { pluralStringResource(R.plurals.timer_minutes_spoken, it, it) },
                onPick = viewModel::pickMinutes,
            )
            val plan = setUp.plan
            Text(
                stringResource(
                    R.string.breathing_hint,
                    pluralStringResource(R.plurals.breathing_rounds, plan.rounds, plan.rounds),
                    pluralStringResource(R.plurals.timer_minutes, plan.minutes, plan.minutes),
                ),
                style = LastHit.type.caption,
                color = colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Box(Modifier.padding(top = 28.dp)) {
            SwitchRow(
                stringResource(R.string.breathing_buzz),
                stringResource(R.string.breathing_buzz_sub),
                checked = setUp.buzz,
                onChange = viewModel::setBuzz,
            )
        }
        if (sessions != null) {
            val progress = BreathingBadges.progress(sessions)
            BadgeLine(
                title = if (sessions == 0) stringResource(R.string.breathing_none) else pluralStringResource(R.plurals.breathing_sessions, sessions, sessions),
                body = when {
                    sessions == 0 -> stringResource(R.string.breathing_first_badge)
                    progress.next == null -> stringResource(R.string.breathing_all_badges)
                    else -> stringResource(R.string.breathing_next_badge, progress.next!!)
                },
                progress = progress,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
            )
        }
    }
}

/** The two patterns as radio rows, each with its shape: a square with a dot, or three bars of 4, 7 and 8. */
@Composable
private fun PatternOptions(selected: BreathingPattern, onPick: (BreathingPattern) -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .selectableGroup(),
    ) {
        BreathingPattern.entries.forEachIndexed { index, pattern ->
            if (index > 0) HorizontalDivider(color = colors.line)
            val isSelected = pattern == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onPick(pattern) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PatternShape(pattern, if (isSelected) colors.text else colors.text2)
                Column(Modifier.weight(1f)) {
                    Text(
                        patternName(pattern),
                        style = LastHit.type.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                    Text(
                        stringResource(if (pattern == BreathingPattern.BOX) R.string.breathing_box_sub else R.string.breathing_478_sub),
                        style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
                        color = colors.text2,
                    )
                }
                Box(
                    Modifier
                        .size(24.dp)
                        .border(2.dp, if (isSelected) colors.chipSelected else colors.lineStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Box(Modifier.size(12.dp).clip(CircleShape).background(colors.chipSelected))
                }
            }
        }
    }
}

/** The pattern's little picture, on a grey tile, from the mockup's 24 by 24 drawings. */
@Composable
private fun PatternShape(pattern: BreathingPattern, ink: Color) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(LastHit.colors.sunk),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val unit = size.width / 24f
            when (pattern) {
                BreathingPattern.BOX -> {
                    drawRoundRect(
                        ink,
                        topLeft = Offset(3 * unit, 3 * unit),
                        size = Size(18 * unit, 18 * unit),
                        cornerRadius = CornerRadius(4 * unit),
                        style = Stroke(2 * unit),
                    )
                    drawCircle(ink, radius = 2.6f * unit, center = Offset(3.5f * unit, 12 * unit))
                }
                BreathingPattern.FOUR_SEVEN_EIGHT -> listOf(5f to 7.6f, 10.5f to 13.3f, 16f to 18f).forEach { (y, width) ->
                    drawRoundRect(
                        ink,
                        topLeft = Offset(3 * unit, y * unit),
                        size = Size(width * unit, 3 * unit),
                        cornerRadius = CornerRadius(1.5f * unit),
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldTitle(text: String) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun BreathingRunning(run: BreathingRun, now: Instant, onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    val colors = LastHit.colors
    val moment = run.moment(now)
    val left = moment.remaining
    TimerPage(
        top = {
            TimerTopBar(
                title = patternName(run.plan.pattern),
                closeDescription = stringResource(R.string.timer_stop_back),
                onClose = onStop,
                trailing = stringResource(R.string.breathing_left, clockText(left)),
                trailingDescription = stringResource(R.string.breathing_left, spokenDuration(left)),
            )
        },
        foot = {
            if (run.isPaused) {
                LineButton(stringResource(R.string.breathing_resume), onResume, Modifier.fillMaxWidth(), icon = R.drawable.ic_play)
            } else {
                LineButton(stringResource(R.string.breathing_pause), onPause, Modifier.fillMaxWidth(), icon = R.drawable.ic_pause)
            }
        },
        centred = true,
    ) {
        val step = stringResource(stepName(moment.step))
        val spoken = stringResource(R.string.breathing_now_description, if (run.isPaused) stringResource(R.string.breathing_paused) else step, moment.round, run.plan.rounds)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(STAGE).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(STAGE)) {
                    val path = squarePath(STAGE.toPx(), INSET.toPx(), CORNER.toPx())
                    drawPath(path, colors.track, style = Stroke(LINE.toPx(), cap = StrokeCap.Round))
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val done = Path()
                    val distance = measure.length * moment.roundProgress.toFloat()
                    if (distance > 0f) {
                        measure.getSegment(0f, distance, done, true)
                        drawPath(done, colors.text, style = Stroke(LINE.toPx(), cap = StrokeCap.Round))
                    }
                    drawDot(measure.getPosition(distance), colors.text, colors.bg)
                }
                val fullness = FastOutSlowInEasing.transform(moment.fullness.toFloat())
                val size = LUNG_SMALL + (LUNG_LARGE - LUNG_SMALL) * fullness
                Lung(size) {
                    Text(
                        if (run.isPaused) stringResource(R.string.breathing_paused) else step,
                        style = LastHit.type.title.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                        color = colors.onHero2,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "${moment.count}",
                        style = LastHit.type.hero.copy(fontSize = fixedSp(96.dp), lineHeight = fixedSp(96.dp), letterSpacing = (-0.04).em),
                        color = colors.onHero,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.breathing_round, moment.round, run.plan.rounds),
                style = LastHit.type.label,
                color = colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun BreathingDone(done: BreathingStage.Done, onClose: () -> Unit, onAnother: () -> Unit) {
    val colors = LastHit.colors
    val plan = done.plan
    TimerPage(
        top = { TimerTopBar(patternName(plan.pattern), stringResource(R.string.timer_close), onClose) },
        foot = {
            PrimaryButton(onClick = onClose) { colour -> PrimaryLabel(stringResource(R.string.timer_done), colour) }
            LineButton(stringResource(R.string.breathing_another), onAnother, Modifier.fillMaxWidth(), icon = R.drawable.ic_wind)
        },
        centred = true,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(DONE_STAGE), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(DONE_STAGE)) {
                    val scale = DONE_STAGE.toPx() / STAGE.toPx()
                    drawPath(
                        squarePath(DONE_STAGE.toPx(), INSET.toPx() * scale, CORNER.toPx() * scale),
                        colors.text,
                        style = Stroke(LINE.toPx(), cap = StrokeCap.Round),
                    )
                }
                Lung(120.dp) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onHero, modifier = Modifier.size(52.dp))
                }
            }
            val session = done.session
            if (session != null) {
                Text(
                    stringResource(R.string.breathing_done_title, session),
                    style = LastHit.type.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.035).em),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
                )
                Text(
                    stringResource(
                        R.string.breathing_done_sub,
                        pluralStringResource(R.plurals.timer_minutes, plan.minutes, plan.minutes),
                        stringResource(if (plan.pattern == BreathingPattern.BOX) R.string.breathing_box_lower else R.string.breathing_478_lower),
                        pluralStringResource(R.plurals.breathing_rounds, plan.rounds, plan.rounds),
                    ),
                    style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    color = colors.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                )
                val earned = BreathingBadges.earnedAt(session)
                val progress = BreathingBadges.progress(session, ending = true)
                val after = BreathingBadges.progress(session)
                BadgeLine(
                    title = null,
                    body = when {
                        earned && after.next != null -> stringResource(R.string.breathing_earned_next, after.next!!)
                        earned -> stringResource(R.string.breathing_earned_last)
                        progress.next != null -> stringResource(R.string.breathing_next_badge, progress.next!!)
                        else -> stringResource(R.string.breathing_all_badges)
                    },
                    progress = progress,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 32.dp),
                )
            }
        }
    }
}

/** The hero green square in the middle of the stage, at [size]. */
@Composable
private fun Lung(size: Dp, content: @Composable () -> Unit) {
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(28.dp))
            .background(LastHit.colors.hero)
            .wrapContentSize(unbounded = true),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/** The rounded square the dot follows, starting at the top left after the corner and going clockwise. */
private fun squarePath(side: Float, inset: Float, corner: Float): Path {
    val left = inset
    val top = inset
    val right = side - inset
    val bottom = side - inset
    return Path().apply {
        moveTo(left + corner, top)
        lineTo(right - corner, top)
        arcTo(Rect(right - 2 * corner, top, right, top + 2 * corner), -90f, 90f, false)
        lineTo(right, bottom - corner)
        arcTo(Rect(right - 2 * corner, bottom - 2 * corner, right, bottom), 0f, 90f, false)
        lineTo(left + corner, bottom)
        arcTo(Rect(left, bottom - 2 * corner, left + 2 * corner, bottom), 90f, 90f, false)
        lineTo(left, top + corner)
        arcTo(Rect(left, top, left + 2 * corner, top + 2 * corner), 180f, 90f, false)
        close()
    }
}

private fun DrawScope.drawDot(at: Offset, fill: Color, ring: Color) {
    drawCircle(ring, radius = (DOT + DOT_RING).toPx(), center = at)
    drawCircle(fill, radius = DOT.toPx(), center = at)
}

/** A size in sp that stays the same at any font scale, for the big numbers, which are already large. */
@Composable
internal fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }

/** "0:30", "1:04". */
internal fun clockText(duration: Duration): String {
    val seconds = duration.seconds
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** "30 seconds", "1 minute 4 seconds", for screen readers. */
@Composable
internal fun spokenDuration(duration: Duration): String {
    val minutes = (duration.seconds / 60).toInt()
    val seconds = (duration.seconds % 60).toInt()
    val parts = listOfNotNull(
        if (minutes > 0) pluralStringResource(R.plurals.timer_minutes_spoken, minutes, minutes) else null,
        if (seconds > 0 || minutes == 0) pluralStringResource(R.plurals.timer_seconds_spoken, seconds, seconds) else null,
    )
    return parts.joinToString(" ")
}

@Composable
private fun patternName(pattern: BreathingPattern): String =
    stringResource(if (pattern == BreathingPattern.BOX) R.string.breathing_box else R.string.breathing_478)

private fun stepName(step: BreathStep): Int = when (step) {
    BreathStep.IN -> R.string.breathing_in
    BreathStep.HOLD_IN, BreathStep.HOLD_OUT -> R.string.breathing_hold
    BreathStep.OUT -> R.string.breathing_out
}

private val STAGE = 300.dp
private val DONE_STAGE = 220.dp
private val INSET = 10.dp
private val CORNER = 28.dp
private val LINE = 6.dp
private val DOT = 11.dp
private val DOT_RING = 4.dp
private val LUNG_SMALL = 136.dp
private val LUNG_LARGE = 232.dp

