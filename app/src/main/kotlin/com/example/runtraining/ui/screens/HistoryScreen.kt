package com.example.runtraining.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtraining.persistence.WorkoutResultRepository
import com.example.runtraining.persistence.db.entities.WorkoutResultEntity
import com.example.runtraining.util.Format
import com.example.runtraining.util.Log
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen showing all completed/stopped workouts.
 * Allows viewing details, repeating, and deleting results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: WorkoutResultRepository,
    onBack: () -> Unit,
    onShowDetail: (resultId: Long) -> Unit,
) {
    val allResults by repository.getAllResults().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var sortByDate by remember { mutableStateOf(true) } // true = newest first, false = oldest first

    val filteredAndSorted = remember(allResults, sortByDate) {
        if (sortByDate) allResults else allResults.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Sort control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { sortByDate = !sortByDate }) {
                    Text(if (sortByDate) "Newest" else "Oldest", fontSize = 12.sp)
                }
            }

            // Batch delete controls
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { selectedIds = filteredAndSorted.map { it.id }.toSet() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Select All", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { selectedIds = emptySet() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }

            // Results list
            if (filteredAndSorted.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No history yet", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn {
                    items(filteredAndSorted) { result ->
                        HistoryResultItem(
                            result = result,
                            isSelected = result.id in selectedIds,
                            onToggleSelect = { selected ->
                                selectedIds = if (selected) {
                                    selectedIds + result.id
                                } else {
                                    selectedIds - result.id
                                }
                            },
                            onTap = { onShowDetail(result.id) },
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            // Batch delete button
            if (selectedIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            Log.d("HistoryScreen: deleting ${selectedIds.size} results")
                            repository.deleteMultiple(selectedIds.toList())
                            selectedIds = emptySet()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Delete ${selectedIds.size} result${if (selectedIds.size != 1) "s" else ""}")
                }
            }
        }
    }
}

@Composable
private fun HistoryResultItem(
    result: WorkoutResultEntity,
    isSelected: Boolean,
    onToggleSelect: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggleSelect,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onTap() },
        ) {
            // Workout name + stopped indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    result.workoutDisplayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (result.wasStoppedEarly) {
                    Text(
                        "STOPPED",
                        fontSize = 10.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Planned vs actual time
            Text(
                "Planned: ${Format.formatDuration(result.plannedDurationSec)} • " +
                        "Actual: ${Format.formatDuration(result.actualDurationSec)}",
                fontSize = 12.sp,
                color = Color.Gray,
            )

            // Average HR (if available)
            if (result.averageHrBpm != null) {
                Text(
                    "Avg HR: ${result.averageHrBpm} BPM",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }

            // Date
            Text(
                SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(
                    Date(result.completedAtEpochMs)
                ),
                fontSize = 11.sp,
                color = Color.LightGray,
            )
        }

        // Tap to open icon
        androidx.compose.material3.Icon(
            Icons.Default.Edit,
            contentDescription = "View details",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
