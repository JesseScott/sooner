package tt.co.jesses.sooner.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tt.co.jesses.sooner.arrivals.Arrival
import tt.co.jesses.sooner.arrivals.ArrivalsException
import tt.co.jesses.sooner.arrivals.ArrivalsSource
import tt.co.jesses.sooner.arrivals.Stop
import tt.co.jesses.sooner.transit.TransitData

data class ArrivalsUiState(
    /** What the user has typed: digits only, at most 5. */
    val input: String = "",
    val stop: Stop? = null,
    val arrivals: List<Arrival> = emptyList(),
    val loading: Boolean = false,
    /** A problem to show, such as an unknown stop number or a network error. */
    val message: String? = null,
    /** When arrivals were last fetched, Unix seconds; null until the first fetch. */
    val updatedAt: Long? = null,
)

/**
 * The arrivals screen's logic, kept free of Android classes so it can be unit
 * tested. ArrivalsViewModel wraps it.
 */
class ArrivalsController(
    private val scope: CoroutineScope,
    private val loadTransit: suspend () -> TransitData,
    private val sourceFor: (TransitData) -> ArrivalsSource,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _state = MutableStateFlow(ArrivalsUiState())
    val state: StateFlow<ArrivalsUiState> = _state.asStateFlow()

    private val transitLock = Mutex()
    private var transit: TransitData? = null
    private var source: ArrivalsSource? = null
    private val refreshLock = Mutex()

    fun onInputChange(text: String) {
        val digits = text.filter { it.isDigit() }.take(STOP_CODE_LENGTH)
        _state.update { it.copy(input = digits) }
    }

    /** Looks up the typed stop number. The screen then calls [refresh] while it's visible. */
    fun submit() {
        val code = _state.value.input
        scope.launch {
            val data = transit()
            val stop = data.stop(code)
            _state.update {
                if (stop == null) {
                    it.copy(stop = null, arrivals = emptyList(), updatedAt = null, message = "No bus stop has the number $code")
                } else {
                    it.copy(stop = stop, arrivals = emptyList(), updatedAt = null, message = null)
                }
            }
        }
    }

    /**
     * Fetches arrivals for the current stop. Unless [force], does nothing if the
     * last fetch was under [MIN_REFRESH_SECONDS] ago, so returning to the app
     * doesn't download the 400 KB feed again straight away.
     */
    fun refresh(force: Boolean = false) {
        val stop = _state.value.stop ?: return
        scope.launch {
            if (!refreshLock.tryLock()) return@launch
            try {
                val last = _state.value.updatedAt
                if (!force && last != null && nowEpochSeconds() - last < MIN_REFRESH_SECONDS) return@launch
                _state.update { it.copy(loading = true) }
                val result = try {
                    Result.success(arrivalsSource().arrivals(stop))
                } catch (e: ArrivalsException) {
                    Result.failure(e)
                }
                _state.update { current ->
                    if (current.stop != stop) return@update current.copy(loading = false) // stop changed meanwhile
                    result.fold(
                        onSuccess = { current.copy(arrivals = it, loading = false, message = null, updatedAt = nowEpochSeconds()) },
                        onFailure = { current.copy(loading = false, message = it.message) },
                    )
                }
            } finally {
                refreshLock.unlock()
            }
        }
    }

    private suspend fun transit(): TransitData = transitLock.withLock {
        transit ?: loadTransit().also { transit = it }
    }

    private suspend fun arrivalsSource(): ArrivalsSource =
        source ?: sourceFor(transit()).also { source = it }

    companion object {
        const val STOP_CODE_LENGTH = 5
        const val MIN_REFRESH_SECONDS = 30L
        const val REFRESH_INTERVAL_MILLIS = 60_000L
    }
}

/** "Now" under a minute, else "8 min". */
internal fun minutesText(minutes: Long): String = if (minutes < 1) "Now" else "$minutes min"
