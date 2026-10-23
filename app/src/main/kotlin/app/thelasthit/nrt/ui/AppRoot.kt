package app.thelasthit.nrt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.settings.SettingsStore
import app.thelasthit.nrt.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RootViewModel(settings: SettingsStore) : ViewModel() {

    /** Null until settings have loaded, so set-up doesn't flash up for someone who has done it. */
    val onboardingComplete: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)
}

/** Set-up until it's done, then the main app. */
@Composable
fun AppRoot(viewModel: RootViewModel = graphViewModel { RootViewModel(it.settings) }) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    when (onboardingComplete) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        false -> OnboardingScreen()
        true -> AppShell()
    }
}
