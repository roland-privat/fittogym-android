package com.example.runtraining.ui.run

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtraining.ui.common.IntensityBadge
import com.example.runtraining.util.Format
import com.example.runtraining.workout.engine.RunState
import com.example.runtraining.workout.engine.RunUiState
import com.example.runtraining.workout.model.Workout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    onBack: () -> Unit,
    onWorkoutComplete: (workoutId: Long, stoppedEarly: Boolean) -> Unit,
    viewModel: RunViewModel = viewModel(factory = RunViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    var workout by remember { mutableStateOf<Workout?>(null) }
    LaunchedEffect(Unit) { viewModel.workoutAsync { workout = it } }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            val w = activity?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(w, true)
            val controller = WindowInsetsControllerCompat(w, w.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state?.state) {
        if (state?.state == RunState.COMPLETE) {
            onWorkoutComplete(workout?.id ?: 0L, state?.wasStoppedEarly == true)
        }
    }

    // FR-031 + UX: when the user is on the Run page, the overlay is redundant.
    // Dismiss it whenever the screen resumes (covers tap-on-overlay,
    // Recents return, launcher relaunch).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.disableMiniView()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { onBack() }

    // Stop confirmation dialog.
    var stopConfirmVisible by remember { mutableStateOf(false) }

    // Mini view permission flow (Spec FR-029).
    var rationaleVisible by remember { mutableStateOf(false) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        if (Settings.canDrawOverlays(context)) {
            viewModel.enableMiniView()
            activity?.moveTaskToBack(true)
        } else {
            Toast.makeText(
                context,
                "Permission not granted \u2014 mini view won't appear.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val onMiniViewToggle: () -> Unit = {
        if (Settings.canDrawOverlays(context)) {
            viewModel.enableMiniView()
            // Common practice: send the main window to the background so the
            // floating mini view is immediately visible over whatever is
            // underneath (home screen / previous app).
            activity?.moveTaskToBack(true)
        } else {
            rationaleVisible = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.workoutDisplayName ?: "Workout run") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onMiniViewToggle) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPictureAlt,
                            contentDescription = "Mini view",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val s = state
        if (s == null || workout == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) { Text("Loading\u2026") }
            return@Scaffold
        }

        val orientation = LocalConfiguration.current.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            LandscapeBody(
                state = s,
                workout = workout!!,
                primaryColor = MaterialTheme.colorScheme.primary,
                contentPadding = padding,
                onAction = viewModel.actions(askToStop = { stopConfirmVisible = true }),
            )
        } else {
            PortraitBody(
                state = s,
                workout = workout!!,
                primaryColor = MaterialTheme.colorScheme.primary,
                contentPadding = padding,
                onAction = viewModel.actions(askToStop = { stopConfirmVisible = true }),
            )
        }
    }

    if (stopConfirmVisible) {
        AlertDialog(
            onDismissRequest = { stopConfirmVisible = false },
            title = { Text("End workout?") },
            text = {
                Text("Stopping will end the workout and show your summary. You can come back to the workout list afterwards.")
            },
            confirmButton = {
                TextButton(onClick = {
                    stopConfirmVisible = false
                    viewModel.onStop()  // engine transitions to COMPLETE; state listener navigates to summary.
                }) { Text("End workout") }
            },
            dismissButton = {
                TextButton(onClick = { stopConfirmVisible = false }) { Text("Back") }
            },
        )
    }

    if (rationaleVisible) {
        AlertDialog(
            onDismissRequest = { rationaleVisible = false },
            title = { Text("Enable mini view?") },
            text = {
                Text(
                    "The mini view is a small floating window that stays on top of other apps so " +
                        "you can watch your timer, target pace, and lap counter while using a music " +
                        "app or anything else.\n\nAndroid needs you to grant the system-overlay " +
                        "permission to this app once. We'll open the settings page next.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rationaleVisible = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    overlayPermissionLauncher.launch(intent)
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { rationaleVisible = false }) { Text("Not now") }
            },
        )
    }
}

// ============================================================================
// PORTRAIT
// ============================================================================

