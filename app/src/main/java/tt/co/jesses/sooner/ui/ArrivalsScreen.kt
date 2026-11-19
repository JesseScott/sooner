package tt.co.jesses.sooner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import tt.co.jesses.sooner.arrivals.Arrival
import tt.co.jesses.sooner.arrivals.Stop
import tt.co.jesses.sooner.ui.theme.SoonerTheme

@Composable
fun ArrivalsRoute(controller: ArrivalsController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Refresh about once a minute, only while the screen is visible.
    LaunchedEffect(state.stop?.id) {
        if (state.stop == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                controller.refresh()
                delay(ArrivalsController.REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    // Count minutes down between fetches.
    var now by remember { mutableLongStateOf(epochSeconds()) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = epochSeconds()
                delay(5_000)
            }
        }
    }

    ArrivalsScreen(
        state = state,
        nowEpochSeconds = now,
        onInputChange = controller::onInputChange,
        onSubmit = controller::submit,
        onRefresh = { controller.refresh(force = true) },
        modifier = modifier,
    )
}

private fun epochSeconds() = System.currentTimeMillis() / 1000

@Composable
fun ArrivalsScreen(
    state: ArrivalsUiState,
    nowEpochSeconds: Long,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = state.input.length == ArrivalsController.STOP_CODE_LENGTH
    val submit = {
        if (canSubmit) {
            keyboard?.hide()
            onSubmit()
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                label = { Text("Bus stop number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = submit, enabled = canSubmit) { Text("Go") }
        }

        state.stop?.let { stop ->
            Spacer(Modifier.padding(top = 16.dp))
            Text(stop.name, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stop ${stop.code} · " + updatedText(state, nowEpochSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }
        }

        state.message?.let {
            Spacer(Modifier.padding(top = 12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        val stop = state.stop
        if (stop != null && state.updatedAt != null && state.arrivals.isEmpty() && state.message == null) {
            Spacer(Modifier.padding(top = 24.dp))
            Text("No buses on the way to this stop right now.")
            if (stop.routes.isNotEmpty()) {
                Text(
                    "Routes here: ${stop.routes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(state.arrivals, key = { "${it.route}-${it.arrivalEpochSeconds}-${it.vehicle}" }) { arrival ->
                ArrivalRow(arrival, nowEpochSeconds)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ArrivalRow(arrival: Arrival, nowEpochSeconds: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            arrival.route,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(arrival.headsign.ifEmpty { "Route ${arrival.route}" }, style = MaterialTheme.typography.bodyLarge)
            arrival.vehicle?.let {
                Text(
                    "Bus $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(minutesText(arrival.minutesUntil(nowEpochSeconds)), style = MaterialTheme.typography.titleMedium)
    }
}

private fun updatedText(state: ArrivalsUiState, now: Long): String {
    val updated = state.updatedAt ?: return "loading…"
    val ago = now - updated
    return if (ago < 60) "updated just now" else "updated ${ago / 60} min ago"
}

@Preview(showBackground = true)
@Composable
private fun ArrivalsScreenPreview() {
    val now = 1_795_065_842L
    SoonerTheme {
        ArrivalsScreen(
            state = ArrivalsUiState(
                input = "51238",
                stop = Stop("51238", "1248", "Southbound Commercial Dr @ William St", listOf("20", "N20")),
                arrivals = listOf(
                    Arrival("20", "Victoria/To SE Marine Dr", now + 534, "2531"),
                    Arrival("20", "Victoria/to 41st Ave", now + 1400, "2532"),
                ),
                updatedAt = now,
            ),
            nowEpochSeconds = now,
            onInputChange = {},
            onSubmit = {},
            onRefresh = {},
        )
    }
}
