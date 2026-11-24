package tt.co.jesses.sooner

import android.app.Application
import tt.co.jesses.sooner.config.SoonerFirebase

class SoonerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoonerFirebase.start(this)
    }
}