@Composable
private fun PortraitBody(
    state: RunUiState,
    workout: Workout,
    primaryColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    onAction: RunActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimelineChart(
            workout = workout,
            playheadFraction = state.playheadFraction,
            height = 110.dp,
            barColor = primaryColor,
        )
        CurrentStepCard(state = state)
        NextUpCard(state = state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverallCard(state = state, modifier = Modifier.weight(1f))
            HrCard(state = state, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.weight(1f))

        RunControls(
            controls = state.controls,
            isRunning = state.state == RunState.RUNNING,
            onStart = onAction.start,
            onPause = onAction.pause,
            onStop = onAction.stop,
            onStepForward = onAction.stepForward,
            onStepBackward = onAction.stepBackward,
        )
    }
}

// ============================================================================
// LANDSCAPE
// ============================================================================

@Composable
private fun LandscapeBody(
    state: RunUiState,
    workout: Workout,
    primaryColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    onAction: RunActions,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // LEFT CONTENT — timeline up top, then the metric cards row.
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimelineChart(
                workout = workout,
                playheadFraction = state.playheadFraction,
                height = 84.dp,
                barColor = primaryColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Big current-step card.
                Box(modifier = Modifier.weight(0.62f)) {
                    CurrentStepCard(state = state)
                }
                // Supporting cards stacked.
                Column(
                    modifier = Modifier.weight(0.38f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NextUpCard(state = state)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OverallCard(state = state, modifier = Modifier.weight(1f))
                        HrCard(state = state, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        // RIGHT RAIL — full-height vertical controls. Distribute evenly so
        // all four buttons are visible regardless of screen height.
        RunControls(
            controls = state.controls,
            isRunning = state.state == RunState.RUNNING,
            onStart = onAction.start,
            onPause = onAction.pause,
            onStop = onAction.stop,
            onStepForward = onAction.stepForward,
            onStepBackward = onAction.stepBackward,
            direction = RunControlsDirection.VERTICAL,
            modifier = Modifier
                .weight(0.15f)
                .fillMaxHeight(),
        )
    }
}

// ============================================================================
// CARDS
// ============================================================================

@Composable
private fun CurrentStepCard(state: RunUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row: step n of N · name · intensity badge · lap "3 of 6".
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Step ${state.currentStepIndex} of ${state.totalAuthoredSteps}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = state.currentStepName ?: "\u2014",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IntensityBadge(state.currentIntensity)
                if (state.currentRepeatLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lap ${state.currentRepeatLabel}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BIG countdown.
            Text(
                text = Format.formatDuration(state.stepRemainingSec),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )

            // "left in step · 0:26 elapsed" sub-line.
            Text(
                text = "left in step \u00b7 ${Format.formatDuration(state.stepElapsedSec)} elapsed",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Target inline with the big number — what the user asked for.
            KeyValue(label = "Target", value = state.currentTargetDisplay, big = true)
        }
    }
}

@Composable
private fun NextUpCard(state: RunUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.nextTargetDisplay ?: "\u2014  Final step",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OverallCard(state: RunUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Overall",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Format.formatDuration(state.sessionElapsedSec),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${Format.formatDuration(state.sessionRemainingSec)} left",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HrCard(state: RunUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Heart rate",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val (big, small) = when {
                state.hrBpm != null -> state.hrBpm.toString() to "bpm"
                state.hrSignalLost -> "\u2014" to "signal lost"
                else -> "\u2014" to "no HRM"
            }
            Text(
                text = big,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = small, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ============================================================================
// LITTLE BITS
// ============================================================================

@Composable
private fun KeyValue(label: String, value: String, big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Action bundle for the controls; built once per recomposition. */
private data class RunActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val stop: () -> Unit,
    val stepForward: () -> Unit,
    val stepBackward: () -> Unit,
)

private fun RunViewModel.actions(askToStop: () -> Unit): RunActions = RunActions(
    start = ::onStart,
    pause = ::onPause,
    stop = askToStop,
    stepForward = ::onStepForward,
    stepBackward = ::onStepBackward,
)
