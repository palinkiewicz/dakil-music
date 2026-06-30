package pl.dakil.music.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pl.dakil.music.MainActivity
import pl.dakil.music.MusicApplication
import pl.dakil.music.R
import pl.dakil.music.domain.model.AudioEffectsSettings
import pl.dakil.music.domain.model.Song

/**
 * Hosts the ExoPlayer and its [MediaLibrarySession]. As a [MediaLibraryService] it owns
 * background playback, the system Now Playing notification, lock-screen/Bluetooth
 * transport controls — and a browsable content tree consumed by media browsers such as
 * Android Auto (see [MediaBrowseTree]). The app's UI connects as a MediaController client.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var audioManager: AudioManager

    /** Owns the platform audio effects bound to the player's session. */
    private var audioEffects: AudioEffectsController? = null

    /** Latest desired effect settings, re-applied whenever the audio session changes. */
    private var audioEffectsSettings = AudioEffectsSettings()

    /** Audio session id the effects are currently attached to, to avoid needless rebuilds. */
    private var audioEffectsSessionId: Int? = null

    /** Latest library snapshot, used to build the browse tree off the main library cache. */
    @Volatile
    private var librarySongs: List<Song> = emptyList()

    // Latest auto-pause/resume preferences, kept in sync from the settings store.
    private var autoPauseOnZeroVolume = true
    private var autoResumeOnVolumeRestored = true

    /** True while playback is paused specifically because the media volume hit 0. */
    private var pausedByVolume = false

    /** Fires on any system setting change; we re-read the media volume on each. */
    private var volumeObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause instead of ducking/continuing when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Pin a stable audio session id up front so the effects can attach immediately,
        // and also (re)build the controller if ExoPlayer reports a different session id
        // once its AudioTrack is initialized. Whenever the controller is rebuilt, the
        // latest desired settings are re-applied.
        val audioSessionId = audioManager.generateAudioSessionId()
        if (audioSessionId != AudioManager.ERROR) {
            player.setAudioSessionId(audioSessionId)
            attachAudioEffects(audioSessionId)
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
                attachAudioEffects(audioSessionId)
            }
        })

        val closeButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.notification_action_close))
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setCustomLayout(ImmutableList.of(closeButton))
            // Tapping the notification body opens the app on the Now Playing screen.
            .setSessionActivity(buildNowPlayingActivityIntent())
            .build()

        val container = (application as MusicApplication).container

        // Keep the auto-pause preferences fresh (works even with no UI connected).
        val settingsRepository = container.settingsRepository
        settingsRepository.settings
            .onEach {
                autoPauseOnZeroVolume = it.autoPauseOnZeroVolume
                autoResumeOnVolumeRestored = it.autoResumeOnVolumeRestored
            }
            .launchIn(serviceScope)

        // Apply audio effects from the shared store; the UI writes, the service applies.
        // Cache the latest settings too, so they can be re-applied when the audio
        // session id changes and the effects controller is rebuilt.
        container.audioEffectsRepository.settings
            .onEach {
                audioEffectsSettings = it
                audioEffects?.apply(it)
            }
            .launchIn(serviceScope)

        // Cache the library for the browse tree and tell browsers when it changes.
        container.musicRepository.songs
            .onEach { songs ->
                librarySongs = songs
                notifyBrowseChildrenChanged()
            }
            .launchIn(serviceScope)
        // Ensure the library is populated even on a cold start with no Activity (e.g. Auto).
        serviceScope.launch { runCatching { container.musicRepository.refresh() } }

        registerVolumeObserver()
    }

    /** (Re)build the effects controller for [sessionId] and apply the latest settings. */
    private fun attachAudioEffects(sessionId: Int) {
        if (audioEffectsSessionId == sessionId && audioEffects != null) return
        audioEffects?.release()
        audioEffectsSessionId = sessionId
        audioEffects = AudioEffectsController(sessionId).also { it.apply(audioEffectsSettings) }
    }

    /**
     * Observe media-volume changes via a [ContentObserver] on system settings —
     * this keeps working while the app is backgrounded, unlike a UI-scoped listener.
     */
    private fun registerVolumeObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onVolumeChanged()
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        volumeObserver = observer
    }

    private fun onVolumeChanged() {
        val player = mediaSession?.player ?: return
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0) {
            if (autoPauseOnZeroVolume && player.isPlaying) {
                pausedByVolume = true
                player.pause()
            }
        } else if (pausedByVolume) {
            pausedByVolume = false
            if (autoResumeOnVolumeRestored &&
                !player.isPlaying &&
                player.playbackState != Player.STATE_IDLE
            ) {
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /** Re-notify browsers of the dynamic browse nodes after the library changes. */
    private fun notifyBrowseChildrenChanged() {
        val session = mediaSession ?: return
        for (parentId in MediaBrowseTree.dynamicParentIds) {
            val count = MediaBrowseTree.children(parentId, librarySongs, categoryTitles()).size
            session.notifyChildrenChanged(parentId, count, null)
        }
    }

    private fun categoryTitles() = MediaBrowseTree.CategoryTitles(
        albums = getString(R.string.tab_albums),
        artists = getString(R.string.tab_performers),
        genres = getString(R.string.tab_genres),
    )

    /** PendingIntent that brings the app to the foreground on the Now Playing screen. */
    private fun buildNowPlayingActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Handles the custom "close" notification action and serves the browsable library
     * tree to media browsers (Android Auto). Browse data comes from the cached
     * [librarySongs]; playback requests are resolved to URIs by media id.
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    connectionResult.availableSessionCommands.buildUpon()
                        .add(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
                        .build()
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE) {
                stopEverything()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaBrowseTree.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = MediaBrowseTree.children(parentId, librarySongs, categoryTitles())
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = MediaBrowseTree.item(mediaId, librarySongs, categoryTitles())
            return Futures.immediateFuture(
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                },
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val count = MediaBrowseTree.children(parentId, librarySongs, categoryTitles()).size
            session.notifyChildrenChanged(browser, parentId, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        /**
         * Browsers send back items stripped to their media id (no uri); re-attach the
         * playable [MediaItem] for each known song so playback can start.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapTo(ArrayList(mediaItems.size)) { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    librarySongs.firstOrNull { it.id.toString() == item.mediaId }
                        ?.let(MediaItemMapper::toMediaItem)
                        ?: item
                }
            }
            return Futures.immediateFuture(resolved)
        }
    }

    /**
     * Stops the current song, tears the player's queue down and shuts the service
     * down so the foreground notification is dismissed.
     */
    private fun stopEverything() {
        mediaSession?.player?.run {
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    /**
     * While auto-paused for zero volume, keep the service in the foreground. Media3
     * normally demotes the service when playback pauses; resuming would then require
     * *starting* a foreground service from the background, which Android 12+ forbids
     * (so the volume-triggered [Player.play] would silently fail). Staying foreground
     * means the resume is just a continuation, allowed from the background.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || pausedByVolume)
    }

    /** Stop the service if the user swipes the app away with nothing playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        volumeObserver?.let(contentResolver::unregisterContentObserver)
        volumeObserver = null
        serviceScope.cancel()
        audioEffects?.release()
        audioEffects = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        /** Custom session command id for the notification's close action. */
        const val ACTION_CLOSE = "pl.dakil.music.action.CLOSE"
    }
}
