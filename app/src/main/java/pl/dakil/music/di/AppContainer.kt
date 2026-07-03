package pl.dakil.music.di

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.dakil.music.data.coverart.CoverArtRefresher
import pl.dakil.music.data.datastore.albumRulesDataStore
import pl.dakil.music.data.datastore.audioEffectsDataStore
import pl.dakil.music.data.datastore.favoritesDataStore
import pl.dakil.music.data.datastore.lyricsAlignmentDataStore
import pl.dakil.music.data.datastore.navigationDataStore
import pl.dakil.music.data.datastore.playbackStateDataStore
import pl.dakil.music.data.datastore.playlistsDataStore
import pl.dakil.music.data.datastore.settingsDataStore
import pl.dakil.music.data.datastore.sortDataStore
import pl.dakil.music.data.db.MusicDatabase
import pl.dakil.music.data.lyrics.LrclibDataSource
import pl.dakil.music.data.repository.BackupRepositoryImpl
import pl.dakil.music.domain.model.BackupCategory
import pl.dakil.music.domain.repository.BackupRepository
import pl.dakil.music.domain.usecase.ExportBackupCategoryUseCase
import pl.dakil.music.domain.usecase.ExportFullBackupUseCase
import pl.dakil.music.domain.usecase.ImportBackupCategoryUseCase
import pl.dakil.music.domain.usecase.ImportFullBackupUseCase
import pl.dakil.music.data.mediastore.MediaStoreDataSource
import pl.dakil.music.data.mediastore.UriAudioResolver
import pl.dakil.music.data.playback.AudioEffectsCapabilitiesProvider
import pl.dakil.music.data.playback.LyricsController
import pl.dakil.music.data.playback.MediaControllerPlayerRepository
import pl.dakil.music.data.playback.PlaybackHistoryTracker
import pl.dakil.music.data.playback.PlaybackResumptionStore
import pl.dakil.music.data.repository.AlbumRuleRepositoryImpl
import pl.dakil.music.data.repository.AudioEffectsRepositoryImpl
import pl.dakil.music.data.repository.FavoritesRepositoryImpl
import pl.dakil.music.data.repository.ListeningHistoryRepositoryImpl
import pl.dakil.music.data.repository.LyricsAlignmentRepositoryImpl
import pl.dakil.music.data.repository.LyricsRepositoryImpl
import pl.dakil.music.data.repository.MusicRepositoryImpl
import pl.dakil.music.data.repository.NavigationConfigRepositoryImpl
import pl.dakil.music.data.repository.SettingsRepositoryImpl
import pl.dakil.music.data.repository.SortStateRepositoryImpl
import pl.dakil.music.data.repository.TagEditorRepositoryImpl
import pl.dakil.music.data.repository.UserPlaylistRepositoryImpl
import pl.dakil.music.domain.repository.AlbumRuleRepository
import pl.dakil.music.domain.repository.AudioEffectsRepository
import pl.dakil.music.domain.repository.FavoritesRepository
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import pl.dakil.music.domain.repository.LyricsAlignmentRepository
import pl.dakil.music.domain.repository.LyricsRepository
import pl.dakil.music.domain.repository.MusicRepository
import pl.dakil.music.domain.repository.NavigationConfigRepository
import pl.dakil.music.domain.repository.PlayerRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.repository.SortStateRepository
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.UserPlaylistRepository
import pl.dakil.music.domain.usecase.ClearHistoryUseCase
import pl.dakil.music.domain.usecase.ExportHistoryUseCase
import pl.dakil.music.domain.usecase.GetEarliestHistoryDateUseCase
import pl.dakil.music.domain.usecase.GetHistoryCountUseCase
import pl.dakil.music.domain.usecase.GetHistoryPageUseCase
import pl.dakil.music.domain.usecase.GetStatisticsUseCase
import pl.dakil.music.domain.usecase.ImportHistoryUseCase
import pl.dakil.music.domain.usecase.MergeHistoryUseCase
import pl.dakil.music.domain.usecase.ObserveHistoryChangesUseCase
import pl.dakil.music.domain.usecase.ObserveNavigationConfigUseCase
import pl.dakil.music.domain.usecase.UpdateNavComponentUseCase
import pl.dakil.music.domain.usecase.PropagateRetagToHistoryUseCase
import pl.dakil.music.domain.usecase.ReconcileHistoryUseCase
import pl.dakil.music.domain.usecase.SearchLibraryUseCase
import pl.dakil.music.domain.usecase.AddSongsToPlaylistUseCase
import pl.dakil.music.domain.usecase.AddToQueueUseCase
import pl.dakil.music.domain.usecase.EnqueueOrPlayUseCase
import pl.dakil.music.domain.usecase.PlayAtFrontUseCase
import pl.dakil.music.domain.usecase.ObserveAudioEffectsUseCase
import pl.dakil.music.domain.usecase.UpdateAudioEffectsUseCase
import pl.dakil.music.domain.usecase.CreatePlaylistUseCase
import pl.dakil.music.domain.usecase.DeleteAlbumRuleUseCase
import pl.dakil.music.domain.usecase.DeletePlaylistUseCase
import pl.dakil.music.domain.usecase.ObserveAlbumRulesUseCase
import pl.dakil.music.domain.usecase.RemoveSongsFromPlaylistUseCase
import pl.dakil.music.domain.usecase.ReorderPlaylistUseCase
import pl.dakil.music.domain.usecase.UpsertAlbumRuleUseCase
import pl.dakil.music.domain.usecase.EditTagsUseCase
import pl.dakil.music.domain.usecase.GetAlbumsUseCase
import pl.dakil.music.domain.usecase.GetGenresUseCase
import pl.dakil.music.domain.usecase.GetPerformersUseCase
import pl.dakil.music.domain.usecase.GetPlaylistsUseCase
import pl.dakil.music.domain.usecase.GetSongsForAlbumUseCase
import pl.dakil.music.domain.usecase.GetSongsForGenreUseCase
import pl.dakil.music.domain.usecase.GetSongsForPerformerUseCase
import pl.dakil.music.domain.usecase.GetSongFileInfoUseCase
import pl.dakil.music.domain.usecase.GetSongsForPlaylistUseCase
import pl.dakil.music.domain.usecase.GetUserPlaylistSongsUseCase
import pl.dakil.music.domain.usecase.BurnLyricsToMetadataUseCase
import pl.dakil.music.domain.usecase.GetLyricsForSongUseCase
import pl.dakil.music.domain.usecase.ReadMetadataLyricsUseCase
import pl.dakil.music.domain.usecase.IsFavoriteUseCase
import pl.dakil.music.domain.usecase.ObserveFavoritesUseCase
import pl.dakil.music.domain.usecase.ObservePlaybackUseCase
import pl.dakil.music.domain.usecase.ObserveSettingsUseCase
import pl.dakil.music.domain.usecase.ObserveUserPlaylistsUseCase
import pl.dakil.music.domain.usecase.PlaybackControlUseCase
import pl.dakil.music.domain.usecase.PlaySongsUseCase
import pl.dakil.music.domain.usecase.RefreshLibraryUseCase
import pl.dakil.music.domain.usecase.SearchLrclibUseCase
import pl.dakil.music.domain.usecase.RenamePlaylistUseCase
import pl.dakil.music.domain.usecase.ResolveAudioUriUseCase
import pl.dakil.music.domain.usecase.ReorderFavoritesUseCase
import pl.dakil.music.domain.usecase.SetFavoritesUseCase
import pl.dakil.music.domain.usecase.ShufflePlayUseCase
import pl.dakil.music.domain.usecase.ToggleFavoriteUseCase
import pl.dakil.music.domain.usecase.UpdateSettingsUseCase
import pl.dakil.music.widget.MusicWidgetUpdater

