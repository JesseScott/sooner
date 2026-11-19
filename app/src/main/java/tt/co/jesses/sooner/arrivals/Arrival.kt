package tt.co.jesses.sooner.arrivals

/** One bus that is on the road and predicted to reach the stop. */
data class Arrival(
    /** Route as shown to riders, e.g. "20" or "N20". */
    val route: String,
    /** Trip headsign, e.g. "Victoria/to 41st Ave", or "" if unknown. Kept visible because one route can have several. */
    val headsign: String,
    /** Predicted arrival as Unix epoch seconds. */
    val arrivalEpochSeconds: Long,
    /** Bus number, if the feed gives one. */
    val vehicle: String? = null,
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
