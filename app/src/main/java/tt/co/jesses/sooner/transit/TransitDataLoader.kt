package tt.co.jesses.sooner.transit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads [TransitData] from the app's assets/transit folder. Call once and keep the result. */
suspend fun loadTransitData(context: Context): TransitData = withContext(Dispatchers.IO) {
    val assets = context.assets
    fun open(name: String) = assets.open("transit/$name").bufferedReader(Charsets.UTF_8)
    open("stops.tsv").use { stops ->
        open("routes.tsv").use { routes ->
            open("headsigns.tsv").use { headsigns ->
                open("trips.tsv").use { trips -> TransitData.parse(stops, routes, headsigns, trips) }
            }
        }
    }
}
