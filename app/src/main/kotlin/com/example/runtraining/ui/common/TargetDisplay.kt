package com.example.runtraining.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.runtraining.settings.DisplayUnit
import com.example.runtraining.util.Format
import com.example.runtraining.workout.model.Target

/**
 * Renders a `Target` per the user's `DisplayUnit` preference.
 *  - PACE  → "5:40–5:47 /km"
 *  - SPEED → "10.4–10.6 km/h"
 *  - Open  → "—"
 */
@Composable
fun TargetDisplay(target: Target, unit: DisplayUnit, modifier: Modifier = Modifier) {
    val text = when (target) {
        is Target.Pace -> when (unit) {
            DisplayUnit.PACE -> Format.formatPaceRange(target.lowerSecPerKm, target.upperSecPerKm)
            DisplayUnit.SPEED -> Format.formatSpeedRangeFromPace(target.lowerSecPerKm, target.upperSecPerKm)
        }
        Target.Open -> "\u2014"
    }
    Text(text = text, modifier = modifier)
}
