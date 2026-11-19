package tt.co.jesses.sooner.arrivals

/** One predicted or scheduled bus arrival at a stop. */
data class Arrival(
    /** Route as shown to riders, e.g. "20" or "N20". */
    val route: String,
    /** Trip headsign, e.g. "Victoria/to 41st Ave". Kept visible because one route can have several. */
    val headsign: String,
    /** Arrival time as Unix epoch seconds. */
    val arrivalEpochSeconds: Long,
    /** True for a real-time prediction, false for a scheduled time. */
    val isLive: Boolean,
) {
    /** Whole minutes from [nowEpochSeconds] until arrival, never below zero. */
    fun minutesUntil(nowEpochSeconds: Long): Long =
        ((arrivalEpochSeconds - nowEpochSeconds).coerceAtLeast(0)) / 60
}

/** A stop as identified by the bundled stop table. */
data class Stop(
    /** The 5-digit number printed on the sign (GTFS stop_code). */
    val code: String,
    /** TransLink's internal stop_id used by the real-time APIs. */
    val id: String,
    val name: String,
    /** Routes serving the stop, as shown to riders. */
    val routes: List<String>,
)
