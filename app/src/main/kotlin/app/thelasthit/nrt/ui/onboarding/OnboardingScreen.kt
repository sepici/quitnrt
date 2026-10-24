package app.thelasthit.nrt.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.components.NoteCard
import app.thelasthit.nrt.ui.components.RadioList
import app.thelasthit.nrt.ui.components.RadioOption
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.domain.log.StopDate
import app.thelasthit.nrt.domain.time.DayBoundary
import app.thelasthit.nrt.ui.components.DayLine
import app.thelasthit.nrt.ui.components.DayStartStepper
import app.thelasthit.nrt.ui.format.DayTitleFormatter
import app.thelasthit.nrt.ui.format.countryBrands
import app.thelasthit.nrt.ui.format.countryName
import app.thelasthit.nrt.ui.format.creatureName
import app.thelasthit.nrt.ui.format.variantName
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.game.BandScene
import app.thelasthit.nrt.ui.game.CreatureSilhouette
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The seven set-up steps. Nothing but the disclaimer is stored until "Finish set-up". */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = graphViewModel {
        OnboardingViewModel(it, region = Markets.forRegion(Locale.getDefault().country))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Box(Modifier.fillMaxSize().background(LastHit.colors.bg))
        return
    }
    val step = current.draft.step
    BackHandler(enabled = step != OnboardingStep.WELCOME && step != OnboardingStep.DONE) { viewModel.back() }
    when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(onAccept = viewModel::accept)
        OnboardingStep.STOPPED -> StoppedStep(current, viewModel)
        OnboardingStep.COUNTRY -> CountryStep(current, viewModel)
        OnboardingStep.PRODUCTS -> ProductsStep(current, viewModel)
        OnboardingStep.DEMON -> DemonStep(current, viewModel)
        OnboardingStep.DAY_START -> DayStartStep(current, viewModel)
        OnboardingStep.DONE -> DoneStep(current, onStart = viewModel::start)
    }
}

@Composable
private fun WelcomeStep(onAccept: () -> Unit) {
    val colors = LastHit.colors
    StepScaffold(
        step = OnboardingStep.WELCOME,
        onBack = null,
        footer = {
            NextButton(stringResource(R.string.onboarding_accept), onAccept)
            Text(
                stringResource(R.string.onboarding_accept_note),
                style = LastHit.type.caption,
                color = colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 268.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.hero),
        ) {
            CreatureSilhouette(
                Creature.DRAGON,
                colour = colors.heroTrack,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp).size(128.dp),
            )
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = LastHit.type.hero.copy(fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.04).em),
                    color = colors.onHero,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.onboarding_app_sub),
                    style = LastHit.type.title.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    color = colors.onHero2,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.onboarding_welcome_body),
                    style = LastHit.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    color = colors.onHero,
                    modifier = Modifier.padding(top = 46.dp).widthIn(max = 230.dp),
                )
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Text(
                stringResource(R.string.onboarding_before_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            val points = listOf(
                R.string.onboarding_point_advice_lead to R.string.onboarding_point_advice,
                R.string.onboarding_point_pharmacist_lead to R.string.onboarding_point_pharmacist,
                R.string.onboarding_point_safer_lead to R.string.onboarding_point_safer,
            )
            points.forEachIndexed { index, (lead, rest) ->
                if (index > 0) HorizontalDivider(color = colors.line)
                val leadText = stringResource(lead)
                val restText = stringResource(rest)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(leadText) }
                        append(" ")
                        append(restText)
                    },
                    style = LastHit.type.body,
                    color = colors.text,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.sunk)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_phone), contentDescription = null, tint = colors.text2)
            Text(stringResource(R.string.onboarding_privacy), style = LastHit.type.label, color = colors.text)
        }
    }
}

