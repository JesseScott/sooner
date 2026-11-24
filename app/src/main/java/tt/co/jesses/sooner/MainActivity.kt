package tt.co.jesses.sooner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tt.co.jesses.sooner.config.SoonerFirebase
import tt.co.jesses.sooner.ui.ArrivalsRoute
import tt.co.jesses.sooner.ui.ArrivalsViewModel
import tt.co.jesses.sooner.ui.theme.SoonerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ArrivalsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val flags by SoonerFirebase.flags.collectAsStateWithLifecycle()
            SoonerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ArrivalsRoute(viewModel.controller, viewModel.reader, Modifier.padding(padding), scanEnabled = flags.scanEnabled)
                }
            }
        }
    }
}
