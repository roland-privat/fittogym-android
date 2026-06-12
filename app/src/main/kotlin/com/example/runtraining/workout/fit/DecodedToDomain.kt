package com.example.runtraining.workout.fit

import com.example.runtraining.workout.model.Intensity
import com.example.runtraining.workout.model.RepeatGroup
import com.example.runtraining.workout.model.RepeatPosition
import com.example.runtraining.workout.model.SourceDuration
import com.example.runtraining.workout.model.Step
import com.example.runtraining.workout.model.Target
import com.example.runtraining.workout.model.Workout

/**
 * Maps a Garmin-SDK-free `DecodedWorkout` into the domain `Workout`. Owns:
 *  - 1-based stepIndex (re-numbered after repeat markers are dropped).
 *  - RepeatGroup synthesis (with negative surrogate ids that the repository
 *    re-maps to real auto-generated DB ids on insert).
 *  - Effective-duration derivation per Spec FR-004a.
 *  - Overall planned duration / distance summation (expanding repeats N×).
 */
object DecodedToDomain {

    fun DecodedWorkout.toDomain(
        originalFilename: String,
        contentHash: String,
        importedAtEpochMs: Long,
    ): Workout {
        // 1) Build RepeatGroups (one per DecodedRepeat) with surrogate negative
        //    ids; the repository re-maps these to real DB ids on insert.
        val repeatGroups: List<RepeatGroup> = repeats.mapIndexed { idx, r ->
            RepeatGroup(
                id = -(idx + 1L),                 // negative surrogate
                orderInWorkout = idx,
                iterationCount = r.iterationCount,
            )
        }

        // 2) For each emitted DecodedStep, figure out which repeat group (if
        //    any) it belongs to, and its 0-based position inside the group.
        fun repeatPosFor(originalIdx: Int): RepeatPosition? {
            repeats.forEachIndexed { gi, r ->
                if (originalIdx in r.fromOriginalIndex..r.toOriginalIndex) {
                    val pos = originalIdx - r.fromOriginalIndex
                    return RepeatPosition(
                        repeatGroupId = repeatGroups[gi].id,
                        positionInRepeat = pos,
                    )
                }
            }
            return null
        }

        // 3) Build the canonical, 1-based authored step list.
        val authoredSteps: List<Step> = steps.mapIndexed { i, s ->
            val target: Target = if (s.targetKind == "pace" &&
                s.targetPaceLowerSecPerKm != null && s.targetPaceUpperSecPerKm != null
            ) {
                Target.Pace(s.targetPaceLowerSecPerKm, s.targetPaceUpperSecPerKm)
            } else {
                Target.Open
            }
            val sourceDuration: SourceDuration = when (s.durationKind) {
                "time" -> SourceDuration.Time(s.durationSec ?: 0)
                "distance" -> SourceDuration.Distance(s.distanceM ?: 0)
                else -> SourceDuration.Open
            }
            val effectiveDurationSec: Int? = computeEffectiveDuration(sourceDuration, target)
            Step(
                id = 0L,                                    // assigned at insert
                stepIndex = i + 1,                          // 1-based
                name = s.name,
                intensity = parseIntensity(s.intensity),
                sourceDuration = sourceDuration,
                effectiveDurationSec = effectiveDurationSec,
                target = target,
                zoneLabel = s.zoneLabel,
                inRepeat = repeatPosFor(s.originalIndex),
            )
        }

        // 4) Compute overall planned duration + distance, expanding repeats.
        //    For each authored step, multiplier = iterationCount if inside a
        //    repeat group, else 1.
        var totalDurationSec = 0
        var totalDistanceM = 0
        authoredSteps.forEach { step ->
            val multiplier = step.inRepeat?.let { rp ->
                repeatGroups.first { it.id == rp.repeatGroupId }.iterationCount
            } ?: 1

            val effSec = step.effectiveDurationSec ?: 0
            totalDurationSec += effSec * multiplier

            val stepDistanceM = when (val sd = step.sourceDuration) {
                is SourceDuration.Distance -> sd.meters
                is SourceDuration.Time -> {
                    val pace = (step.target as? Target.Pace)?.midpointSecPerKm
                    if (pace != null && pace > 0) ((sd.seconds.toDouble() * 1000.0) / pace).toInt()
                    else 0
                }
                SourceDuration.Open -> 0
            }
            totalDistanceM += stepDistanceM * multiplier
        }

        // 5) Derive a default display name from the filename (extension stripped).
        val defaultName = originalFilename.substringBeforeLast('.', missingDelimiterValue = originalFilename)
            .takeIf { it.isNotBlank() } ?: "Unnamed workout"
        val displayName = (name?.takeIf { it.isNotBlank() } ?: defaultName)

        return Workout(
            id = 0L,
            contentHash = contentHash,
            originalFilename = originalFilename,
            displayName = displayName,
            importedAtEpochMs = importedAtEpochMs,
            plannedDurationSec = totalDurationSec,
            plannedDistanceM = totalDistanceM,
            tss = null,                                 // TssCalculator fills this
            lastCompletedAtEpochMs = null,              // freshly imported → never completed
            steps = authoredSteps,
            repeatGroups = repeatGroups,
        )
    }

    /**
     * FR-004a: distance-based step → effective_duration = distance / pace_midpoint.
     * Time-based → its own seconds. Open or distance-without-pace → null.
     */
    internal fun computeEffectiveDuration(source: SourceDuration, target: Target): Int? = when (source) {
        is SourceDuration.Time -> if (source.seconds > 0) source.seconds else null
        is SourceDuration.Distance -> {
            val pace = (target as? Target.Pace)?.midpointSecPerKm
            if (source.meters > 0 && pace != null && pace > 0) {
                ((source.meters.toDouble() * pace) / 1000.0).toInt()
            } else null
        }
        SourceDuration.Open -> null
    }

    private fun parseIntensity(s: String): Intensity = when (s.lowercase()) {
        "warmup" -> Intensity.WARMUP
        "active" -> Intensity.ACTIVE
        "rest", "recovery" -> Intensity.REST
        "cooldown" -> Intensity.COOLDOWN
        else -> Intensity.OTHER
    }
}
