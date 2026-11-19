package tt.co.jesses.sooner.arrivals

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalTest {
    private fun arrivalAt(epoch: Long) = Arrival("20", "To SE Marine Dr", epoch, isLive = true)

    @Test
    fun minutesUntilRoundsDown() {
        assertEquals(4, arrivalAt(1_000 + 299).minutesUntil(1_000))
    }

    @Test
    fun minutesUntilNeverNegative() {
        assertEquals(0, arrivalAt(900).minutesUntil(1_000))
    }
}
