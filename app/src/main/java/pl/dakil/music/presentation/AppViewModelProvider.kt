package pl.dakil.music.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.dakil.music.MusicApplication
import pl.dakil.music.di.AppContainer
import pl.dakil.music.presentation.history.ListeningHistoryViewModel
import pl.dakil.music.presentation.library.LibraryViewModel
import pl.dakil.music.presentation.more.MoreViewModel
import pl.dakil.music.presentation.nowplaying.NowPlayingViewModel
import pl.dakil.music.presentation.settings.SettingsViewModel
import pl.dakil.music.presentation.songlist.SongListViewModel
import pl.dakil.music.presentation.statistics.StatisticsViewModel

/** Central [ViewModelProvider.Factory] that injects [AppContainer] into each ViewModel. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { NowPlayingViewModel(container()) }
        initializer { LibraryViewModel(container()) }
        initializer { MoreViewModel(container()) }
        initializer { SettingsViewModel(container()) }
        initializer { SongListViewModel(container(), this.createSavedStateHandle()) }
        initializer { ListeningHistoryViewModel(container()) }
        initializer { StatisticsViewModel(container()) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication).container
