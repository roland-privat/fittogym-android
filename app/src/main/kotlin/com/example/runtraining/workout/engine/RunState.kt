package com.example.runtraining.workout.engine

import com.example.runtraining.workout.model.Intensity

/** High-level run-session FSM state. */
enum class RunState { IDLE, RUNNING, PAUSED, COMPLETE }

/**
 * Captured at the moment Step Forward is pressed, so Step Backward can restore
 * exactly that state. Cleared on any natural step transition or Stop per
 * Spec FR-019b.
 */
data class SkipCheckpoint(
    val skippedStepIndex: Int,
    val skippedIteration: Int,
    val elapsedMsAtSkip: Long,
    val accumulatedHrConnectedMsAtSkip: Long,
)

/** Enabled-flags for the five Run controls (Spec FR-019..FR-019b). */
data class RunControlsState(
    val startEnabled: Boolean,
    val pauseEnabled: Boolean,
    val stopEnabled: Boolean,
    val stepForwardEnabled: Boolean,
    val stepBackwardEnabled: Boolean,
)

/**
 * UI-side projection of `RunSession`. The Workout Run page binds to a
 * StateFlow<RunUiState> exposed by the service. Per the Observability
 * surface in contracts/run-session-state-machine.md, the UI never reads
 * RunSession directly.
 */
data class RunUiState(
    val workoutDisplayName: String,
    val totalAuthoredSteps: Int,          // N in "step n of N" (FR-017a)
    val currentStepIndex: Int,            // 1-based
    val currentStepName: String?,         // null → render "—"
    val currentIntensity: Intensity,
    /** Pre-formatted via the user's DisplayUnit (FR-013a). */
    val currentTargetDisplay: String,
    val nextTargetDisplay: String?,       // null → final step → render "—"
    /** "3 of 6" while inside a repeat, else null (FR-018). */
    val currentRepeatLabel: String?,
    val stepElapsedSec: Int,
    val stepRemainingSec: Int,
    val sessionElapsedSec: Int,
    val sessionRemainingSec: Int,
    val hrBpm: Int?,                      // null → "—"
    val hrSignalLost: Boolean,            // shown when HRM was previously emitting
    val state: RunState,
    val controls: RunControlsState,
    /** 0..1 across the EXPANDED timeline (repeats unrolled). */
    val playheadFraction: Float,
    /** True when the COMPLETE state was reached via Stop, not natural finish. */
    val wasStoppedEarly: Boolean = false,
)
