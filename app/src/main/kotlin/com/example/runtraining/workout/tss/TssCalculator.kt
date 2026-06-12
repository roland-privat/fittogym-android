package com.example.runtraining.workout.tss

import com.example.runtraining.workout.model.SourceDuration
import com.example.runtraining.workout.model.Target
import com.example.runtraining.workout.model.Workout

/**
 * Running Training Stress Score (rTSS) computation against a user-set
 * threshold pace. Expands repeat blocks: each authored step contributes
 * iterationCount times.
 *
 * Formula per specs/001-core-workout-flow/research.md §8:
 *   IF_i   = threshold_pace_sec_per_km / step_pace_midpoint_sec_per_km
 *   tss_i  = (seconds_i × IF_i²) / 36     // i.e. (sec * IF² / 3600) * 100
 *
 * Open steps and steps without a usable pace target contribute 0.
 * `threshold == null` (unset in Options) → returns null (UI renders "—").
 */
object TssCalculator {

    fun compute(workout: Workout, thresholdPaceSecPerKm: Int?): Double? {
        if (thresholdPaceSecPerKm == null || thresholdPaceSecPerKm <= 0) return null

        var total = 0.0
        workout.steps.forEach { step ->
            val effSec = step.effectiveDurationSec ?: return@forEach
            val pace = (step.target as? Target.Pace)?.midpointSecPerKm ?: return@forEach
            if (pace <= 0) return@forEach

            // Multiplier from repeat-group membership (iteration count).
            val multiplier = step.inRepeat?.let { rp ->
                workout.repeatGroups.firstOrNull { it.id == rp.repeatGroupId }?.iterationCount
            } ?: 1

            val intensityFactor = thresholdPaceSecPerKm.toDouble() / pace.toDouble()
            total += (effSec * intensityFactor * intensityFactor) / 36.0 * multiplier
        }

        // Round to nearest 0.1 to avoid noise from floating-point arithmetic.
        return Math.round(total * 10.0) / 10.0
    }

    // Provided for legacy/no-domain call sites (not used in v1).
    @Suppress("unused")
    fun computeBySourceDuration(
        source: SourceDuration,
        target: Target,
        thresholdPaceSecPerKm: Int?,
    ): Double {
        // Pure-step helper for unit tests; computes the un-multiplied tss_i.
        if (thresholdPaceSecPerKm == null) return 0.0
        val pace = (target as? Target.Pace)?.midpointSecPerKm ?: return 0.0
        val effSec = when (source) {
            is SourceDuration.Time -> source.seconds
            is SourceDuration.Distance ->
                if (pace > 0 && source.meters > 0) ((source.meters.toDouble() * pace) / 1000.0).toInt()
                else 0
            SourceDuration.Open -> 0
        }
        if (effSec <= 0 || pace <= 0) return 0.0
        val intensity = thresholdPaceSecPerKm.toDouble() / pace.toDouble()
        return (effSec * intensity * intensity) / 36.0
    }
}
