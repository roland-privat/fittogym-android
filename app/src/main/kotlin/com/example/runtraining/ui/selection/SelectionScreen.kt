package com.example.runtraining.ui.selection

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtraining.ui.run.TimelineChart
import com.example.runtraining.util.Format
import com.example.runtraining.workout.model.Workout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    onTapWorkout: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenOptions: () -> Unit,
    viewModel: SelectionViewModel = viewModel(factory = SelectionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    var workoutPendingDeletion by remember { mutableStateOf<Workout?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workouts") },
                actions = {
                    IconButton(onClick = onOpenOptions) {
                        Icon(Icons.Filled.Settings, contentDescription = "Options")
                    }
                },
            )
        },
    ) { padding ->
        if (state.workouts.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                SortChipRow(
                    selected = state.sortOrder,
                    onSelect = viewModel::setSortOrder,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.workouts, key = { it.id }) { w ->
                        WorkoutRow(
                            workout = w,
                            onTap = {
                                if (w.isRunnable) {
                                    onTapWorkout(w.id)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "This workout has no runnable steps.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onOpenDetails(w.id)
                                }
                            },
                            onOpenDetails = { onOpenDetails(w.id) },
                            onDelete = { workoutPendingDeletion = w },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val pending = workoutPendingDeletion
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { workoutPendingDeletion = null },
            title = { Text("Delete workout?") },
            text = { Text("\u201c${pending.displayName}\u201d will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pending.id)
                    workoutPendingDeletion = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { workoutPendingDeletion = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutRow(
    workout: Workout,
    onTap: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuOpen = true },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workout.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Mini timeline — varying heights, no playhead, matches the Run page chart.
            if (workout.isRunnable) {
                TimelineChart(
                    workout = workout,
                    playheadFraction = 0f,
                    showPlayhead = false,
                    height = 28.dp,
                    barColor = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = "${Format.formatDuration(workout.plannedDurationSec)}  \u00b7  " +
                    "TSS ${workout.tss?.let { String.format("%.0f", it) } ?: "\u2014"}" +
                    if (!workout.isRunnable) "  \u00b7  (no runnable steps)" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (workout.lastCompletedAtEpochMs != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last completed " + Format.formatRelativeAgo(workout.lastCompletedAtEpochMs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = { menuOpen = false; onOpenDetails() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No workouts yet",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Share a .fit file from another app, or open one from a file manager.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SortChipRow(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SortOrder.entries) { order ->
            FilterChip(
                selected = order == selected,
                onClick = { onSelect(order) },
                label = { Text(order.label) },
            )
        }
    }
}
