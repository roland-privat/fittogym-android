package com.example.runtraining.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.runtraining.workout.engine.RunControlsState

enum class RunControlsDirection { HORIZONTAL, VERTICAL }

/**
 * Five controls per Spec FR-019. Layout switches between a horizontal row
 * (portrait) and a vertical column (landscape — placed on the right edge).
 */
@Composable
fun RunControls(
    controls: RunControlsState,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    modifier: Modifier = Modifier,
    direction: RunControlsDirection = RunControlsDirection.HORIZONTAL,
) {
    val buttons: @Composable () -> Unit = {
        FilledTonalIconButton(
            onClick = onStepBackward,
            enabled = controls.stepBackwardEnabled,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Step back") }

        if (isRunning) {
            FilledTonalIconButton(
                onClick = onPause,
                enabled = controls.pauseEnabled,
                modifier = Modifier.size(72.dp),
            ) { Icon(Icons.Filled.Pause, contentDescription = "Pause") }
        } else {
            FilledTonalIconButton(
                onClick = onStart,
                enabled = controls.startEnabled,
                modifier = Modifier.size(72.dp),
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Start") }
        }

        FilledTonalIconButton(
            onClick = onStop,
            enabled = controls.stopEnabled,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }

        FilledTonalIconButton(
            onClick = onStepForward,
            enabled = controls.stepForwardEnabled,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Filled.SkipNext, contentDescription = "Step forward") }
    }

    when (direction) {
        RunControlsDirection.HORIZONTAL -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { buttons() }

        RunControlsDirection.VERTICAL -> Column(
            modifier = modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { buttons() }
    }
}
