package tt.co.jesses.sooner.arrivals

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import tt.co.jesses.sooner.transit.TransitData

class GtfsRealtimeArrivalsSourceTest {
    private val now = 1_795_065_842L
    private val stop = Stop("51238", "1248", "Southbound Commercial Dr @ William St", listOf("20", "N20"))
    private val transit = TransitData.parse(
        stops = "51238\t1248\tSouthbound Commercial Dr @ William St\t6625 6835\n".reader().buffered(),
        routes = "6625\t20\tVictoria/Downtown\n6835\tN20\tNightbus\n".reader().buffered(),
        headsigns = "20 Victoria/to 41st Ave\n20 Victoria/To SE Marine Dr\n".reader().buffered(),
        trips = "15529229\t15529229\t0\n15529230\t15529231\t1\n".reader().buffered(),
    )

    private fun source(bytes: ByteArray) = GtfsRealtimeArrivalsSource(transit, { bytes }, { now })

    @Test
    fun labelsAndSortsSoonestFirst() = runBlocking {
        val bytes = feed(now) {
            tripUpdate("15529231", "6625", "2532") { stopTime("1248", now + 1400) }
            tripUpdate("15529229", "6625", "2531") { stopTime("1248", now + 534) }
            tripUpdate("99", "6835") { stopTime("1248", now + 900) }
        }
        val arrivals = source(bytes).arrivals(stop)
        assertEquals(
            listOf(
                Arrival("20", "Victoria/to 41st Ave", now + 534, "2531"),
                Arrival("N20", "", now + 900, null),
                Arrival("20", "Victoria/To SE Marine Dr", now + 1400, "2532"),
            ),
            arrivals,
        )
        assertEquals(listOf(8L, 15L, 23L), arrivals.map { it.minutesUntil(now) })
    }

    @Test
    fun keepsABusAtTheStopButDropsOnesLongGone() = runBlocking {
        val bytes = feed(now) {
            tripUpdate("1", "6625") { stopTime("1248", now - 30) }
            tripUpdate("2", "6625") { stopTime("1248", now - 300) }
        }
        assertEquals(listOf(now - 30), source(bytes).arrivals(stop).map { it.arrivalEpochSeconds })
    }

    @Test(expected = ArrivalsException::class)
    fun garbageFeedBecomesArrivalsException(): Unit = runBlocking {
        source(byteArrayOf(0x0A, 0x7F)).arrivals(stop)
    }

    @Test(expected = ArrivalsException::class)
    fun blankKeyIsReportedWithoutANetworkCall(): Unit = runBlocking {
        GtfsRealtimeArrivalsSource.downloadFeed(" ")
    }
}
