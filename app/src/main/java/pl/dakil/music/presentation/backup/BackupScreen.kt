package pl.dakil.music.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import pl.dakil.music.R
import pl.dakil.music.domain.model.BackupCategory
import pl.dakil.music.presentation.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current

    // The category whose single-file import/export is in flight (set before launching SAF).
    var pendingCategory by remember { mutableStateOf<BackupCategory?>(null) }

    val exportFullLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportFull(context.contentResolver, uri) }
    val importFullLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFull(context.contentResolver, uri) }

    val exportCategoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val category = pendingCategory
        if (uri != null && category != null) viewModel.exportCategory(context.contentResolver, uri, category)
    }
    val importCategoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val category = pendingCategory
        if (uri != null && category != null) viewModel.importCategory(context.contentResolver, uri, category)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { res ->
            snackbarHostState.showSnackbar(resources.getString(res))
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SectionHeader(stringResource(R.string.backup_full_section)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_full_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_full_summary)) },
                    trailingContent = {
                        ImportExportActions(
                            enabled = !busy,
                            onExport = { exportFullLauncher.launch("music-backup.zip") },
                            onImport = { importFullLauncher.launch(arrayOf("application/zip", "*/*")) },
                        )
                    },
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.backup_individual_section)) }

            items(BackupCategory.entries) { category ->
                ListItem(
                    headlineContent = { Text(stringResource(category.labelRes())) },
                    supportingContent = { Text(category.fileName) },
                    trailingContent = {
                        ImportExportActions(
                            enabled = !busy,
                            onExport = {
                                pendingCategory = category
                                exportCategoryLauncher.launch(category.fileName)
                            },
                            onImport = {
                                pendingCategory = category
                                importCategoryLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ImportExportActions(
    enabled: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onExport, enabled = enabled) {
            Icon(Icons.Rounded.Upload, contentDescription = stringResource(R.string.backup_export))
        }
        IconButton(onClick = onImport, enabled = enabled) {
            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.backup_import))
        }
    }
}

private fun BackupCategory.labelRes(): Int = when (this) {
    BackupCategory.SETTINGS -> R.string.backup_cat_settings
    BackupCategory.FAVORITES -> R.string.backup_cat_favorites
    BackupCategory.PLAYLISTS -> R.string.backup_cat_playlists
    BackupCategory.ALBUM_RULES -> R.string.backup_cat_album_rules
    BackupCategory.LYRICS_ALIGNMENT -> R.string.backup_cat_lyrics_alignment
    BackupCategory.SORT -> R.string.backup_cat_sort
}
