package app.thelasthit.nrt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RootViewModel(private val settings: SettingsStore) : ViewModel() {

    /** Null until settings have loaded, so set-up doesn't flash up for someone who has done it. */
    val onboardingComplete: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun skipOnboarding() {
        viewModelScope.launch { settings.completeOnboarding() }
    }
}

/** Set-up until it's done, then the main app. */
@Composable
fun AppRoot(viewModel: RootViewModel = graphViewModel { RootViewModel(it.settings) }) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    when (onboardingComplete) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        false -> OnboardingPlaceholder(onSkip = viewModel::skipOnboarding)
        true -> AppShell()
    }
}

// Stands in for onboarding until it's built later in phase 1.
@Composable
private fun OnboardingPlaceholder(onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_placeholder_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_placeholder_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_placeholder_skip))
        }
    }
}
