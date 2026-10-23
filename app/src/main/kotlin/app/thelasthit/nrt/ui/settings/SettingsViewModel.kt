package app.thelasthit.nrt.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.data.repo.ImportMode
import app.thelasthit.nrt.data.repo.StoredCounts
import app.thelasthit.nrt.domain.backup.Backup
import app.thelasthit.nrt.domain.backup.BackupException
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the Settings page lists, summed up. */
data class SettingsSummary(
    val products: List<UserProduct>,
    val quickLog: List<PillLabel>,
    val tags: List<TagEntity>,
    val dayStart: LocalTime,
    val disclaimerAcceptedAt: Instant?,
)

/** A backup file picked to restore, and how far along restoring it is. */
data class RestoreCandidate(
    val fileName: String,
    val backup: Backup,
    val madeOn: LocalDate?,
    val mode: ImportMode = ImportMode.MERGE,
    /** Set once "Replace everything" needs confirming, with what it would delete. */
    val replacing: StoredCounts? = null,
)

/** A message for the snackbar: a string, or a plural with its count. */
sealed interface SettingsMessage {
    data class Text(val res: Int) : SettingsMessage
    data class Count(val res: Int, val count: Int) : SettingsMessage
}

class SettingsViewModel(
    private val graph: DataGraph,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    val summary: StateFlow<SettingsSummary?> = combine(
        graph.products.everything,
        graph.products.quickLog,
        graph.tags.all,
        graph.settings.settings,
    ) { products, quickLog, tags, settings ->
        val labels = PillLabel.of(quickLog)
        SettingsSummary(products, quickLog.map { labels.getValue(it.id) }, tags, settings.dayStart, settings.disclaimerAcceptedAt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _restore = MutableStateFlow<RestoreCandidate?>(null)
    val restore: StateFlow<RestoreCandidate?> = _restore.asStateFlow()

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    /** Today's date for backup file names. */
    fun today(): LocalDate = graph.clock.instant().atZone(zone()).toLocalDate()

    suspend fun backupText(): String = withContext(Dispatchers.Default) { graph.backups.export().encode() }

    suspend fun csvText(): String = graph.backups.exportCsv()

    fun tell(message: SettingsMessage) {
        viewModelScope.launch { _messages.send(message) }
    }

    /** Reads a picked file. A file that isn't a backup says so rather than opening the sheet. */
    fun openBackup(fileName: String, text: String) {
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.Default) { Backup.decode(text) }
                val madeOn = runCatching { Instant.parse(backup.exportedAt).atZone(zone()).toLocalDate() }.getOrNull()
                _restore.value = RestoreCandidate(fileName, backup, madeOn)
            } catch (e: BackupException) {
                tell(
                    SettingsMessage.Text(
                        when (e.reason) {
                            BackupException.Reason.NOT_A_BACKUP -> R.string.settings_not_backup
                            BackupException.Reason.NEWER_VERSION -> R.string.settings_newer_backup
                        },
                    ),
                )
            }
        }
    }

    fun pickRestoreMode(mode: ImportMode) = _restore.update { it?.copy(mode = mode) }

    /** Merging goes ahead. Replacing asks first, with what's on the phone now. */
    fun startRestore() {
        val candidate = _restore.value ?: return
        when (candidate.mode) {
            ImportMode.MERGE -> run(candidate, ImportMode.MERGE)
            ImportMode.REPLACE -> viewModelScope.launch {
                _restore.update { it?.copy(replacing = graph.backups.counts()) }
            }
        }
    }

    fun confirmReplace() {
        _restore.value?.let { run(it, ImportMode.REPLACE) }
    }

    fun cancelReplace() = _restore.update { it?.copy(replacing = null) }

    fun closeRestore() {
        _restore.value = null
    }

    private fun run(candidate: RestoreCandidate, mode: ImportMode) {
        _restore.value = null
        viewModelScope.launch {
            val summary = graph.backups.import(candidate.backup, mode)
            _messages.send(
                when (mode) {
                    ImportMode.MERGE -> SettingsMessage.Count(R.plurals.restored_merge, summary.entries)
                    ImportMode.REPLACE -> SettingsMessage.Text(R.string.restored_replace)
                },
            )
        }
    }
}
