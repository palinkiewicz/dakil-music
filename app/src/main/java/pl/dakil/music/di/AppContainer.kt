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
import pl.dakil.music.data.datastore.favoritesDataStore
import pl.dakil.music.data.datastore.playlistsDataStore
import pl.dakil.music.data.datastore.settingsDataStore
import pl.dakil.music.data.datastore.sortDataStore
import pl.dakil.music.data.db.MusicDatabase
import pl.dakil.music.data.mediastore.MediaStoreDataSource
import pl.dakil.music.data.playback.MediaControllerPlayerRepository
import pl.dakil.music.data.playback.PlaybackHistoryTracker
import pl.dakil.music.data.repository.AlbumRuleRepositoryImpl
import pl.dakil.music.data.repository.FavoritesRepositoryImpl
import pl.dakil.music.data.repository.ListeningHistoryRepositoryImpl
import pl.dakil.music.data.repository.MusicRepositoryImpl
import pl.dakil.music.data.repository.SettingsRepositoryImpl
import pl.dakil.music.data.repository.SortStateRepositoryImpl
import pl.dakil.music.data.repository.TagEditorRepositoryImpl
import pl.dakil.music.data.repository.UserPlaylistRepositoryImpl
import pl.dakil.music.domain.repository.AlbumRuleRepository
import pl.dakil.music.domain.repository.FavoritesRepository
import pl.dakil.music.domain.repository.ListeningHistoryRepository
import pl.dakil.music.domain.repository.MusicRepository
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
import pl.dakil.music.domain.usecase.PropagateRetagToHistoryUseCase
import pl.dakil.music.domain.usecase.ReconcileHistoryUseCase
import pl.dakil.music.domain.usecase.SearchLibraryUseCase
import pl.dakil.music.domain.usecase.AddSongsToPlaylistUseCase
import pl.dakil.music.domain.usecase.AddToQueueUseCase
import pl.dakil.music.domain.usecase.CreatePlaylistUseCase
import pl.dakil.music.domain.usecase.DeleteAlbumRuleUseCase
import pl.dakil.music.domain.usecase.DeletePlaylistUseCase
import pl.dakil.music.domain.usecase.ObserveAlbumRulesUseCase
import pl.dakil.music.domain.usecase.RemoveSongsFromPlaylistUseCase
import pl.dakil.music.domain.usecase.ReorderPlaylistUseCase
import pl.dakil.music.domain.usecase.UpsertAlbumRuleUseCase
import pl.dakil.music.domain.usecase.EditTagsUseCase
import pl.dakil.music.domain.usecase.GetAlbumsUseCase
import pl.dakil.music.domain.usecase.GetPerformersUseCase
import pl.dakil.music.domain.usecase.GetPlaylistsUseCase
import pl.dakil.music.domain.usecase.GetSongsForAlbumUseCase
import pl.dakil.music.domain.usecase.GetSongsForPerformerUseCase
import pl.dakil.music.domain.usecase.GetSongsForPlaylistUseCase
import pl.dakil.music.domain.usecase.GetUserPlaylistSongsUseCase
import pl.dakil.music.domain.usecase.IsFavoriteUseCase
import pl.dakil.music.domain.usecase.ObserveFavoritesUseCase
import pl.dakil.music.domain.usecase.ObservePlaybackUseCase
import pl.dakil.music.domain.usecase.ObserveSettingsUseCase
import pl.dakil.music.domain.usecase.ObserveUserPlaylistsUseCase
import pl.dakil.music.domain.usecase.PlaybackControlUseCase
import pl.dakil.music.domain.usecase.PlaySongsUseCase
import pl.dakil.music.domain.usecase.RefreshLibraryUseCase
import pl.dakil.music.domain.usecase.RenamePlaylistUseCase
import pl.dakil.music.domain.usecase.ReorderFavoritesUseCase
import pl.dakil.music.domain.usecase.SetFavoritesUseCase
import pl.dakil.music.domain.usecase.ShufflePlayUseCase
import pl.dakil.music.domain.usecase.ToggleFavoriteUseCase
import pl.dakil.music.domain.usecase.UpdateSettingsUseCase

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

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(appContext.settingsDataStore)

    val albumRuleRepository: AlbumRuleRepository =
        AlbumRuleRepositoryImpl(appContext.albumRulesDataStore)

    val musicRepository: MusicRepository =
        MusicRepositoryImpl(mediaStoreDataSource, settingsRepository, albumRuleRepository)

    val favoritesRepository: FavoritesRepository =
        FavoritesRepositoryImpl(appContext.favoritesDataStore)

    val userPlaylistRepository: UserPlaylistRepository =
        UserPlaylistRepositoryImpl(appContext.playlistsDataStore)

    val coverArtRefresher = CoverArtRefresher(appContext)

    val sortStateRepository: SortStateRepository = SortStateRepositoryImpl(appContext.sortDataStore)

    val tagEditorRepository: TagEditorRepository = TagEditorRepositoryImpl(appContext)

    private val database = Room.databaseBuilder(appContext, MusicDatabase::class.java, "music.db").build()

    val listeningHistoryRepository: ListeningHistoryRepository =
        ListeningHistoryRepositoryImpl(database.listeningRecordDao())

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
    val getPlaylists = GetPlaylistsUseCase(musicRepository, favoritesRepository, userPlaylistRepository)
    val getSongsForAlbum = GetSongsForAlbumUseCase(musicRepository)
    val getSongsForPerformer = GetSongsForPerformerUseCase(musicRepository)
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
    val playbackControl = PlaybackControlUseCase(playerRepository)

    val searchLibrary = SearchLibraryUseCase()

    val refreshLibrary = RefreshLibraryUseCase(musicRepository)
    val editTags = EditTagsUseCase(tagEditorRepository)

    val observeSettings = ObserveSettingsUseCase(settingsRepository)
    val updateSettings = UpdateSettingsUseCase(settingsRepository)

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

    init {
        // Self-heal history whenever the library (re)loads: re-link records whose
        // song was deleted and re-added under a new MediaStore id.
        appScope.launch {
            musicRepository.songs.collect { songs ->
                if (songs.isNotEmpty()) reconcileHistory(songs)
            }
        }
    }

    fun release() {
        historyTracker.release()
        playerRepository.release()
        appScope.cancel()
    }
}
