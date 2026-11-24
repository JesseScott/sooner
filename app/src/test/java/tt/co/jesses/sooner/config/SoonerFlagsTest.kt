package tt.co.jesses.sooner.config

import org.junit.Assert.assertEquals
import org.junit.Test

class SoonerFlagsTest {
    @Test
    fun defaultsAreAlreadySane() {
        assertEquals(SoonerFlags(), SoonerFlags().sanitized())
    }

    @Test
    fun sanitizedClampsConsoleTypos() {
        val flags = SoonerFlags(refreshIntervalSeconds = 1, minRefreshSeconds = 0, maxArrivalMinutes = 100_000).sanitized()
        assertEquals(15L, flags.refreshIntervalSeconds)
        assertEquals(10L, flags.minRefreshSeconds)
        assertEquals(240L, flags.maxArrivalMinutes)
    }

    @Test
    fun defaultsMapCoversEveryKey() {
        assertEquals(
            setOf("arrivals_refresh_interval_seconds", "arrivals_min_refresh_seconds", "arrivals_max_minutes", "scan_enabled"),
            SoonerFlags.defaults().keys,
        )
    }
}
