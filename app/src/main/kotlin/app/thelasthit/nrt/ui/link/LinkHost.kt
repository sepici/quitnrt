package app.thelasthit.nrt.ui.link

import android.content.res.Resources
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.matcher.LogLinkError
import app.thelasthit.nrt.domain.matcher.LogLinkParser
import app.thelasthit.nrt.ui.format.ShortDateFormatter
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.log.LogSheet
import app.thelasthit.nrt.ui.log.LogSheetViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Shows what came of the links the app was opened with, over whichever page is open: the
 * snackbar with Undo for a logged dose, the duplicate question, a refusal in plain words, or the
 * log sheet for a bare link or a product that could be several.
 */
@Composable
fun LinkHost(
    snackbars: SnackbarHostState,
    viewModel: LinkViewModel = viewModel(factory = LinkViewModel.Factory),
    sheetViewModel: LogSheetViewModel = graphViewModel(key = "link-sheet") { LogSheetViewModel(it) },
) {
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    val timeFormat = rememberTimeFormatter()

    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            when (outcome) {
                is LinkOutcome.Logged -> launch {
                    val result = snackbars.showSnackbar(
                        message = resources.getString(R.string.today_logged, outcome.entry.total.mgText(), outcome.name),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(outcome.entry)
                }
                is LinkOutcome.Refused -> launch {
                    snackbars.showSnackbar(resources.linkErrorText(outcome.error, timeFormat), duration = SnackbarDuration.Long)
                }
                is LinkOutcome.OpenSheet -> {
                    sheetViewModel.openFromLink(outcome.source, outcome.choices)
                    sheetOpen = true
                }
            }
        }
    }

    if (sheetOpen) {
        LogSheet(
            viewModel = sheetViewModel,
            onDismiss = { sheetOpen = false },
            onSaved = { saved ->
                sheetOpen = false
                viewModel.onSaved(saved.entry)
            },
        )
    }

    duplicate?.let { pending ->
        val time = timeFormat.formatAt(pending.existing.occurredAt, pending.existing.zone)
        AlertDialog(
            onDismissRequest = viewModel::discardDuplicate,
            title = { Text(stringResource(R.string.log_duplicate_title)) },
            text = { Text(stringResource(R.string.log_duplicate_body, time)) },
            confirmButton = { TextButton(onClick = viewModel::keepBoth) { Text(stringResource(R.string.log_duplicate_keep)) } },
            dismissButton = { TextButton(onClick = viewModel::discardDuplicate) { Text(stringResource(R.string.log_duplicate_discard)) } },
        )
    }
}

/** Why a link was refused, in the app's words. */
fun Resources.linkErrorText(error: LogLinkError, timeFormat: DateTimeFormatter): String = when (error) {
    LogLinkError.NotALogLink -> getString(R.string.link_not_log)
    LogLinkError.BadEncoding -> getString(R.string.link_bad_encoding)
    is LogLinkError.UnknownParameter -> getString(R.string.link_unknown_parameter, error.name)
    is LogLinkError.RepeatedParameter -> getString(R.string.link_repeated_parameter, error.name)
    LogLinkError.MissingProduct -> getString(R.string.link_missing_product)
    is LogLinkError.UnknownProduct -> getString(R.string.link_unknown_product, error.text)
    is LogLinkError.BadQuantity -> getString(R.string.link_bad_quantity, LogLinkParser.MAX_QUANTITY)
    is LogLinkError.ConflictingQuantity -> getString(R.string.link_conflicting_quantity, error.inProduct, error.qty)
    is LogLinkError.TooMany -> getString(R.string.link_too_many, error.quantity, LogLinkParser.MAX_QUANTITY)
    is LogLinkError.BadTime -> getString(R.string.link_bad_time)
    is LogLinkError.TimeInFuture -> getString(R.string.link_time_in_future, ShortDateFormatter.format(error.at), timeFormat.format(error.at))
    is LogLinkError.UnknownTags -> getQuantityString(R.plurals.link_unknown_tags, error.tags.size, error.tags.joinToString(", "))
    // Names two tags share, reported one at a time: renaming one fixes it.
    is LogLinkError.AmbiguousTags -> getString(R.string.link_ambiguous_tag, error.tags.keys.first())
}
