package pl.dakil.music

import android.app.Application
import pl.dakil.music.di.AppContainer

class MusicApplication : Application() {

    /** Process-wide DI graph. Created eagerly so the MediaController connects early. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.release()
        super.onTerminate()
    }
}
