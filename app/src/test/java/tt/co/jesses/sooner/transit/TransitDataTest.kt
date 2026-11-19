package tt.co.jesses.sooner.transit

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class TransitDataTest {
    // Same shape as tools/build_transit_data.py writes.
    private val data = TransitData.parse(
        stops = """
            50001	5	Unserved stop	
            51238	1248	Southbound Commercial Dr @ William St	6625 6835
        """.trimIndent().reader().buffered(),
        routes = """
            6625	20	Victoria/Downtown
            6835	N20	Downtown/Victoria Nightbus
        """.trimIndent().reader().buffered(),
        // Headsign 0 is empty, as one real trip has.
        headsigns = "\nVictoria/to 41st Ave\nTo SE Marine Dr\n".reader().buffered(),
        trips = """
            48441	48441	0
            15529220	15529226	1
            15529227	15529227	2
        """.trimIndent().reader().buffered(),
    )

    @Test
    fun findsStopBySignNumber() {
        val stop = data.stop("51238")!!
        assertEquals("1248", stop.id)
        assertEquals("Southbound Commercial Dr @ William St", stop.name)
        assertEquals(listOf("20", "N20"), stop.routes)
    }

    @Test
    fun trimsTypedNumber() {
        assertEquals("1248", data.stop(" 51238 ")?.id)
    }

    @Test
    fun stopWithNoRoutesHasEmptyList() {
        assertEquals(emptyList<String>(), data.stop("50001")!!.routes)
    }

    @Test
    fun unknownStopIsNull() {
        assertNull(data.stop("99999"))
    }

    @Test
    fun labelsTrips() {
        assertEquals("20", data.routeName("6625"))
        assertEquals("unknown-route", data.routeName("unknown-route"))
        assertEquals("Victoria/to 41st Ave", data.headsign("15529220"))
        assertEquals("Victoria/to 41st Ave", data.headsign("15529226"))
        assertEquals("To SE Marine Dr", data.headsign("15529227"))
        assertNull(data.headsign("15529228"))
        assertNull(data.headsign("15529219"))
        assertNull(data.headsign("48441"))
        assertNull(data.headsign("nope"))
    }

    /** Checks the generated tables shipped in the app, not just the parser. */
    @Test
    fun bundledTablesParseAndKnowCommercialAndWilliam() {
        val dir = File("src/main/assets/transit")
        fun open(name: String) = File(dir, name).bufferedReader()
        val bundled = TransitData.parse(open("stops.tsv"), open("routes.tsv"), open("headsigns.tsv"), open("trips.tsv"))
        assertTrue(bundled.stopCount > 8_000)
        val stop = bundled.stop("51238")!!
        assertEquals("1248", stop.id)
        assertEquals(listOf("20", "N20"), stop.routes)
    }
}
