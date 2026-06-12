package com.example.runtraining.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.runtraining.workout.model.Intensity

// Brand palette — blues that match the timeline reference image in the spec.
val BlueDeep = Color(0xFF1565C0)        // ACTIVE
val BlueBright = Color(0xFF1E88E5)      // WARMUP
val BlueLight = Color(0xFF64B5F6)       // REST
val BlueMuted = Color(0xFF90CAF9)       // COOLDOWN
val Gray500 = Color(0xFF9E9E9E)         // OTHER

/** Map an intensity class to the rectangle fill on the timeline + badge color. */
fun colorForIntensity(intensity: Intensity): Color = when (intensity) {
    Intensity.WARMUP -> BlueBright
    Intensity.ACTIVE -> BlueDeep
    Intensity.REST -> BlueLight
    Intensity.COOLDOWN -> BlueMuted
    Intensity.OTHER -> Gray500
}
