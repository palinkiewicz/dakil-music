package pl.dakil.music

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.music.di.AppContainer
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.presentation.navigation.MusicApp
import pl.dakil.music.presentation.permissions.AudioPermissionGate
import pl.dakil.music.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MusicApplication).container

    // Emits whenever an intent asks to open the Now Playing screen (notification tap).
    // replay = 1 so a signal raised before the NavHost subscribes is still delivered.
    private val openNowPlaying = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by container.observeSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            MusicTheme(
                colorTheme = settings.colorTheme,
                darkThemeOption = settings.darkThemeOption,
                pureBlack = settings.pureBlack,
            ) {
                AudioPermissionGate(onGranted = ::scanLibrary) {
                    MusicApp(navigateToNowPlaying = openNowPlaying)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlaying.tryEmit(Unit)
        }
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            playFromSearch(intent.getStringExtra(SearchManager.QUERY))
        }
        // "Add to Playback Queue" chooser entry (the .AddToQueueActivity alias).
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let(::enqueueExternalAudio)
        }
    }

    /**
     * "Add to Playback Queue" system action for an externally opened audio file:
     * appends it to the live queue (a library track when the uri resolves to one,
     * otherwise a synthetic song playing the granted uri). If nothing is currently
     * playing it starts immediately; otherwise it is appended quietly, without
     * interrupting the current track.
     */
    private fun enqueueExternalAudio(uri: Uri) {
        // Best effort: keep read access beyond this task for SAF-persistable grants;
        // most ACTION_VIEW grants aren't persistable, hence the swallow.
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        lifecycleScope.launch {
            val song = container.resolveAudioUri(uri)
            container.enqueueOrPlay(listOf(song))
        }
    }

    /**
     * Handles a voice "play …" request (Android Auto / Assistant): plays library songs
     * whose title, artist, album or genre matches [query]; an empty query plays everything.
     */
    private fun playFromSearch(query: String?) {
        lifecycleScope.launch {
            val songs = container.musicRepository.songs.first()
            val matches = if (query.isNullOrBlank()) {
                songs
            } else {
                songs.filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true) ||
                        song.genre.contains(query, ignoreCase = true) ||
                        song.artists.any { it.contains(query, ignoreCase = true) }
                }
            }
            val toPlay = matches.ifEmpty { songs }
            if (toPlay.isNotEmpty()) {
                container.playSongs(toPlay, 0)
                openNowPlaying.tryEmit(Unit)
            }
        }
    }

    /** Kicks off the initial MediaStore scan after the user grants audio access. */
    private fun scanLibrary() {
        lifecycleScope.launch { container.refreshLibrary() }
    }

    companion object {
        /** Intent extra set by the playback notification to jump to Now Playing. */
        const val EXTRA_OPEN_NOW_PLAYING = "pl.dakil.music.OPEN_NOW_PLAYING"
    }
}
