package app.thelasthit.nrt.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.domain.time.DayBoundary
import app.thelasthit.nrt.ui.components.FieldLabel
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.components.RadioList
import app.thelasthit.nrt.ui.components.RadioOption
import app.thelasthit.nrt.ui.components.sheetEdge
import app.thelasthit.nrt.ui.components.sheetScrim
import app.thelasthit.nrt.ui.format.DayMonthYearFormatter
import app.thelasthit.nrt.ui.format.pillLabelText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where each Settings row goes. */
data class SettingsLinks(
    val products: () -> Unit,
    val quickLog: () -> Unit,
    val tags: () -> Unit,
    val dayStart: () -> Unit,
    val about: () -> Unit,
)

@Composable
fun SettingsScreen(
    snackbars: SnackbarHostState,
    onBack: () -> Unit,
    links: SettingsLinks,
    viewModel: SettingsViewModel = graphViewModel { SettingsViewModel(it) },
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val timeFormat = rememberTimeFormatter()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is SettingsMessage.Text -> resources.getString(message.res)
                is SettingsMessage.Count -> resources.getQuantityString(message.res, message.count, message.count)
            }
            launch { snackbars.showSnackbar(text) }
        }
    }

    // Each file goes where the user picks, through the system's own picker.
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { save(resolver, uri, viewModel.backupText(), viewModel, R.string.settings_exported) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { save(resolver, uri, viewModel.csvText(), viewModel, R.string.settings_csv_exported) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = withContext(Dispatchers.IO) {
                    runCatching {
                        displayName(resolver, uri) to requireNotNull(resolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    }.getOrNull()
                }
                if (read == null) {
                    viewModel.tell(SettingsMessage.Text(R.string.settings_read_failed))
                } else {
                    viewModel.openBackup(read.first, read.second)
                }
            }
        }
    }

    SettingsPage(stringResource(R.string.settings_title), stringResource(R.string.settings_back_today), onBack) {
        val current = summary ?: return@SettingsPage
        SectionHeading(stringResource(R.string.settings_what_you_use))
        RowList {
            val inUse = current.products.count { !it.isHidden }
            val hidden = current.products.count { it.isHidden }
            val productsDetail = when {
                current.products.isEmpty() -> stringResource(R.string.settings_products_none)
                hidden == 0 -> pluralStringResource(R.plurals.settings_products_in_use, inUse, inUse)
                else -> listOf(
                    pluralStringResource(R.plurals.settings_products_in_use, inUse, inUse),
                    pluralStringResource(R.plurals.settings_hidden_count, hidden, hidden),
                ).joinToString(", ")
            }
            SettingsRow(stringResource(R.string.settings_products), productsDetail, first = true, onClick = links.products, chevron = true)
            val quickLog = current.quickLog.map { pillLabelText(it) }
            SettingsRow(
                stringResource(R.string.settings_quick_log),
                quickLog.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: stringResource(R.string.settings_quick_log_empty),
                first = false,
                onClick = links.quickLog,
                chevron = true,
            )
            val builtIn = current.tags.count { it.isBuiltIn }
            val own = current.tags.count { !it.isBuiltIn }
            val hiddenTags = current.tags.count { it.isHidden }
            val tagParts = buildList {
                add(pluralStringResource(R.plurals.settings_tags_built_in, builtIn, builtIn))
                if (own > 0) add(pluralStringResource(R.plurals.settings_tags_own, own, own))
                if (hiddenTags > 0) add(pluralStringResource(R.plurals.settings_hidden_count, hiddenTags, hiddenTags))
            }
            SettingsRow(stringResource(R.string.settings_tags), tagParts.joinToString(", "), first = false, onClick = links.tags, chevron = true)
        }

        SectionHeading(stringResource(R.string.settings_your_day))
        RowList {
            val example = DayBoundary(current.dayStart).lateDoseExample
            SettingsRow(
                stringResource(R.string.settings_day_start),
                if (example == null) {
                    stringResource(R.string.settings_day_start_midnight)
                } else {
                    stringResource(R.string.settings_day_start_detail, timeFormat.format(example))
                },
                first = true,
                onClick = links.dayStart,
                value = timeFormat.format(current.dayStart),
                chevron = true,
            )
        }

        SectionHeading(stringResource(R.string.settings_backup))
        RowList {
            SettingsRow(
                stringResource(R.string.settings_export),
                stringResource(R.string.settings_export_detail),
                first = true,
                onClick = { exportBackup.launch("thelasthit-backup-${viewModel.today()}.json") },
            )
            SettingsRow(
                stringResource(R.string.settings_restore),
                stringResource(R.string.settings_restore_detail),
                first = false,
                onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            )
            SettingsRow(
                stringResource(R.string.settings_csv),
                stringResource(R.string.settings_csv_detail),
                first = false,
                onClick = { exportCsv.launch("thelasthit-doses-${viewModel.today()}.csv") },
            )
        }
        FootNote(stringResource(R.string.settings_backup_foot))

        SectionHeading(stringResource(R.string.settings_about_section))
        RowList {
            SettingsRow(
                stringResource(R.string.settings_about),
                stringResource(R.string.settings_about_detail),
                first = true,
                onClick = links.about,
                chevron = true,
            )
            SettingsRow(stringResource(R.string.settings_version), detail = null, first = false, onClick = null, value = appVersion())
        }
    }

    restore?.let { candidate ->
        RestoreSheet(candidate, viewModel)
        candidate.replacing?.let { counts ->
            val madeOn = candidate.madeOn?.let { DayMonthYearFormatter.format(it) } ?: candidate.fileName
            AlertDialog(
                onDismissRequest = viewModel::cancelReplace,
                title = { Text(stringResource(R.string.replace_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(LocalResources.current.replaceBody(counts, madeOn))
                        Text(stringResource(R.string.replace_small), style = LastHit.type.label, color = LastHit.colors.text2)
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmReplace) {
                        Text(stringResource(R.string.replace_confirm), color = LastHit.colors.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::cancelReplace) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSheet(candidate: RestoreCandidate, viewModel: SettingsViewModel) {
    val colors = LastHit.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::closeRestore,
        sheetState = sheetState,
        containerColor = colors.surface,
        scrimColor = sheetScrim,
        modifier = Modifier.sheetEdge(colors),
        contentColor = colors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.lineStrong, width = 36.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.restore_title),
                        style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        color = colors.text,
                    )
                    Text(stringResource(R.string.restore_from_file), style = LastHit.type.label, color = colors.text2)
                }
                IconButton(onClick = viewModel::closeRestore) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = colors.text)
                }
            }
            val backup = candidate.backup
            val doses = pluralStringResource(R.plurals.restore_doses, backup.entries.size, backup.entries.size)
            val products = pluralStringResource(R.plurals.restore_products, backup.products.size, backup.products.size)
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.sunk)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = colors.text2)
                Column {
                    Text(candidate.fileName, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                    Text(
                        if (candidate.madeOn != null) {
                            stringResource(R.string.restore_file_detail, DayMonthYearFormatter.format(candidate.madeOn), doses, products)
                        } else {
                            "$doses, $products"
                        },
                        style = LastHit.type.label.copy(fontWeight = FontWeight.Normal),
                        color = colors.text2,
                    )
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                FieldLabel(stringResource(R.string.restore_how))
                RadioList(
                    options = listOf(
                        RadioOption(ImportMode.MERGE, stringResource(R.string.restore_merge), stringResource(R.string.restore_merge_detail)),
                        RadioOption(ImportMode.REPLACE, stringResource(R.string.restore_replace), stringResource(R.string.restore_replace_detail)),
                    ),
                    selected = candidate.mode,
                    onPick = viewModel::pickRestoreMode,
                )
            }
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                PrimaryButton(onClick = viewModel::startRestore) { content ->
                    // Names what happens, since the two ways of restoring are so different.
                    Text(
                        stringResource(if (candidate.mode == ImportMode.REPLACE) R.string.replace_confirm else R.string.restore_button),
                        style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        color = content,
                    )
                }
            }
        }
    }
}

private suspend fun save(resolver: ContentResolver, uri: Uri, text: String, viewModel: SettingsViewModel, done: Int) {
    val saved = withContext(Dispatchers.IO) {
        runCatching { requireNotNull(resolver.openOutputStream(uri)).use { it.write(text.toByteArray()) } }.isSuccess
    }
    viewModel.tell(SettingsMessage.Text(if (saved) done else R.string.settings_export_failed))
}

private fun displayName(resolver: ContentResolver, uri: Uri): String =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

@Composable
fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