/**
 * Manual dependency-injection container. A single instance lives on the
 * [pl.dakil.music.MusicApplication] for the process lifetime; ViewModels read it
 * via the application [Context]. Keeping DI manual avoids an annotation processor
 * while still enforcing the Clean Architecture wiring (impls behind interfaces).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // --- Data sources & repositories (interfaces exposed, impls hidden) -------------

    private val mediaStoreDataSource = MediaStoreDataSource(appContext)

    private val uriAudioResolver = UriAudioResolver(appContext)

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(appContext.settingsDataStore)

    val albumRuleRepository: AlbumRuleRepository =
        AlbumRuleRepositoryImpl(appContext.albumRulesDataStore)

    val audioEffectsRepository: AudioEffectsRepository =
        AudioEffectsRepositoryImpl(appContext.audioEffectsDataStore)

    val audioEffectsCapabilitiesProvider = AudioEffectsCapabilitiesProvider(appContext)

    val musicRepository: MusicRepository =
        MusicRepositoryImpl(mediaStoreDataSource, settingsRepository, albumRuleRepository)

    val favoritesRepository: FavoritesRepository =
        FavoritesRepositoryImpl(appContext.favoritesDataStore)

    val userPlaylistRepository: UserPlaylistRepository =
        UserPlaylistRepositoryImpl(appContext.playlistsDataStore)

    val coverArtRefresher = CoverArtRefresher(appContext)

    val sortStateRepository: SortStateRepository = SortStateRepositoryImpl(appContext.sortDataStore)

    val tagEditorRepository: TagEditorRepository = TagEditorRepositoryImpl(appContext)

    private val lrclibDataSource = LrclibDataSource()

    val lyricsRepository: LyricsRepository = LyricsRepositoryImpl(appContext, lrclibDataSource)

    val lyricsAlignmentRepository: LyricsAlignmentRepository =
        LyricsAlignmentRepositoryImpl(appContext.lyricsAlignmentDataStore)

    val navigationConfigRepository: NavigationConfigRepository =
        NavigationConfigRepositoryImpl(appContext.navigationDataStore)

    /** Last-queue snapshots consumed by PlaybackService for system playback resumption. */
    val playbackResumptionStore = PlaybackResumptionStore(appContext.playbackStateDataStore)

    private val database = Room.databaseBuilder(appContext, MusicDatabase::class.java, "music.db").build()

    val listeningHistoryRepository: ListeningHistoryRepository =
        ListeningHistoryRepositoryImpl(database.listeningRecordDao())

    val backupRepository: BackupRepository = BackupRepositoryImpl(
        stores = mapOf(
            BackupCategory.SETTINGS to appContext.settingsDataStore,
            BackupCategory.FAVORITES to appContext.favoritesDataStore,
            BackupCategory.PLAYLISTS to appContext.playlistsDataStore,
            BackupCategory.ALBUM_RULES to appContext.albumRulesDataStore,
            BackupCategory.LYRICS_ALIGNMENT to appContext.lyricsAlignmentDataStore,
            BackupCategory.SORT to appContext.sortDataStore,
        ),
    )

    // Concrete type retained so the history tracker can use its raw-event seam.
    private val mediaControllerPlayer = MediaControllerPlayerRepository(appContext)
    val playerRepository: PlayerRepository = mediaControllerPlayer

    private val historyTracker = PlaybackHistoryTracker(
        source = mediaControllerPlayer,
        historyRepository = listeningHistoryRepository,
        settingsRepository = settingsRepository,
    )

    /** Process-lifetime scope for background reconciliation of history with the library. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Use cases ------------------------------------------------------------------

    val getAlbums = GetAlbumsUseCase(musicRepository)
    val getPerformers = GetPerformersUseCase(musicRepository)
    val getGenres = GetGenresUseCase(musicRepository)
    val getPlaylists = GetPlaylistsUseCase(musicRepository, favoritesRepository, userPlaylistRepository)
    val getSongsForAlbum = GetSongsForAlbumUseCase(musicRepository)
    val getSongsForPerformer = GetSongsForPerformerUseCase(musicRepository)
    val getSongsForGenre = GetSongsForGenreUseCase(musicRepository)
    val getSongsForPlaylist = GetSongsForPlaylistUseCase(musicRepository, favoritesRepository)
    val getUserPlaylistSongs = GetUserPlaylistSongsUseCase(musicRepository, userPlaylistRepository)

    val observeUserPlaylists = ObserveUserPlaylistsUseCase(userPlaylistRepository)
    val createPlaylist = CreatePlaylistUseCase(userPlaylistRepository)
    val renamePlaylist = RenamePlaylistUseCase(userPlaylistRepository)
    val deletePlaylist = DeletePlaylistUseCase(userPlaylistRepository)
    val addSongsToPlaylist = AddSongsToPlaylistUseCase(userPlaylistRepository)
    val removeSongsFromPlaylist = RemoveSongsFromPlaylistUseCase(userPlaylistRepository)
    val reorderPlaylist = ReorderPlaylistUseCase(userPlaylistRepository)

    val observeAlbumRules = ObserveAlbumRulesUseCase(albumRuleRepository)
    val upsertAlbumRule = UpsertAlbumRuleUseCase(albumRuleRepository)
    val deleteAlbumRule = DeleteAlbumRuleUseCase(albumRuleRepository)

    val observeFavorites = ObserveFavoritesUseCase(favoritesRepository)
    val isFavorite = IsFavoriteUseCase(favoritesRepository)
    val toggleFavorite = ToggleFavoriteUseCase(favoritesRepository)
    val setFavorites = SetFavoritesUseCase(favoritesRepository)
    val reorderFavorites = ReorderFavoritesUseCase(favoritesRepository)

    val observePlayback = ObservePlaybackUseCase(playerRepository)
    val playSongs = PlaySongsUseCase(playerRepository)
    val shufflePlay = ShufflePlayUseCase(playerRepository)
    val addToQueue = AddToQueueUseCase(playerRepository)
    val enqueueOrPlay = EnqueueOrPlayUseCase(playerRepository)
    val playAtFront = PlayAtFrontUseCase(playerRepository)
    val playbackControl = PlaybackControlUseCase(playerRepository)
    val resolveAudioUri = ResolveAudioUriUseCase(uriAudioResolver, musicRepository)

    val searchLibrary = SearchLibraryUseCase()

    val refreshLibrary = RefreshLibraryUseCase(musicRepository)
    val getSongFileInfo = GetSongFileInfoUseCase(musicRepository)
    val editTags = EditTagsUseCase(tagEditorRepository)

    val getLyricsForSong = GetLyricsForSongUseCase(lyricsRepository)
    val readMetadataLyrics = ReadMetadataLyricsUseCase(lyricsRepository)
    val searchLrclib = SearchLrclibUseCase(lyricsRepository)

    val observeSettings = ObserveSettingsUseCase(settingsRepository)
    val updateSettings = UpdateSettingsUseCase(settingsRepository)

    val observeNavigationConfig = ObserveNavigationConfigUseCase(navigationConfigRepository)
    val updateNavComponent = UpdateNavComponentUseCase(navigationConfigRepository)

    val observeAudioEffects = ObserveAudioEffectsUseCase(audioEffectsRepository)
    val updateAudioEffects = UpdateAudioEffectsUseCase(audioEffectsRepository)

    val getHistoryPage = GetHistoryPageUseCase(listeningHistoryRepository)
    val getHistoryCount = GetHistoryCountUseCase(listeningHistoryRepository)
    val observeHistoryChanges = ObserveHistoryChangesUseCase(listeningHistoryRepository)
    val getEarliestHistoryDate = GetEarliestHistoryDateUseCase(listeningHistoryRepository)
    val getStatistics = GetStatisticsUseCase(listeningHistoryRepository)
    val clearHistory = ClearHistoryUseCase(listeningHistoryRepository)
    val exportHistory = ExportHistoryUseCase(listeningHistoryRepository)
    val importHistory = ImportHistoryUseCase(listeningHistoryRepository)
    val reconcileHistory = ReconcileHistoryUseCase(listeningHistoryRepository)
    val propagateRetagToHistory = PropagateRetagToHistoryUseCase(listeningHistoryRepository)
    val mergeHistory = MergeHistoryUseCase(listeningHistoryRepository)

    val exportBackupCategory = ExportBackupCategoryUseCase(backupRepository)
    val importBackupCategory = ImportBackupCategoryUseCase(backupRepository)
    val exportFullBackup = ExportFullBackupUseCase(backupRepository)
    val importFullBackup = ImportFullBackupUseCase(backupRepository)

    val burnLyricsToMetadata = BurnLyricsToMetadataUseCase(
        editTags = editTags,
        propagateRetagToHistory = propagateRetagToHistory,
        alignmentRepository = lyricsAlignmentRepository,
    )

    /** Watches playback and resolves lyrics for the current song; UI observes [LyricsController.state]. */
    val lyricsController = LyricsController(
        playerRepository = playerRepository,
        settingsRepository = settingsRepository,
        readMetadataLyrics = readMetadataLyrics,
        searchLrclib = searchLrclib,
        scope = appScope,
    )

    init {
        // Self-heal history whenever the library (re)loads: re-link records whose
        // song was deleted and re-added under a new MediaStore id.
        appScope.launch {
            musicRepository.songs.collect { songs ->
                if (songs.isNotEmpty()) reconcileHistory(songs)
            }
        }
        // Refresh home-screen widgets when the track or play/pause state changes.
        MusicWidgetUpdater.start(appContext, this, appScope)
    }

    fun release() {
        historyTracker.release()
        playerRepository.release()
        appScope.cancel()
    }
}
