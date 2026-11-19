package tt.co.jesses.sooner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import tt.co.jesses.sooner.BuildConfig
import tt.co.jesses.sooner.arrivals.GtfsRealtimeArrivalsSource
import tt.co.jesses.sooner.transit.loadTransitData

class ArrivalsViewModel(application: Application) : AndroidViewModel(application) {
    val controller = ArrivalsController(
        scope = viewModelScope,
        loadTransit = { loadTransitData(application) },
        sourceFor = { transit -> GtfsRealtimeArrivalsSource(BuildConfig.TRANSLINK_API_KEY, transit) },
    )
}
