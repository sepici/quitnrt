package app.thelasthit.nrt.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.data.DataGraph

/** The app's [DataGraph], reached from a ViewModel factory. */
val CreationExtras.graph: DataGraph
    get() = (checkNotNull(this[APPLICATION_KEY]) as LastHitApp).graph

/** A ViewModel built from the app's [DataGraph], with no dependency injection framework. */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(crossinline create: (DataGraph) -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create(graph) } })
