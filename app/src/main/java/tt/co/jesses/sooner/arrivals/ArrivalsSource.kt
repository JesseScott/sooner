package tt.co.jesses.sooner.arrivals

/**
 * Where arrival times come from. Sooner shows only buses already on the road,
 * so implementations return live predictions, not timetable entries.
 */
interface ArrivalsSource {
    /** Upcoming arrivals at [stop], soonest first. */
    suspend fun arrivals(stop: Stop): List<Arrival>
}

/** Arrivals couldn't be fetched. [message] is safe to show and never contains the API key. */
class ArrivalsException(message: String, cause: Throwable? = null) : Exception(message, cause)
