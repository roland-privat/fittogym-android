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

    /**
     * Actual (performed) rTSS for a session that ran for [elapsedSec] seconds,
     * integrating the planned per-step intensity over the runtime timeline in
     * execution order (repeat blocks unrolled) and prorating the step that was
     * in progress when the session ended.
     *
     * Because this app has no pace/GPS sensor, "actual" means the planned TSS of
     * the portion of the plan the user actually completed — so stopping early
     * yields a smaller value than the full planned TSS, and a natural finish
     * reproduces the full planned TSS.
     *
     * `threshold == null` (unset in Options) → returns null (UI renders "—").
     */
    fun computeElapsed(workout: Workout, thresholdPaceSecPerKm: Int?, elapsedSec: Int): Double? {
        if (thresholdPaceSecPerKm == null || thresholdPaceSecPerKm <= 0) return null
        if (elapsedSec <= 0) return 0.0

        var remaining = elapsedSec
        var total = 0.0
        for (seg in runtimeTimeline(workout)) {
            if (remaining <= 0) break
            val dur = seg.effectiveDurationSec ?: 0
            if (dur <= 0) continue
            val used = minOf(dur, remaining)
            val pace = (seg.target as? Target.Pace)?.midpointSecPerKm
            if (pace != null && pace > 0) {
                val intensityFactor = thresholdPaceSecPerKm.toDouble() / pace.toDouble()
                total += (used * intensityFactor * intensityFactor) / 36.0
            }
            remaining -= used
        }
        return Math.round(total * 10.0) / 10.0
    }

    /**
     * Flattens a workout into the ordered list of steps as they are actually
     * executed: repeat-group members are contiguous and the whole block is
     * emitted `iterationCount` times, matching the engine's traversal.
     */
    private fun runtimeTimeline(workout: Workout): List<com.example.runtraining.workout.model.Step> {
        val steps = workout.steps
        val result = ArrayList<com.example.runtraining.workout.model.Step>(steps.size)
        var i = 0
        while (i < steps.size) {
            val groupId = steps[i].inRepeat?.repeatGroupId
            if (groupId != null) {
                var j = i
                val members = ArrayList<com.example.runtraining.workout.model.Step>()
                while (j < steps.size && steps[j].inRepeat?.repeatGroupId == groupId) {
                    members.add(steps[j]); j++
                }
                val iterations = workout.repeatGroups.firstOrNull { it.id == groupId }?.iterationCount ?: 1
                repeat(iterations) { result.addAll(members) }
                i = j
            } else {
                result.add(steps[i]); i++
            }
        }
        return result
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
