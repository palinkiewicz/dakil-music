package pl.dakil.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import pl.dakil.music.data.coverart.EmbeddedArtFetcher
import pl.dakil.music.data.coverart.EmbeddedArtKeyer
import pl.dakil.music.di.AppContainer

class MusicApplication : Application(), ImageLoaderFactory {

    /** Process-wide DI graph. Created eagerly so the MediaController connects early. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * The app-wide Coil loader, registering the embedded-art components so songs can
     * render their own cover art (individual mode) in addition to MediaStore URIs.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(EmbeddedArtKeyer())
                add(EmbeddedArtFetcher.Factory())
            }
            .build()

    override fun onTerminate() {
        container.release()
        super.onTerminate()
    }
}
