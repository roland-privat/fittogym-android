package com.example.runtraining

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.runtraining.nav.AppNavHost
import com.example.runtraining.nav.Routes
import com.example.runtraining.ui.theme.RunTrainingTheme
import com.example.runtraining.util.Log
import com.example.runtraining.workout.fit.RejectReason
import com.example.runtraining.workout.import.ImportWorkoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single Activity host with intent dispatch:
 *  - cold start (no FIT data)            → SELECTION (FR-009)
 *  - ACTION_SEND / ACTION_VIEW with FIT  → run ImportWorkoutUseCase → DETAILS (FR-007/008)
 *  - rejected import                      → Snackbar, stay on SELECTION (FR-033)
 *
 * State for the screen-start decision lives in [importBootState], which is
 * a small inline flow exposed to the Compose tree.
 */
class MainActivity : ComponentActivity() {

    private val importBootState = MutableStateFlow(ImportBootState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            RunTrainingTheme {
                val state by importBootState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(state.errorMessage) {
                    val msg = state.errorMessage
                    if (msg != null) {
                        snackbarHostState.showSnackbar(msg)
                        importBootState.update { it.copy(errorMessage = null) }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        AppNavHost(
                            navController = navController,
                            startDestination = state.startDestination,
                            freshImportId = state.freshImportId,
                        )
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.fillMaxSize(),
                            snackbar = { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Inspect the launching intent for a FIT payload. If present, run the
     * import use case off the main thread and update [importBootState] so the
     * NavHost can swap its startDestination.
     */
    private fun handleIntent(intent: Intent?) {
        val uri = extractFitUri(intent) ?: return
        Log.d("MainActivity: incoming FIT uri=$uri action=${intent?.action}")

        val container = (application as RunTrainingApp).container
        val useCase: ImportWorkoutUseCase = container.importUseCase()

        lifecycleScope.launch {
            when (val r = useCase.invoke(uri)) {
                is ImportWorkoutUseCase.Result.NewImport -> {
                    importBootState.update {
                        it.copy(
                            startDestination = Routes.details(r.workoutId),
                            freshImportId = r.workoutId,
                            errorMessage = null,
                        )
                    }
                }
                is ImportWorkoutUseCase.Result.AlreadyImported -> {
                    importBootState.update {
                        it.copy(
                            startDestination = Routes.details(r.workoutId),
                            freshImportId = r.workoutId,
                            errorMessage = null,
                        )
                    }
                }
                is ImportWorkoutUseCase.Result.Rejected -> {
                    importBootState.update {
                        it.copy(
                            startDestination = Routes.SELECTION,
                            freshImportId = null,
                            errorMessage = humanReason(r.reason),
                        )
                    }
                }
            }
        }
    }

    private fun extractFitUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun humanReason(reason: RejectReason): String = when (reason) {
        RejectReason.NOT_A_FIT_FILE -> "That doesn't look like a FIT file."
        RejectReason.NOT_A_WORKOUT_FILE -> "That's a FIT file but not a workout."
        RejectReason.NOT_RUNNING -> "Only running workouts are supported in this version."
        RejectReason.NO_STEPS -> "This workout has no steps."
        RejectReason.MALFORMED -> "Couldn't read this workout file."
    }
}

/** Boot-time state shared between intent dispatch and Compose. */
private data class ImportBootState(
    val startDestination: String = Routes.SELECTION,
    val freshImportId: Long? = null,
    val errorMessage: String? = null,
)