private val DateWithYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoppedStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val colors = LastHit.colors
    val today = state.today
    val stopped = state.draft.stoppedOn
    StepScaffold(
        step = OnboardingStep.STOPPED,
        onBack = viewModel::back,
        onSkip = viewModel::skipStopDate,
        footer = { NextButton(stringResource(R.string.onboarding_next_country), viewModel::next, enabled = stopped != null) },
    ) {
        StepHeader(stringResource(R.string.onboarding_stopped_title), stringResource(R.string.onboarding_stopped_body))
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = stopped?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                }
            },
        )
        LaunchedEffect(picker.selectedDateMillis) {
            picker.selectedDateMillis?.let { viewModel.pickStopDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        }
        Surface(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.line),
        ) {
            DatePicker(
                state = picker,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = colors.surface,
                    selectedDayContainerColor = colors.chipSelected,
                    selectedDayContentColor = colors.onChipSelected,
                    todayDateBorderColor = colors.lineStrong,
                    todayContentColor = colors.text,
                    dayContentColor = colors.text,
                    weekdayContentColor = colors.text2,
                    navigationContentColor = colors.text,
                    subheadContentColor = colors.text2,
                    yearContentColor = colors.text,
                    selectedYearContainerColor = colors.chipSelected,
                    selectedYearContentColor = colors.onChipSelected,
                ),
            )
        }
        if (stopped != null) {
            val days = StopDate.daysSince(stopped, today)
            Row(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    (if (stopped.year == today.year) DayTitleFormatter else DateWithYear).format(stopped),
                    style = LastHit.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        days == 0L -> stringResource(R.string.onboarding_stopped_today)
                        days < 14 -> pluralStringResource(R.plurals.onboarding_stopped_days, days.toInt(), days.toInt())
                        else -> StopDate.weeksSince(stopped, today).toInt().let { pluralStringResource(R.plurals.onboarding_stopped_weeks, it, it) }
                    },
                    style = LastHit.type.label,
                    color = colors.text2,
                )
            }
            if (StopDate.isEarlyInCourse(stopped, today)) {
                val weeks = StopDate.weeksSince(stopped, today).toInt()
                NoteCard(
                    title = stringResource(R.string.onboarding_course_title),
                    body = if (weeks == 0) {
                        stringResource(R.string.onboarding_course_days)
                    } else {
                        pluralStringResource(R.plurals.onboarding_course_weeks, weeks, weeks)
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun CountryStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val names = Markets.supported + Markets.ELSEWHERE
    val options = names.map { code ->
        RadioOption(
            value = code,
            title = stringResource(countryName(code)),
            detail = stringResource(countryBrands(code)),
            tag = if (code == state.region && code != Markets.ELSEWHERE) stringResource(R.string.onboarding_your_region) else null,
        )
    }
    StepScaffold(
        step = OnboardingStep.COUNTRY,
        onBack = viewModel::back,
        footer = { NextButton(stringResource(R.string.onboarding_next_products), viewModel::next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_country_title), stringResource(R.string.onboarding_country_body))
        RadioList(
            options = options,
            selected = state.draft.country,
            onPick = viewModel::pickCountry,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
        )
    }
}

@Composable
private fun DayStartStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val colors = LastHit.colors
    val timeFormat = rememberTimeFormatter()
    val dayStart = state.draft.dayStart
    val example = DayBoundary(dayStart).lateDoseExample
    val startText = timeFormat.format(dayStart)
    StepScaffold(
        step = OnboardingStep.DAY_START,
        onBack = viewModel::back,
        footer = { NextButton(stringResource(R.string.onboarding_finish), viewModel::finish) },
    ) {
        StepHeader(
            stringResource(R.string.onboarding_day_title),
            if (example == null) {
                stringResource(R.string.onboarding_day_body_midnight)
            } else {
                stringResource(R.string.onboarding_day_body)
            },
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            FieldLabel(stringResource(R.string.onboarding_day_label))
            DayStartStepper(
                time = startText,
                isDefault = dayStart == DayBoundary.DEFAULT_DAY_START,
                canEarlier = dayStart > DayBoundary.EARLIEST,
                canLater = dayStart < DayBoundary.LATEST,
                onMove = viewModel::moveDayStart,
            )
            if (example != null) DayLine(startText, timeFormat.format(example), dayStart.toSecondOfDay(), example.toSecondOfDay())
            Text(
                stringResource(R.string.onboarding_day_hint),
                style = LastHit.type.caption,
                color = colors.text2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun DoneStep(state: OnboardingState, onStart: () -> Unit) {
    val colors = LastHit.colors
    val draft = state.draft
    val name = draft.demonName.trim()
    val timeFormat = rememberTimeFormatter()
    StepScaffold(
        step = OnboardingStep.DONE,
        onBack = null,
        footer = { NextButton(stringResource(R.string.onboarding_start), onStart) },
    ) {
        StepHeader(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_body))
        val flavour = stringResource(R.string.onboarding_done_flavour, name)
        val description = stringResource(R.string.onboarding_done_description, flavour)
        BandScene(
            draft.creature,
            variant = null,
            height = 188.dp,
            textStart = 172.dp,
            textTop = 26.dp,
            modifier = Modifier
                .padding(top = 24.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Column {
                Text(name, style = LastHit.type.headline.copy(fontSize = 30.sp, lineHeight = 34.sp), color = colors.stageText, maxLines = 1)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.demon_hp_unknown), style = LastHit.type.pixel, color = colors.stageText)
                    Text(
                        stringResource(R.string.demon_hp),
                        style = LastHit.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = colors.stageText2,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    flavour,
                    style = LastHit.type.flavour.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = colors.stageText2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        // Each label stays on one line, so the list only breaks after a comma.
        val quickLog = state.pickedProducts.mapNotNull { state.pickLabels[it.id] }.map { pillLabelText(it).replace(' ', '\u00A0') }
        val variantName = stringResource(variantName(draft.variant))
        val kind = stringResource(R.string.onboarding_demon_kind, variantName, stringResource(creatureName(draft.creature)).lowercase(Locale.UK))
        val rows = listOf(
            stringResource(R.string.onboarding_summary_stopped) to (
                draft.stoppedOn?.let { (if (it.year == state.today.year) DayTitleFormatter else DateWithYear).format(it) }
                    ?: stringResource(R.string.onboarding_summary_not_set)
                ),
            stringResource(R.string.onboarding_summary_country) to stringResource(countryName(draft.country)),
            stringResource(R.string.onboarding_summary_quick_log) to (
                quickLog.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: stringResource(R.string.onboarding_summary_nothing)
                ),
            stringResource(R.string.onboarding_summary_demon) to stringResource(R.string.onboarding_summary_demon_value, name, kind.lowercase(Locale.UK)),
            stringResource(R.string.onboarding_summary_day) to timeFormat.format(draft.dayStart),
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            rows.forEachIndexed { index, (label, value) -> SummaryRow(label, value, first = index == 0) }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.onboarding_next_title),
                style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_next_week), style = LastHit.type.body, color = colors.text)
            Text(stringResource(R.string.onboarding_next_plan), style = LastHit.type.body, color = colors.text)
        }
    }
}
