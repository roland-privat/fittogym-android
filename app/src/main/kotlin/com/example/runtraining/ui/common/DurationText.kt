package com.example.runtraining.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.runtraining.util.Format

@Composable
fun DurationText(seconds: Int, modifier: Modifier = Modifier) {
    Text(text = Format.formatDuration(seconds), modifier = modifier)
}
