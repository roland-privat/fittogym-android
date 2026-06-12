package com.example.runtraining.ui.complete

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtraining.util.Format

/** Workout-completion summary card (Spec FR-022a). Shown on natural finish AND on Stop. */
@Composable
fun CompleteScreen(
    onDone: () -> Unit,
    viewModel: CompleteViewModel = viewModel(factory = CompleteViewModel.Factory),
) {
    val summary by viewModel.summary.collectAsState()

    val dismiss = {
        viewModel.dismiss()
        onDone()
    }

    BackHandler { dismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = dismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (summary == null) {
            Text("Loading\u2026")
        } else {
            val s = summary!!
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (s.wasStoppedEarly) "Workout ended" else "\uD83C\uDF89 Congratulations!",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (s.wasStoppedEarly) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = s.workoutDisplayName,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (s.wasStoppedEarly) {
                        Text(
                            text = "Stopped before the finish.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "You finished the workout. Nice work.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SummaryRow("Planned", Format.formatDuration(s.plannedDurationSec))
                    SummaryRow("Actual", Format.formatDuration(s.actualElapsedSec))
                    if (s.averageHrBpm != null) {
                        SummaryRow("Avg HR", "${s.averageHrBpm} bpm")
                    }
                    SummaryRow("TSS", s.plannedTss?.let { String.format("%.0f", it) } ?: "\u2014")

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = dismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleLarge)
    }
}
