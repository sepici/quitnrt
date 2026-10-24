package app.thelasthit.nrt.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.ui.components.SwitchRow
import app.thelasthit.nrt.ui.graphViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SoundViewModel(private val graph: DataGraph) : ViewModel() {
    /** Null until the setting is read. */
    val soundOn: StateFlow<Boolean?> = graph.settings.settings.map<_, Boolean?> { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSoundOn(on: Boolean) {
        viewModelScope.launch { graph.settings.setSoundOn(on) }
    }
}

/**
 * The lair's sound switch (ROADMAP P4-3): on by default, and quiet while the phone is on silent
 * whatever it says.
 */
@Composable
fun SoundSection(viewModel: SoundViewModel = graphViewModel { SoundViewModel(it) }) {
    val on by viewModel.soundOn.collectAsStateWithLifecycle()
    val checked = on ?: return
    SectionHeading(stringResource(R.string.settings_lair))
    SwitchRow(
        title = stringResource(R.string.settings_sound),
        detail = stringResource(R.string.settings_sound_detail),
        checked = checked,
        onChange = viewModel::setSoundOn,
    )
}
