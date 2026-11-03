package pl.dakil.music.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import pl.dakil.music.R
import pl.dakil.music.domain.model.BackupCategory
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.presentation.settings.SectionHeader
import pl.dakil.music.presentation.settings.SettingRow
import pl.dakil.music.ui.designsystem.BackButton
import pl.dakil.music.ui.designsystem.CollapsingScaffold
import pl.dakil.music.ui.designsystem.PageColumn
import pl.dakil.music.ui.designsystem.SegmentedGroup
import pl.dakil.music.ui.designsystem.ShapedIcon
import pl.dakil.music.ui.designsystem.icon.AppIcon
import pl.dakil.music.ui.designsystem.icon.AppSymbol
import pl.dakil.music.ui.designsystem.icon.AppSymbols
import pl.dakil.music.ui.theme.Spacing

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

    CollapsingScaffold(
        title = stringResource(R.string.backup_title),
        navigationIcon = { BackButton(onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        PageColumn(padding, spacing = Spacing.xs) {
            FullBackupCard(
                busy = busy,
                onExport = { exportFullLauncher.launch("music-backup.zip") },
                onImport = { importFullLauncher.launch(arrayOf("application/zip", "*/*")) },
                modifier = Modifier.padding(top = Spacing.s),
            )

            SectionHeader(stringResource(R.string.backup_individual_section))
            SegmentedGroup {
                BackupCategory.entries.forEach { category ->
                    SettingRow(
                        title = stringResource(category.labelRes()),
                        summary = category.fileName,
                        leading = { ShapedIcon(category.symbol()) },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        pendingCategory = category
                                        exportCategoryLauncher.launch(category.fileName)
                                    },
                                    enabled = !busy,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    AppIcon(AppSymbols.Upload, contentDescription = stringResource(R.string.backup_export))
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        pendingCategory = category
                                        importCategoryLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                                    },
                                    enabled = !busy,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    AppIcon(AppSymbols.Download, contentDescription = stringResource(R.string.backup_import))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The headline action: everything in one zip, out or back in. Shows progress while busy. */
@Composable
private fun FullBackupCard(
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                AnimatedContent(busy, label = "backupBusy") { working ->
                    if (working) {
                        LoadingIndicator(Modifier.size(56.dp))
                    } else {
                        ShapedIcon(
                            symbol = AppSymbols.Inventory2,
                            polygon = MaterialShapes.Cookie9Sided,
                            size = 56.dp,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_full_title), style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(stringResource(R.string.backup_full_summary), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(onClick = onExport, enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    AppIcon(AppSymbols.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.backup_export), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onImport,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.weight(1f),
                ) {
                    AppIcon(AppSymbols.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.backup_import), maxLines = 1)
                }
            }
        }
    }
}

private fun BackupCategory.symbol(): AppSymbol = when (this) {
    BackupCategory.SETTINGS -> AppSymbols.Settings
    BackupCategory.FAVORITES -> AppSymbols.Favorite
    BackupCategory.PLAYLISTS -> AppSymbols.QueueMusic
    BackupCategory.ALBUM_RULES -> AppSymbols.Rule
    BackupCategory.LYRICS_ALIGNMENT -> AppSymbols.Lyrics
    BackupCategory.SORT -> AppSymbols.Sort
}

private fun BackupCategory.labelRes(): Int = when (this) {
    BackupCategory.SETTINGS -> R.string.backup_cat_settings
    BackupCategory.FAVORITES -> R.string.backup_cat_favorites
    BackupCategory.PLAYLISTS -> R.string.backup_cat_playlists
    BackupCategory.ALBUM_RULES -> R.string.backup_cat_album_rules
    BackupCategory.LYRICS_ALIGNMENT -> R.string.backup_cat_lyrics_alignment
    BackupCategory.SORT -> R.string.backup_cat_sort
}
