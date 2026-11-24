package tt.co.jesses.sooner.config

/**
 * Settings that can be changed from Firebase Remote Config without shipping a
 * new build. The defaults here are what the app uses when Firebase isn't set
 * up, is offline, or hasn't fetched yet, so they must always be safe.
 *
 * Values are clamped by [sanitized] so a typo in the console can't, say, make
 * every phone download the 400 KB feed once a second.
 */
data class SoonerFlags(
    /** How often the arrivals screen refetches while visible. */
    val refreshIntervalSeconds: Long = DEFAULT_REFRESH_INTERVAL_SECONDS,
    /** A non-forced refresh is skipped if the last fetch was more recent than this. */
    val minRefreshSeconds: Long = DEFAULT_MIN_REFRESH_SECONDS,
    /** Arrivals further out than this are hidden. */
    val maxArrivalMinutes: Long = DEFAULT_MAX_ARRIVAL_MINUTES,
    /** Kill switch for the photo buttons, in case OCR misbehaves on some phones. */
    val scanEnabled: Boolean = true,
) {
    fun sanitized(): SoonerFlags = copy(
        refreshIntervalSeconds = refreshIntervalSeconds.coerceIn(15, 600),
        minRefreshSeconds = minRefreshSeconds.coerceIn(10, 300),
        maxArrivalMinutes = maxArrivalMinutes.coerceIn(10, 240),
    )

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 60L
        const val DEFAULT_MIN_REFRESH_SECONDS = 30L
        const val DEFAULT_MAX_ARRIVAL_MINUTES = 90L

        // Remote Config parameter keys. Keep in sync with docs/FIREBASE.md.
        const val KEY_REFRESH_INTERVAL_SECONDS = "arrivals_refresh_interval_seconds"
        const val KEY_MIN_REFRESH_SECONDS = "arrivals_min_refresh_seconds"
        const val KEY_MAX_ARRIVAL_MINUTES = "arrivals_max_minutes"
        const val KEY_SCAN_ENABLED = "scan_enabled"

        /** The in-app defaults, in the form Remote Config's setDefaults takes. */
        fun defaults(): Map<String, Any> = SoonerFlags().let {
            mapOf(
                KEY_REFRESH_INTERVAL_SECONDS to it.refreshIntervalSeconds,
                KEY_MIN_REFRESH_SECONDS to it.minRefreshSeconds,
                KEY_MAX_ARRIVAL_MINUTES to it.maxArrivalMinutes,
                KEY_SCAN_ENABLED to it.scanEnabled,
            )
        }
    }
}
