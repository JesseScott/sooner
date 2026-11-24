package tt.co.jesses.sooner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tt.co.jesses.sooner.arrivals.Arrival
import tt.co.jesses.sooner.arrivals.Stop
import tt.co.jesses.sooner.scan.CameraCaptureScreen
import tt.co.jesses.sooner.scan.StopSignReader
import tt.co.jesses.sooner.ui.theme.SoonerTheme

@Composable
fun ArrivalsRoute(
    controller: ArrivalsController,
    reader: StopSignReader,
    modifier: Modifier = Modifier,
    scanEnabled: Boolean = true,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current

    var showCamera by rememberSaveable { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCamera = true
        } else {
            controller.onScanFailed("Sooner needs the camera to photograph the sign. You can choose a photo instead.")
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) controller.scan { withContext(Dispatchers.IO) { reader.read(context, uri) } }
    }
    val takePhoto = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    val choosePhoto = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (showCamera) {
        BackHandler { showCamera = false }
        CameraCaptureScreen(
            onCaptured = { photo ->
                showCamera = false
                controller.scan { reader.read(photo) }
            },
            onError = {
                showCamera = false
                controller.onScanFailed(it)
            },
            onClose = { showCamera = false },
            modifier = modifier,
        )
        return
    }

    state.scan?.let { scan ->
        ScanConfirmDialog(
            scan = scan,
            onNumberChange = controller::onScanNumberChange,
            onConfirm = controller::confirmScan,
            onRetake = {
                controller.dismissScan()
                takePhoto()
            },
            onDismiss = controller::dismissScan,
        )
    }

    // Refresh about once a minute (Remote Config can change it), only while the screen is visible.
    LaunchedEffect(state.stop?.id) {
        if (state.stop == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                controller.refresh()
                delay(controller.refreshIntervalMillis)
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
        onTakePhoto = takePhoto,
        onChoosePhoto = choosePhoto,
        modifier = modifier,
        scanEnabled = scanEnabled,
    )
}

/** Shows the number read from the photo for the user to confirm or fix. */
@Composable
private fun ScanConfirmDialog(
    scan: ScanUiState,
    onNumberChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canConfirm = !scan.reading && scan.number.length == ArrivalsController.STOP_CODE_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scan.reading) "Reading the sign…" else "Is this the stop number?") },
        text = {
            if (scan.reading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column {
                    scan.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.padding(top = 8.dp))
                    }
                    OutlinedTextField(
                        value = scan.number,
                        onValueChange = onNumberChange,
                        label = { Text("Bus stop number") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (canConfirm) onConfirm() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val others = scan.candidates.filter { it != scan.number }
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.padding(top = 8.dp))
                        Text(
                            "Other numbers on the sign",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            others.take(3).forEach { number ->
                                SuggestionChip(onClick = { onNumberChange(number) }, label = { Text(number) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text("Show buses") }
        },
        dismissButton = {
            if (!scan.reading) TextButton(onClick = onRetake) { Text("Retake") }
        },
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
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    scanEnabled: Boolean = true,
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
        if (scanEnabled) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto) { Text("Photo of sign") }
                OutlinedButton(onClick = onChoosePhoto) { Text("Choose photo") }
            }
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
            onTakePhoto = {},
            onChoosePhoto = {},
        )
    }
}
