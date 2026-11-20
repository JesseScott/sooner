package tt.co.jesses.sooner.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tt.co.jesses.sooner.BuildConfig

/**
 * Firebase is optional: without app/google-services.json the build skips the
 * Google Services and Crashlytics plugins, FirebaseApp never initialises, and
 * everything here falls back to [SoonerFlags] defaults. That keeps CI and fresh
 * clones building with no Firebase project.
 */
object SoonerFirebase {
    private const val TAG = "SoonerFirebase"

    private val _flags = MutableStateFlow(SoonerFlags())
    /** The current flags: defaults until Remote Config has fetched and activated. */
    val flags: StateFlow<SoonerFlags> = _flags.asStateFlow()

    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Call once from Application.onCreate. */
    fun start(context: Context) {
        if (!isConfigured(context)) {
            Log.i(TAG, "No google-services.json in this build; Crashlytics and Remote Config are off")
            return
        }
        val config = FirebaseRemoteConfig.getInstance()
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                // Debug builds fetch quickly so console changes show up while testing.
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 60 else 3600)
                .build(),
        )
        config.setDefaultsAsync(SoonerFlags.defaults())
        _flags.value = config.read()
        config.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) Log.w(TAG, "Remote Config fetch failed; keeping cached values", task.exception)
            _flags.value = config.read()
        }
    }

    private fun FirebaseRemoteConfig.read() = SoonerFlags(
        refreshIntervalSeconds = getLong(SoonerFlags.KEY_REFRESH_INTERVAL_SECONDS),
        minRefreshSeconds = getLong(SoonerFlags.KEY_MIN_REFRESH_SECONDS),
        maxArrivalMinutes = getLong(SoonerFlags.KEY_MAX_ARRIVAL_MINUTES),
        scanEnabled = getBoolean(SoonerFlags.KEY_SCAN_ENABLED),
    ).sanitized()
}
