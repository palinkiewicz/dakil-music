package pl.dakil.music.di

import android.content.Context
import pl.dakil.music.data.datastore.favoritesDataStore
import pl.dakil.music.data.datastore.playlistsDataStore
import pl.dakil.music.data.datastore.settingsDataStore
import pl.dakil.music.data.mediastore.MediaStoreDataSource
import pl.dakil.music.data.playback.MediaControllerPlayerRepository
import pl.dakil.music.data.repository.FavoritesRepositoryImpl
import pl.dakil.music.data.repository.MusicRepositoryImpl
import pl.dakil.music.data.repository.SettingsRepositoryImpl
import pl.dakil.music.data.repository.TagEditorRepositoryImpl
import pl.dakil.music.data.repository.UserPlaylistRepositoryImpl
import pl.dakil.music.domain.repository.FavoritesRepository
import pl.dakil.music.domain.repository.MusicRepository
import pl.dakil.music.domain.repository.PlayerRepository
import pl.dakil.music.domain.repository.SettingsRepository
import pl.dakil.music.domain.repository.TagEditorRepository
import pl.dakil.music.domain.repository.UserPlaylistRepository
import pl.dakil.music.domain.usecase.SearchLibraryUseCase
import pl.dakil.music.domain.usecase.AddSongsToPlaylistUseCase
import pl.dakil.music.domain.usecase.AddToQueueUseCase
import pl.dakil.music.domain.usecase.CreatePlaylistUseCase
import pl.dakil.music.domain.usecase.DeletePlaylistUseCase
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

    val musicRepository: MusicRepository = MusicRepositoryImpl(mediaStoreDataSource)

    val favoritesRepository: FavoritesRepository =
        FavoritesRepositoryImpl(appContext.favoritesDataStore)

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(appContext.settingsDataStore)

    val userPlaylistRepository: UserPlaylistRepository =
        UserPlaylistRepositoryImpl(appContext.playlistsDataStore)

    val tagEditorRepository: TagEditorRepository = TagEditorRepositoryImpl(appContext)

    val playerRepository: PlayerRepository = MediaControllerPlayerRepository(appContext)

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

    val observeFavorites = ObserveFavoritesUseCase(favoritesRepository)
    val isFavorite = IsFavoriteUseCase(favoritesRepository)
    val toggleFavorite = ToggleFavoriteUseCase(favoritesRepository)
    val setFavorites = SetFavoritesUseCase(favoritesRepository)

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

    fun release() {
        playerRepository.release()
    }
}
