package tt.co.jesses.sooner.transit

import java.io.BufferedReader
import tt.co.jesses.sooner.arrivals.Stop

/**
 * The static tables bundled in the app, built by tools/build_transit_data.py
 * from TransLink's GTFS. Answers "which stop is sign number X" and labels live
 * trips with their route name and headsign, with no network call.
 */
class TransitData(
    private val stopsByCode: Map<String, StopRecord>,
    private val routes: Map<String, RouteRecord>,
    private val tripHeadsigns: TripHeadsigns,
) {
    data class StopRecord(val code: String, val id: String, val name: String, val routeIds: List<String>)
    data class RouteRecord(val id: String, val displayName: String, val longName: String)

    /** The stop whose sign shows [code], or null if no stop has that number. */
    fun stop(code: String): Stop? {
        val record = stopsByCode[code.trim()] ?: return null
        return Stop(
            code = record.code,
            id = record.id,
            name = record.name,
            routes = record.routeIds.map { routeName(it) },
        )
    }

    /** Route as shown to riders ("20", "N20"); falls back to the raw id if unknown. */
    fun routeName(routeId: String): String = routes[routeId]?.displayName ?: routeId

    /** The trip's headsign, or null if the trip isn't in the bundled schedule. */
    fun headsign(tripId: String): String? = tripHeadsigns[tripId]

    /**
     * trip_id to headsign, stored as runs of consecutive numeric trip IDs that
     * share a headsign (about 6,000 runs for 63,000 trips), found by binary search.
     */
    class TripHeadsigns(
        private val firstIds: LongArray,
        private val lastIds: LongArray,
        private val headsignIndex: IntArray,
        private val headsigns: List<String>,
    ) {
        operator fun get(tripId: String): String? {
            val id = tripId.toLongOrNull() ?: return null
            var lo = 0
            var hi = firstIds.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    id < firstIds[mid] -> hi = mid - 1
                    id > lastIds[mid] -> lo = mid + 1
                    else -> return headsigns[headsignIndex[mid]].ifEmpty { null }
                }
            }
            return null
        }
    }

    val stopCount: Int get() = stopsByCode.size

    companion object {
        /** Parses the TSV files. Each reader is read to the end but not closed. */
        fun parse(
            stops: BufferedReader,
            routes: BufferedReader,
            headsigns: BufferedReader,
            trips: BufferedReader,
        ): TransitData {
            val stopMap = HashMap<String, StopRecord>()
            stops.forEachTsv { f ->
                val routeIds = f.getOrElse(3) { "" }.split(' ').filter { it.isNotEmpty() }
                stopMap[f[0]] = StopRecord(f[0], f[1], f[2], routeIds)
            }
            val routeMap = HashMap<String, RouteRecord>()
            routes.forEachTsv { f -> routeMap[f[0]] = RouteRecord(f[0], f[1], f.getOrElse(2) { "" }) }
            // Line N is headsign N, and a headsign can be empty, so keep blank lines here.
            val headsignList = headsigns.readLines()
            val firstIds = ArrayList<Long>()
            val lastIds = ArrayList<Long>()
            val indexes = ArrayList<Int>()
            trips.forEachTsv { f ->
                firstIds += f[0].toLong()
                lastIds += f[1].toLong()
                indexes += f[2].toInt().also { require(it in headsignList.indices) { "bad headsign index $it" } }
            }
            val tripHeadsigns = TripHeadsigns(
                firstIds.toLongArray(), lastIds.toLongArray(), indexes.toIntArray(), headsignList,
            )
            return TransitData(stopMap, routeMap, tripHeadsigns)
        }

        private inline fun BufferedReader.forEachTsv(action: (List<String>) -> Unit) {
            while (true) {
                val line = readLine() ?: break
                if (line.isEmpty()) continue
                action(line.split('\t'))
            }
        }
    }
}
