package tt.co.jesses.sooner.ui

import kotlinx.coroutines.CancellationException
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
import tt.co.jesses.sooner.scan.OcrWord
import tt.co.jesses.sooner.scan.StopNumberPicker
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
    /** The confirm step after reading a photo; null when no photo is being read. */
    val scan: ScanUiState? = null,
)

data class ScanUiState(
    /** True while OCR runs. */
    val reading: Boolean = true,
    /** The number to confirm: the picker's guess, then whatever the user edits it to. */
    val number: String = "",
    /** Every 5-digit number found, best first, to offer as one-tap alternatives. */
    val candidates: List<String> = emptyList(),
    val message: String? = null,
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

    /**
     * Reads a stop number from a photo. [readWords] runs OCR; the picker then
     * chooses the number and the user confirms or edits it with [confirmScan].
     */
    fun scan(readWords: suspend () -> List<OcrWord>) {
        _state.update { it.copy(scan = ScanUiState(reading = true)) }
        scope.launch {
            val words = try {
                readWords()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    if (it.scan == null) it
                    else it.copy(scan = ScanUiState(reading = false, message = "Couldn't read that photo. Try again, or type the number."))
                }
                return@launch
            }
            val data = transit()
            val pick = StopNumberPicker.pick(words) { data.stop(it) != null }
            _state.update {
                if (it.scan == null) return@update it // cancelled meanwhile
                it.copy(
                    scan = ScanUiState(
                        reading = false,
                        number = pick.number.orEmpty(),
                        candidates = pick.candidates,
                        message = if (pick.number == null) "No stop number found. Type it in, or try another photo." else null,
                    ),
                )
            }
        }
    }

    fun onScanFailed(message: String) {
        _state.update { it.copy(scan = ScanUiState(reading = false, message = message)) }
    }

    fun onScanNumberChange(text: String) {
        val digits = text.filter { it.isDigit() }.take(STOP_CODE_LENGTH)
        _state.update { s -> s.copy(scan = s.scan?.copy(number = digits)) }
    }

    /** Takes the confirmed number into the stop field and looks it up, as if typed. */
    fun confirmScan() {
        val number = _state.value.scan?.number ?: return
        if (number.length != STOP_CODE_LENGTH) return
        _state.update { it.copy(input = number, scan = null) }
        submit()
    }

    fun dismissScan() {
        _state.update { it.copy(scan = null) }
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
