package tt.co.jesses.sooner.arrivals

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tt.co.jesses.sooner.transit.TransitData

/**
 * Live arrivals from TransLink's GTFS-realtime V3 trip-updates feed. The feed
 * covers the whole system and only includes trips that already have a bus on
 * them, which is exactly "buses on the road" for this app.
 *
 * Each call downloads the full feed (about 400 KB; the server doesn't gzip),
 * so callers should refresh about once a minute and only while visible.
 */
class GtfsRealtimeArrivalsSource(
    private val transit: TransitData,
    private val fetchFeed: suspend () -> ByteArray,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : ArrivalsSource {

    constructor(apiKey: String, transit: TransitData) : this(transit, { downloadFeed(apiKey) })

    override suspend fun arrivals(stop: Stop): List<Arrival> {
        val snapshot = try {
            GtfsRealtime.predictionsAt(fetchFeed(), stop.id)
        } catch (e: IllegalArgumentException) {
            throw ArrivalsException("The arrivals feed couldn't be read", e)
        } catch (e: IllegalStateException) {
            throw ArrivalsException("The arrivals feed couldn't be read", e)
        }
        // A bus at or just past the stop is still worth showing as "0 min".
        val cutoff = nowEpochSeconds() - GRACE_SECONDS
        return snapshot.predictions
            .filter { it.time >= cutoff }
            .map {
                val route = transit.routeName(it.routeId)
                Arrival(
                    route = route,
                    // GTFS headsigns repeat the route ("20 Victoria/To SE Marine Dr"); the route is shown separately.
                    headsign = transit.headsign(it.tripId).orEmpty().removePrefix("$route "),
                    arrivalEpochSeconds = it.time,
                    vehicle = it.vehicleLabel,
                )
            }
            .sortedBy { it.arrivalEpochSeconds }
    }

    companion object {
        private const val GRACE_SECONDS = 60L
        private const val FEED_URL = "https://gtfsapi.translink.ca/v3/gtfsrealtime"

        /** Downloads the feed. Errors never include the URL, because it carries the key. */
        suspend fun downloadFeed(apiKey: String): ByteArray = withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                throw ArrivalsException("No TransLink API key. Add translink.apiKey to local.properties.")
            }
            val url = URL("$FEED_URL?apikey=${URLEncoder.encode(apiKey, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readBytes() }
                    HttpURLConnection.HTTP_FORBIDDEN ->
                        throw ArrivalsException("TransLink rejected the API key (HTTP 403)")
                    else -> throw ArrivalsException("TransLink returned HTTP $code")
                }
            } catch (e: IOException) {
                // No cause attached: some IOExceptions quote the URL, which holds the key.
                throw ArrivalsException("Couldn't reach TransLink: ${e.javaClass.simpleName}")
            } finally {
                connection.disconnect()
            }
        }
    }
}
