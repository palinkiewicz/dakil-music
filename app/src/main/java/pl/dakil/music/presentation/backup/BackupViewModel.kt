package pl.dakil.music.presentation.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import pl.dakil.music.R
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.model.BackupCategory

class BackupViewModel(private val container: AppContainer) : ViewModel() {

    /** True while an import/export is running, so the UI can disable the controls. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun exportFull(resolver: ContentResolver, uri: Uri) = run(R.string.backup_export_done) {
        resolver.openOutputStream(uri)?.let { container.exportFullBackup(it) }
            ?: error("Could not open output")
    }

    fun importFull(resolver: ContentResolver, uri: Uri) = run(R.string.backup_import_done) {
        resolver.openInputStream(uri)?.let { container.importFullBackup(it) }
            ?: error("Could not open input")
    }

    fun exportCategory(resolver: ContentResolver, uri: Uri, category: BackupCategory) =
        run(R.string.backup_export_done) {
            resolver.openOutputStream(uri)?.let { container.exportBackupCategory(category, it) }
                ?: error("Could not open output")
        }

    fun importCategory(resolver: ContentResolver, uri: Uri, category: BackupCategory) =
        run(R.string.backup_import_done) {
            resolver.openInputStream(uri)?.let { container.importBackupCategory(category, it) }
                ?: error("Could not open input")
        }

    /** Runs [block] off the main thread, guarding against overlap and reporting the outcome. */
    private fun run(@StringRes successRes: Int, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching { block() }.isSuccess
            _busy.value = false
            _messages.send(if (ok) successRes else R.string.backup_failed)
        }
    }
}
