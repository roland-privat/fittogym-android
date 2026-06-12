package com.example.runtraining.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtraining.ui.common.IntensityBadge
import com.example.runtraining.ui.common.TargetDisplay
import com.example.runtraining.util.Format
import com.example.runtraining.workout.model.SourceDuration
import com.example.runtraining.workout.model.Step
import com.example.runtraining.workout.model.Workout
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    isFreshImport: Boolean,
    onBack: () -> Unit,
    onRun: (Long) -> Unit,
    viewModel: DetailsViewModel = viewModel(factory = DetailsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val workout = state.workout

    var pendingDelete by remember { mutableStateOf(false) }
    var pendingCancelImport by remember { mutableStateOf(false) }
    var name by remember(workout?.id) { mutableStateOf(workout?.displayName ?: "") }

    LaunchedEffect(workout?.id) {
        if (workout != null) name = workout.displayName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (workout == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text(if (state.isLoading) "Loading\u2026" else "Workout not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.isAlreadyImported) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Already imported — opened the existing copy.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    val canSave = name.isNotBlank() && name != workout.displayName
                    if (canSave) {
                        IconButton(onClick = { viewModel.rename(name) }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save name",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            SummaryRows(workout = workout)

            // TSS hint when threshold pace is unset.
            if (workout.tss == null && state.settings.thresholdPaceSecPerKm == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Set your threshold pace in Options to compute TSS for every workout.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            StepList(workout = workout, displayUnit = state.settings.displayUnit)

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isFreshImport) {
                    // Fresh-import flow: user is deciding whether to keep this
                    // workout in their library or discard it.
                    Button(onClick = onBack) { Text("Add to library") }
                    OutlinedButton(onClick = { pendingCancelImport = true }) { Text("Cancel") }
                } else {
                    Button(
                        enabled = workout.isRunnable,
                        onClick = { onRun(workout.id) },
                    ) { Text(if (workout.isRunnable) "Run" else "No runnable steps") }
                    OutlinedButton(onClick = { pendingDelete = true }) { Text("Delete") }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (pendingDelete && workout != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("\u201c${workout.displayName}\u201d will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (pendingCancelImport && workout != null) {
        AlertDialog(
            onDismissRequest = { pendingCancelImport = false },
            title = { Text("Discard import?") },
            text = { Text("This workout won't be added to your library.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCancelImport = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelImport = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun SummaryRows(workout: Workout) {
    LabeledRow("Duration", Format.formatDuration(workout.plannedDurationSec))
    LabeledRow("Distance", "%.2f km".format(workout.plannedDistanceM / 1000.0))
    LabeledRow("Imported", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(workout.importedAtEpochMs)))
    LabeledRow("TSS", workout.tss?.let { String.format("%.0f", it) } ?: "\u2014")
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            color = Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StepList(workout: Workout, displayUnit: com.example.runtraining.settings.DisplayUnit) {
    // Render steps in order. Insert a "Repeat ×N" header above the first step
    // of each repeat group, and accent steps that belong to a repeat group with
    // a colored left stripe so the grouping is visible at a glance.
    var previousRepeatGroupId: Long? = null
    workout.steps.forEach { step ->
        val rp = step.inRepeat
        val currentGroupId = rp?.repeatGroupId
        if (currentGroupId != null && currentGroupId != previousRepeatGroupId) {
            val group = workout.repeatGroups.firstOrNull { it.id == currentGroupId }
            if (group != null) RepeatHeader(iterationCount = group.iterationCount)
        }
        previousRepeatGroupId = currentGroupId
        StepRow(step = step, displayUnit = displayUnit, inRepeat = currentGroupId != null)
        HorizontalDivider()
    }
}

@Composable
private fun RepeatHeader(iterationCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            text = "Repeat ×$iterationCount",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepRow(
    step: Step,
    displayUnit: com.example.runtraining.settings.DisplayUnit,
    inRepeat: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent stripe for steps inside a repeat group.
        if (inRepeat) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp),
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "${step.stepIndex}.", modifier = Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.name ?: "\u2014", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                IntensityBadge(step.intensity)
            }
            Spacer(modifier = Modifier.height(2.dp))
            val durationText = when (val sd = step.sourceDuration) {
                is SourceDuration.Time -> Format.formatDuration(sd.seconds)
                is SourceDuration.Distance -> "%.2f km".format(sd.meters / 1000.0)
                SourceDuration.Open -> "open"
            }
            Row {
                Text(text = durationText)
                Spacer(modifier = Modifier.width(12.dp))
                TargetDisplay(target = step.target, unit = displayUnit)
            }
        }
    }
}
