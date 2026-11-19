package tt.co.jesses.sooner.arrivals

/**
 * Where arrival times come from. Implementations (the official GTFS-realtime
 * feed, or translink.ca's internal endpoint) are swappable behind this interface.
 */
interface ArrivalsSource {
    /** Upcoming arrivals at [stop], soonest first. */
    suspend fun arrivals(stop: Stop): List<Arrival>
}
