package com.example.runtraining.audio

/**
 * Schedules step-end countdown beeps. Decoupled from the audio output (TonePlayer)
 * so the engine can be unit-tested with a fake scheduler.
 *
 * Contract: see specs/001-core-workout-flow/contracts/run-session-state-machine.md
 * "Beep scheduler contract" + Spec FR-023a.
 */
interface BeepScheduler {

    /**
     * Schedule countdown beeps for a single step. Always cancels any previously
     * scheduled beeps belonging to a prior step before scheduling new ones.
     *
     * Beeps fire at monotonic times:
     *   stepStartMonotonicMs + (effectiveDurationSec - k) * 1000   for k = 5,4,3,2,1
     *
     * The last beep coincides (within ±100 ms per FR-023a) with the transition
     * to the next step. If `effectiveDurationSec <= 5`, only the beeps that
     * still fit are scheduled. If `effectiveDurationSec` is null or <= 0
     * (open step), nothing is scheduled.
     */
    fun scheduleStepEndBeeps(
        stepStartMonotonicMs: Long,
        effectiveDurationSec: Int?,
        onBeep: () -> Unit,
    )

    /** Cancel all pending beep callbacks. Safe to call any time. */
    fun cancelAll()
}
