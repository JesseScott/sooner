package tt.co.jesses.sooner.ui

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tt.co.jesses.sooner.arrivals.Arrival
import tt.co.jesses.sooner.arrivals.ArrivalsException
import tt.co.jesses.sooner.arrivals.ArrivalsSource
import tt.co.jesses.sooner.arrivals.Stop
import tt.co.jesses.sooner.transit.TransitData

class ArrivalsControllerTest {
    private var now = 1_795_065_842L
    private var fetches = 0
    private var next: () -> List<Arrival> = { listOf(Arrival("20", "Victoria/To SE Marine Dr", now + 534, "2531")) }
    private var transitLoads = 0

    private val transit = TransitData.parse(
        "51238\t1248\tSouthbound Commercial Dr @ William St\t6625 6835\n".reader().buffered(),
        "6625\t20\tVictoria\n6835\tN20\tNightbus\n".reader().buffered(),
        "".reader().buffered(),
        "".reader().buffered(),
    )

    private fun TestScope.controller() = ArrivalsController(
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        loadTransit = { transitLoads++; transit },
        sourceFor = {
            object : ArrivalsSource {
                override suspend fun arrivals(stop: Stop): List<Arrival> {
                    fetches++
                    return next()
                }
            }
        },
        nowEpochSeconds = { now },
    )

    @Test
    fun inputKeepsFiveDigitsOnly() = runTest {
        val c = controller()
        c.onInputChange("51a23-8 99")
        assertEquals("51238", c.state.value.input)
    }

    @Test
    fun submitFindsStopThenRefreshLoadsArrivals() = runTest {
        val c = controller()
        c.onInputChange("51238")
        c.submit()
        assertEquals("1248", c.state.value.stop?.id)
        assertEquals(listOf("20", "N20"), c.state.value.stop?.routes)
        c.refresh()
        assertEquals(1, c.state.value.arrivals.size)
        assertEquals(now, c.state.value.updatedAt)
        assertNull(c.state.value.message)
    }

    @Test
    fun unknownStopShowsMessage() = runTest {
        val c = controller()
        c.onInputChange("99999")
        c.submit()
        assertNull(c.state.value.stop)
        assertEquals("No bus stop has the number 99999", c.state.value.message)
        c.refresh()
        assertEquals(0, fetches)
    }

    @Test
    fun automaticRefreshSkipsWhenRecentButForcedDoesNot() = runTest {
        val c = controller()
        c.onInputChange("51238")
        c.submit()
        c.refresh()
        now += 10
        c.refresh()
        assertEquals(1, fetches)
        c.refresh(force = true)
        assertEquals(2, fetches)
        now += ArrivalsController.MIN_REFRESH_SECONDS
        c.refresh()
        assertEquals(3, fetches)
    }

    @Test
    fun errorKeepsLastArrivalsAndShowsMessage() = runTest {
        val c = controller()
        c.onInputChange("51238")
        c.submit()
        c.refresh()
        next = { throw ArrivalsException("TransLink rejected the API key (HTTP 403)") }
        c.refresh(force = true)
        assertEquals("TransLink rejected the API key (HTTP 403)", c.state.value.message)
        assertEquals(1, c.state.value.arrivals.size)
        assertEquals(false, c.state.value.loading)
    }

    @Test
    fun transitTablesLoadOnce() = runTest {
        val c = controller()
        c.onInputChange("51238")
        c.submit()
        c.refresh()
        c.submit()
        assertEquals(1, transitLoads)
    }

    @Test
    fun minutesTextSaysNowUnderAMinute() {
        assertEquals("Now", minutesText(0))
        assertEquals("8 min", minutesText(8))
    }
}
