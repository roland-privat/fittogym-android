package com.example.runtraining.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.runtraining.workout.model.Intensity
import com.example.runtraining.workout.model.Workout

/**
 * Horizontal timeline chart per the spec's reference image: one rectangle per
 * *expanded* step (repeats unrolled), width ∝ effective duration, **height ∝
 * intensity**, a thin slightly-darker accent stripe along the top. A 3 dp
 * vertical playhead marks the current session position (Spec FR-014–FR-016).
 *
 * Single brand color across all steps — intensity is communicated by height
 * (per the user's UI direction).
 */
@Composable
fun TimelineChart(
    workout: Workout,
    playheadFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
    barColor: Color = Color(0xFF1565C0),
    showPlayhead: Boolean = true,
) {
    val expanded = remember(workout.id) { expandSteps(workout) }
    val totalDurationSec = expanded.sumOf { it.effectiveDurationSec.toLong() }.coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        val gapPx = 1.dp.toPx()
        val accentH = h * 0.10f

        var xCursor = 0f
        expanded.forEach { es ->
            val rectWidth = (es.effectiveDurationSec.toFloat() / totalDurationSec.toFloat()) * w
            val barHeight = h * es.heightFraction
            val topY = h - barHeight
            val drawnWidth = (rectWidth - gapPx).coerceAtLeast(1f)
            drawRect(
                color = barColor,
                topLeft = Offset(xCursor, topY),
                size = Size(width = drawnWidth, height = barHeight),
            )
            drawRect(
                color = accentColor(barColor),
                topLeft = Offset(xCursor, topY),
                size = Size(width = drawnWidth, height = accentH.coerceAtMost(barHeight)),
            )
            xCursor += rectWidth
        }

        val playX = playheadFraction.coerceIn(0f, 1f) * w
        val playWidth = 3.dp.toPx()
        if (showPlayhead) {
            drawRoundRect(
                color = playheadColor,
                topLeft = Offset(playX - playWidth / 2f, 0f),
                size = Size(width = playWidth, height = h),
                cornerRadius = CornerRadius(playWidth / 2f, playWidth / 2f),
            )
        }
    }
}

private val playheadColor = Color(0xFF0D1B2A)

private fun accentColor(base: Color): Color = Color(
    red = (base.red * 0.55f).coerceIn(0f, 1f),
    green = (base.green * 0.55f).coerceIn(0f, 1f),
    blue = (base.blue * 0.55f).coerceIn(0f, 1f),
    alpha = 1f,
)

private data class ExpandedStep(val effectiveDurationSec: Int, val heightFraction: Float)

/** Height fraction per intensity. rest < warmup ≈ cooldown < active. */
private fun heightFor(intensity: Intensity): Float = when (intensity) {
    Intensity.WARMUP -> 0.40f
    Intensity.COOLDOWN -> 0.35f
    Intensity.REST -> 0.55f
    Intensity.ACTIVE -> 0.95f
    Intensity.OTHER -> 0.60f
}

private fun expandSteps(workout: Workout): List<ExpandedStep> {
    val out = mutableListOf<ExpandedStep>()
    val expandedGroups = mutableSetOf<Long>()
    workout.steps.forEach { step ->
        val effSec = step.effectiveDurationSec ?: return@forEach
        val rp = step.inRepeat
        if (rp == null) {
            out += ExpandedStep(effSec, heightFor(step.intensity))
            return@forEach
        }
        if (expandedGroups.contains(rp.repeatGroupId)) return@forEach
        val group = workout.repeatGroups.firstOrNull { it.id == rp.repeatGroupId } ?: return@forEach
        val groupSteps = workout.steps.filter { s -> s.inRepeat?.repeatGroupId == rp.repeatGroupId }
            .sortedBy { it.inRepeat?.positionInRepeat ?: 0 }
        repeat(group.iterationCount) {
            groupSteps.forEach { gs ->
                gs.effectiveDurationSec?.let { d ->
                    out += ExpandedStep(d, heightFor(gs.intensity))
                }
            }
        }
        expandedGroups += rp.repeatGroupId
    }
    return out
}

@Composable
private fun <T> remember(key1: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key1) { calculation() }
