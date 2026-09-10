package com.example.runtraining.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * The FitToGym brand mark: four rounded "timeline" bars over a baseline
 * (geometry from ic_launcher_foreground, 108-unit viewport). Draws in [color].
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val scale = size.minDimension / 108f
        val bars = listOf(
            listOf(28f, 46f, 12f, 22f),
            listOf(44f, 36f, 12f, 32f),
            listOf(60f, 28f, 12f, 40f),
            listOf(76f, 42f, 12f, 26f),
        )
        for (bar in bars) {
            val (x, y, w, h) = bar
            drawRoundRect(
                color = color,
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(3f * scale, 3f * scale),
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(22f * scale, 72f * scale),
            size = Size(70f * scale, 4f * scale),
            cornerRadius = CornerRadius(1.5f * scale, 1.5f * scale),
        )
    }
}
