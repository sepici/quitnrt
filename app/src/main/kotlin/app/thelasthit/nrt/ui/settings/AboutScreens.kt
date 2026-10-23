package app.thelasthit.nrt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.time.DayBoundary
import app.thelasthit.nrt.ui.components.DayLine
import app.thelasthit.nrt.ui.components.DayStartStepper
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.format.DayMonthYearFormatter
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

class DayStartViewModel(private val graph: DataGraph) : ViewModel() {

    val saved: StateFlow<LocalTime?> = graph.settings.settings.map { it.dayStart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores the new day start, then moves every dose to the day it now counts toward. */
    fun save(time: LocalTime, then: () -> Unit) {
        viewModelScope.launch {
            graph.settings.setDayStart(time)
            graph.entries.recalculateLogicalDays(DayBoundary(time))
            then()
        }
    }
}

/** The day start setting. [onSaved] runs once doses have moved, and should leave the page. */
@Composable
fun DayStartScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DayStartViewModel = graphViewModel { DayStartViewModel(it) },
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val timeFormat = rememberTimeFormatter()
    SettingsPage(stringResource(R.string.day_title), stringResource(R.string.settings_back), onBack) {
        val stored = saved ?: return@SettingsPage
        var time by remember(stored) { mutableStateOf(stored) }
        val example = DayBoundary(time).lateDoseExample
        Intro(
            if (example == null) {
                stringResource(R.string.onboarding_day_body_midnight)
            } else {
                stringResource(R.string.onboarding_day_body, timeFormat.format(time), timeFormat.format(example))
            },
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            FieldLabel(stringResource(R.string.onboarding_day_label))
            DayStartStepper(
                time = timeFormat.format(time),
                isDefault = time == DayBoundary.DEFAULT_DAY_START,
                canEarlier = time > DayBoundary.EARLIEST,
                canLater = time < DayBoundary.LATEST,
                onMove = { time = DayBoundary.step(time, it) },
            )
            if (example != null) DayLine(timeFormat.format(time), timeFormat.format(example), time.toSecondOfDay(), example.toSecondOfDay())
            Box(Modifier.padding(top = 28.dp)) {
                PrimaryButton(
                    onClick = { viewModel.save(time, then = onSaved) },
                    enabled = time != stored,
                ) { content ->
                    Text(
                        stringResource(R.string.day_save),
                        style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        color = content,
                    )
                }
            }
        }
    }
}

class AboutViewModel(graph: DataGraph) : ViewModel() {
    val acceptedAt = graph.settings.settings.map { it.disclaimerAcceptedAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The font licences bundled in assets, which About must show. */
enum class FontLicence(val title: Int, val detail: Int, val asset: String) {
    GROTESK(R.string.about_font_grotesk, R.string.about_font_grotesk_detail, "licences/schibsted-grotesk-ofl.txt"),
    SILKSCREEN(R.string.about_font_silkscreen, R.string.about_font_silkscreen_detail, "licences/silkscreen-ofl.txt"),
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicence: (FontLicence) -> Unit,
    viewModel: AboutViewModel = graphViewModel { AboutViewModel(it) },
) {
    val acceptedAt by viewModel.acceptedAt.collectAsStateWithLifecycle()
    val colors = LastHit.colors
    val timeFormat = rememberTimeFormatter()
    SettingsPage(stringResource(R.string.about_title), stringResource(R.string.settings_back), onBack) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Text(stringResource(R.string.about_app), style = LastHit.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = colors.text)
            Text(stringResource(R.string.about_version, appVersion()), style = LastHit.type.label, color = colors.text2)
        }
        SectionHeading(stringResource(R.string.about_not_advice))
        Prose {
            listOf(
                R.string.onboarding_point_advice_lead to R.string.onboarding_point_advice,
                R.string.onboarding_point_pharmacist_lead to R.string.onboarding_point_pharmacist,
                R.string.onboarding_point_safer_lead to R.string.onboarding_point_safer,
            ).forEach { (lead, rest) ->
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
                )
            }
            acceptedAt?.let { at ->
                val zone = ZoneId.systemDefault()
                Text(
                    stringResource(R.string.about_accepted, DayMonthYearFormatter.format(at.atZone(zone)), timeFormat.formatAt(at, zone)),
                    style = LastHit.type.label,
                    color = colors.text2,
                )
            }
        }
        SectionHeading(stringResource(R.string.about_sources_title))
        Prose {
            Text(stringResource(R.string.about_sources), style = LastHit.type.body, color = colors.text)
            Text(stringResource(R.string.about_sources_pack), style = LastHit.type.body, color = colors.text)
        }
        SectionHeading(stringResource(R.string.about_privacy_title))
        Prose { Text(stringResource(R.string.about_privacy), style = LastHit.type.body, color = colors.text) }
        SectionHeading(stringResource(R.string.about_fonts))
        RowList {
            FontLicence.entries.forEachIndexed { index, licence ->
                SettingsRow(
                    stringResource(licence.title),
                    stringResource(licence.detail),
                    first = index == 0,
                    onClick = { onLicence(licence) },
                    chevron = true,
                )
            }
        }
    }
}

@Composable
private fun Prose(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** A font's licence, read from the app's assets. */
@Composable
fun LicenceScreen(licence: FontLicence, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(licence) {
        runCatching { context.assets.open(licence.asset).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    SettingsPage(stringResource(R.string.licence_title, stringResource(licence.title)), stringResource(R.string.settings_back), onBack) {
        Text(
            text,
            style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
            color = LastHit.colors.text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
