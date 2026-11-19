package tt.co.jesses.sooner.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsRealtimeTest {
    private val now = 1_795_065_842L

    @Test
    fun readsHeaderTimestampAndPredictionsAtStop() {
        val bytes = feed(now) {
            tripUpdate("15529229", "6625", vehicleLabel = "2531") {
                stopTime("1247", now + 400)
                stopTime("1248", now + 534)
                stopTime("1249", now + 600)
            }
            tripUpdate("15529300", "6835") { stopTime("9999", now + 100) }
        }
        val snapshot = GtfsRealtime.predictionsAt(bytes, "1248")
        assertEquals(now, snapshot.timestamp)
        assertEquals(listOf(GtfsRealtime.Prediction("15529229", "6625", now + 534, "2531")), snapshot.predictions)
    }

    @Test
    fun dropsCanceledTripsAndSkippedStops() {
        val bytes = feed(now) {
            tripUpdate("15529221", "6625", canceled = true) { stopTime("1248", null, skipped = true) }
            tripUpdate("15529222", "6625", canceled = true) { stopTime("1248", now + 60) }
            tripUpdate("15529223", "6625") { stopTime("1248", now + 120, skipped = true) }
        }
        assertTrue(GtfsRealtime.predictionsAt(bytes, "1248").predictions.isEmpty())
    }

    @Test
    fun fallsBackToDepartureTime() {
        val bytes = feed(now) { tripUpdate("1", "6625") { stopTime("1248", arrival = null, departure = now + 90) } }
        assertEquals(now + 90, GtfsRealtime.predictionsAt(bytes, "1248").predictions.single().time)
    }

    @Test
    fun stopIdMustMatchExactly() {
        val bytes = feed(now) { tripUpdate("1", "6625") { stopTime("12480", now + 90); stopTime("124", now + 95) } }
        assertTrue(GtfsRealtime.predictionsAt(bytes, "1248").predictions.isEmpty())
    }

    @Test
    fun skipsUnknownFieldsOfEveryWireType() {
        val bytes = feed(now) {
            fixed32(99, 7)
            tripUpdate("1", "6625") { stopTime("1248", now + 90) }
        }
        assertEquals(1, GtfsRealtime.predictionsAt(bytes, "1248").predictions.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedFeed() {
        val bytes = feed(now) { tripUpdate("1", "6625") { stopTime("1248", now + 90) } }
        GtfsRealtime.predictionsAt(bytes.copyOf(bytes.size - 3), "1248")
    }
}
