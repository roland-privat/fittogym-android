package com.example.runtraining.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Detail view for a single completed workout result.
 * Shows all metrics, allows repeat, and allows delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    resultId: Long,
    repository: WorkoutResultRepository,
    onBack: () -> Unit,
    onRepeatWorkout: (workoutId: Long) -> Unit,
) {
    var result by remember { mutableStateOf<WorkoutResultEntity?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resultId) {
        scope.launch {
            result = repository.getResult(resultId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val r = result!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Workout name and stopped indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            r.workoutDisplayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (r.wasStoppedEarly) {
                            Text(
                                "⚠ Stopped Early",
                                fontSize = 14.sp,
                                color = Color.Red,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                "✓ Completed",
                                fontSize = 14.sp,
                                color = Color.Green,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Metrics cards
                MetricCard("Planned Duration", Format.formatDuration(r.plannedDurationSec))
                MetricCard("Actual Duration", Format.formatDuration(r.actualDurationSec))

                if (r.averageHrBpm != null) {
                    MetricCard("Average Heart Rate", "${r.averageHrBpm} BPM")
                }

                if (r.tss != null) {
                    MetricCard("Training Stress Score (TSS)", String.format("%.1f", r.tss))
                }

                // Date completed
                MetricCard(
                    "Completed",
                    SimpleDateFormat("EEEE, MMM d, yyyy h:mm a", Locale.getDefault())
                        .format(Date(r.completedAtEpochMs)),
                )

                // Spacer to push buttons down
                Box(modifier = Modifier.weight(1f))

                // Action buttons
                Button(
                    onClick = { onRepeatWorkout(r.workoutId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Repeat This Workout")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.delete(resultId)
                            Log.d("HistoryDetailScreen: deleted result $resultId")
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Delete Result", color = Color.Red)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
            )
            .padding(12.dp),
    ) {
        Column {
            Text(
                label,
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
