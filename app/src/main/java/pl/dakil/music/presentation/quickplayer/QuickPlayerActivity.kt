package pl.dakil.music.presentation.quickplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.music.MainActivity
import pl.dakil.music.MusicApplication
import pl.dakil.music.domain.repository.AppSettings
import pl.dakil.music.presentation.AppViewModelProvider
import pl.dakil.music.ui.theme.MusicTheme

/**
 * Standalone player for audio files opened from other apps (ACTION_VIEW). Reached
 * only through its manifest intent filter — never from in-app navigation — and
 * plays the granted uri in isolation, without touching the library or the queue.
 * No AudioPermissionGate: the incoming uri grant is all the access this screen needs.
 */
class QuickPlayerActivity : ComponentActivity() {

    private val viewModel: QuickPlayerViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        viewModel.load(uri)

        val container = (application as MusicApplication).container
        setContent {
            val settings by container.observeSettings()
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            MusicTheme(
                colorTheme = settings.colorTheme,
                darkThemeOption = settings.darkThemeOption,
                pureBlack = settings.pureBlack,
            ) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                QuickPlayerScreen(
                    state = state,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeek = viewModel::seekTo,
                    onSeekBy = viewModel::seekBy,
                    onBack = ::finish,
                    onOpenApp = ::openMainApp,
                )
            }
        }
    }

    // singleTop: the user opened another file while this player is on top.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(viewModel::load)
    }

    /**
     * Transfers the current track into the main app: inserts it at the front of the
     * live queue and resumes from the same position, then opens Now Playing.
     */
    private fun openMainApp() {
        // Best effort: let the main playback service keep read access to the granted uri.
        intent?.data?.let { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.handOffToMainApp()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true),
        )
        finish()
    }
}
