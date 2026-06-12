package com.example.runtraining.workout.model

/**
 * Domain model for an imported, planned running workout.
 *
 * Mirrors the Room schema (see `persistence/db/entities`) with non-nullable
 * invariants enforced at the boundary mapper. The domain layer never touches
 * Room entities directly.
 *
 * See specs/001-core-workout-flow/data-model.md §3.
 */
data class Workout(
    val id: Long,
    val contentHash: String,
    val originalFilename: String,
    val displayName: String,
    val importedAtEpochMs: Long,
    val plannedDurationSec: Int,
    val plannedDistanceM: Int,
    /** Null → unknown (render "—"). */
    val tss: Double?,
    /** Null → never completed. Set when a Run session reaches state COMPLETE naturally. */
    val lastCompletedAtEpochMs: Long?,
    val steps: List<Step>,
    val repeatGroups: List<RepeatGroup>,
) {
    /** N in "step n of N" — counts authored steps once, NOT expanding repeats. (FR-017a) */
    val totalAuthoredSteps: Int get() = steps.size

    /** True if at least one step has a usable effective duration. */
    val isRunnable: Boolean get() = steps.any { it.effectiveDurationSec != null }
}

/** One segment of a workout (a row in workout_step). */
data class Step(
    val id: Long,
    /** 1-based ordinal in the authored step list (FR-017a). */
    val stepIndex: Int,
    /** Null → render "—" (FR-017a). */
    val name: String?,
    val intensity: Intensity,
    val sourceDuration: SourceDuration,
    /** Null → step is effectively "open"; not runnable on its own. */
    val effectiveDurationSec: Int?,
    val target: Target,
    val zoneLabel: String?,
    /** Null → step is not inside a repeat block. */
    val inRepeat: RepeatPosition?,
)

enum class Intensity { WARMUP, ACTIVE, REST, COOLDOWN, OTHER }

sealed interface SourceDuration {
    data class Time(val seconds: Int) : SourceDuration
    data class Distance(val meters: Int) : SourceDuration
    data object Open : SourceDuration
}

sealed interface Target {
    data class Pace(
        /** Faster bound (smaller sec/km). */
        val lowerSecPerKm: Int,
        /** Slower bound (larger sec/km). */
        val upperSecPerKm: Int,
    ) : Target {
        /** Midpoint pace in sec/km, used for TSS and distance derivation. */
        val midpointSecPerKm: Int get() = (lowerSecPerKm + upperSecPerKm) / 2
    }
    data object Open : Target
}

data class RepeatPosition(
    val repeatGroupId: Long,
    /** 0-based position of this step within its repeat group. */
    val positionInRepeat: Int,
)

/** A bracket around two or more contiguous steps with an iteration count. */
data class RepeatGroup(
    val id: Long,
    val orderInWorkout: Int,
    /** N in the repeat-lap counter "n of N" (FR-018). */
    val iterationCount: Int,
)
