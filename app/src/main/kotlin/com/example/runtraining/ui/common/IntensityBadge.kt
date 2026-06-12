package com.example.runtraining.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.runtraining.ui.theme.colorForIntensity
import com.example.runtraining.workout.model.Intensity

/** Small chip showing the intensity label with the matching color. */
@Composable
fun IntensityBadge(intensity: Intensity, modifier: Modifier = Modifier) {
    val bg = colorForIntensity(intensity)
    Text(
        text = intensity.name.lowercase().replaceFirstChar { it.titlecase() },
        modifier = modifier
            .background(color = bg, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = Color.White,
    )
}
