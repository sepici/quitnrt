package app.thelasthit.nrt.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.launch

/**
 * A modal sheet as the mockups draw one: the surface colour, their scrim and drag handle, and in
 * dark mode the hairline round its top edge ([sheetEdge]).
 *
 * The hairline is drawn on the sheet's own content, with the handle and the window's insets
 * inside it, so it moves with the sheet. Drawn on the sheet's modifier instead, it would sit
 * outside the sheet's drag offset, at the top of the window, and cut across a short sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, sheetState: SheetState, content: @Composable ColumnScope.() -> Unit) {
    val colors = LastHit.colors
    val scope = rememberCoroutineScope()
    val close = stringResource(R.string.action_close)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        scrimColor = sheetScrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetEdge(colors)
                .windowInsetsPadding(BottomSheetDefaults.windowInsets),
        ) {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics {
                        // As Material's own handle does: a screen reader can close the sheet from it.
                        dismiss(close) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
                            true
                        }
                    },
                width = 36.dp,
                height = 4.dp,
                color = colors.lineStrong,
            )
            content()
        }
    }
}
