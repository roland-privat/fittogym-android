package com.example.runtraining.workout.engine

import android.os.SystemClock
import com.example.runtraining.audio.BeepScheduler
import com.example.runtraining.ble.HrSample
import com.example.runtraining.settings.DisplayUnit
import com.example.runtraining.util.Format
import com.example.runtraining.workout.model.Intensity
import com.example.runtraining.workout.model.Step
import com.example.runtraining.workout.model.Target
import com.example.runtraining.workout.model.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Run-session state machine. Owns the monotonic clocks for one workout
 * execution; emits `RunUiState` at ~4 Hz while running so the UI playhead
 * stays smooth. Pure-Android (no Compose) so it lives in the service.
 *
 * See specs/001-core-workout-flow/contracts/run-session-state-machine.md
 * for the full state table.
 *
 * `clockProvider` lets tests inject a fake clock; default is
 * SystemClock.elapsedRealtime() which is immune to wall-clock corrections.
 */
class RunSessionEngine(
    private val beepScheduler: BeepScheduler,
    private val onPlayBeep: () -> Unit,
    private val clockProvider: () -> Long = { SystemClock.elapsedRealtime() },
    /** UI emission cadence. 250 ms gives smooth playhead movement; tests can crank lower. */
    private val emissionPeriodMs: Long = 250L,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _ui = MutableStateFlow(emptyUiState())
    val uiState: StateFlow<RunUiState> = _ui.asStateFlow()

    private var workout: Workout? = null
    private var displayUnit: DisplayUnit = DisplayUnit.PACE

    // FSM bookkeeping
    private var state: RunState = RunState.IDLE
    private var currentStepIndex: Int = 1                      // 1-based
    /** 1-based iteration within the current step's repeat group (1 when step is not in a repeat). */
    private var currentRepeatIteration: Int = 1
    private var stepStartMonotonicMs: Long = 0L
    private var pausedAccumulatedMsForCurrentStep: Long = 0L
    private var pauseStartMonotonicMs: Long = 0L

    private var sessionStartMonotonicMs: Long = 0L
    private var sessionPausedAccumulatedMs: Long = 0L
    private var elapsedTimeFromCompletedStepsMs: Long = 0L     // sum of effective durations of finished steps

    private var isStepBackUndoable: Boolean = false
    private var lastSkippedStepCheckpoint: SkipCheckpoint? = null

    private var hrSample: HrSample? = null
    private var hrSignalLost: Boolean = false
    private var hrConnectedDurationMs: Long = 0L
    // Running mean: accumulate every accepted BPM sample (~1 Hz from a BLE HRM)
    // while the engine is in RUNNING state. Used by buildCompletionSummary().
    private var hrBpmSum: Long = 0L
    private var hrSampleCount: Int = 0

    /** True when the COMPLETE state was reached via Stop, not natural end. */
    private var wasStoppedEarly: Boolean = false

    // ----------------- Public API ------------------------------------------

    fun setDisplayUnit(unit: DisplayUnit) {
        displayUnit = unit
        emit()
    }

    fun load(workout: Workout) {
        this.workout = workout
        resetForNewWorkout()
        emit()
    }

    fun start() {
        val w = workout ?: return
        val now = clockProvider()
        when (state) {
            RunState.IDLE -> {
                state = RunState.RUNNING
                sessionStartMonotonicMs = now
                sessionPausedAccumulatedMs = 0L
                stepStartMonotonicMs = now
                pausedAccumulatedMsForCurrentStep = 0L
                elapsedTimeFromCompletedStepsMs = 0L
                scheduleBeepsForCurrentStep()
                startTicker()
            }
            RunState.PAUSED -> {
                state = RunState.RUNNING
                // Account for the time we were paused so monotonic math stays correct.
                val pausedMs = now - pauseStartMonotonicMs
                stepStartMonotonicMs += pausedMs
                sessionPausedAccumulatedMs += pausedMs
                scheduleBeepsForCurrentStep()
                startTicker()
            }
            else -> Unit
        }
        emit()
        if (!w.isRunnable) {
            // Belt and braces: a workout with all-open steps shouldn't transition through Run.
            stop()
        }
    }

    fun pause() {
        if (state != RunState.RUNNING) return
        state = RunState.PAUSED
        pauseStartMonotonicMs = clockProvider()
        beepScheduler.cancelAll()
        stopTicker()
        emit()
    }

    fun stop() {
        if (state == RunState.IDLE || state == RunState.COMPLETE) return
        // Transition to COMPLETE so the UI lands on the summary page (the
        // user explicitly wants a summary even when they end early). The
        // service distinguishes "stopped early" from "natural" via the
        // wasStoppedEarly flag below — only natural completion counts as
        // "last completed".
        wasStoppedEarly = true
        state = RunState.COMPLETE
        beepScheduler.cancelAll()
        stopTicker()
        isStepBackUndoable = false
        lastSkippedStepCheckpoint = null
        emit()
    }

    fun stepForward() {
        val w = workout ?: return
        if (state != RunState.RUNNING && state != RunState.PAUSED) return
        val next = nextPosition(currentStepIndex, currentRepeatIteration) ?: return

        val curStep = w.steps[currentStepIndex - 1]
        val nowElapsedMs = stepElapsedMs()
        // Capture undo checkpoint.
        lastSkippedStepCheckpoint = SkipCheckpoint(
            skippedStepIndex = currentStepIndex,
            skippedIteration = currentRepeatIteration,
            elapsedMsAtSkip = nowElapsedMs,
            accumulatedHrConnectedMsAtSkip = hrConnectedDurationMs,
        )
        isStepBackUndoable = true

        // Spec FR-019a: the playhead jumps to the next step's start. Credit
        // the skipped step as fully completed for timeline accounting.
        val fullSkippedDurationMs = (curStep.effectiveDurationSec ?: 0) * 1000L
        elapsedTimeFromCompletedStepsMs += fullSkippedDurationMs

        advanceTo(next.first, next.second)
    }

    fun stepBackward() {
        val w = workout ?: return
        if (!isStepBackUndoable) return
        val checkpoint = lastSkippedStepCheckpoint ?: return
        val skippedStep = w.steps.getOrNull(checkpoint.skippedStepIndex - 1) ?: return
        val skippedFullDurationMs = (skippedStep.effectiveDurationSec ?: 0) * 1000L

        // Undo the full-duration credit we added on Step Forward.
        elapsedTimeFromCompletedStepsMs -= skippedFullDurationMs
        if (elapsedTimeFromCompletedStepsMs < 0L) elapsedTimeFromCompletedStepsMs = 0L

        hrConnectedDurationMs = checkpoint.accumulatedHrConnectedMsAtSkip
        currentStepIndex = checkpoint.skippedStepIndex
        currentRepeatIteration = checkpoint.skippedIteration

        val now = clockProvider()
        stepStartMonotonicMs = now - checkpoint.elapsedMsAtSkip
        pausedAccumulatedMsForCurrentStep = 0L
        if (state == RunState.PAUSED) {
            pauseStartMonotonicMs = now
        }

        isStepBackUndoable = false
        lastSkippedStepCheckpoint = null

        if (state == RunState.RUNNING) {
            scheduleBeepsForCurrentStep()
        }
        emit()
    }

    fun onHrSample(sample: HrSample?) {
        hrSample = sample
        if (sample != null) {
            // Bookkeeping for "have we ever had a connection?" signal-lost UX.
            hrConnectedDurationMs += 1_000L
            // Average HR is the mean of all accepted samples while RUNNING.
            // Sampling cadence from a BLE HRM is ~1 Hz so the count is also a
            // reasonable proxy for "seconds of HR coverage".
            if (state == RunState.RUNNING && sample.bpm in 30..240) {
                hrBpmSum += sample.bpm
                hrSampleCount += 1
            }
        }
        hrSignalLost = sample == null && hrConnectedDurationMs > 0L
        emit()
    }

    // ----------------- Internals -------------------------------------------

    private fun resetForNewWorkout() {
        state = RunState.IDLE
        currentStepIndex = 1
        currentRepeatIteration = 1
        stepStartMonotonicMs = 0L
        pausedAccumulatedMsForCurrentStep = 0L
        pauseStartMonotonicMs = 0L
        sessionStartMonotonicMs = 0L
        sessionPausedAccumulatedMs = 0L
        elapsedTimeFromCompletedStepsMs = 0L
        isStepBackUndoable = false
        lastSkippedStepCheckpoint = null
        hrSample = null
        hrSignalLost = false
        hrConnectedDurationMs = 0L
        hrBpmSum = 0L
        hrSampleCount = 0
        wasStoppedEarly = false
        tickerJob?.cancel()
        tickerJob = null
        beepScheduler.cancelAll()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                emit()
                checkForNaturalStepEnd()
                if (state != RunState.RUNNING) break
                delay(emissionPeriodMs)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun checkForNaturalStepEnd() {
        val w = workout ?: return
        if (state != RunState.RUNNING) return
        val step = w.steps.getOrNull(currentStepIndex - 1) ?: return
        val durationMs = (step.effectiveDurationSec ?: return) * 1000L
        if (stepElapsedMs() >= durationMs) {
            // Natural step transition: any prior undo flag is cleared.
            elapsedTimeFromCompletedStepsMs += durationMs
            isStepBackUndoable = false
            lastSkippedStepCheckpoint = null
            val next = nextPosition(currentStepIndex, currentRepeatIteration)
            if (next == null) {
                // Workout complete.
                state = RunState.COMPLETE
                stopTicker()
                beepScheduler.cancelAll()
                emit()
            } else {
                advanceTo(next.first, next.second)
            }
        }
    }

    private fun advanceTo(newIndex: Int, newIteration: Int) {
        val w = workout ?: return
        if (newIndex > w.totalAuthoredSteps) {
            // Workout complete.
            state = RunState.COMPLETE
            stopTicker()
            beepScheduler.cancelAll()
            emit()
            return
        }
        currentStepIndex = newIndex
        currentRepeatIteration = newIteration
        stepStartMonotonicMs = clockProvider()
        pausedAccumulatedMsForCurrentStep = 0L
        if (state == RunState.RUNNING) {
            scheduleBeepsForCurrentStep()
        }
        emit()
    }

    private fun scheduleBeepsForCurrentStep() {
        val w = workout ?: return
        val step = w.steps.getOrNull(currentStepIndex - 1) ?: return
        beepScheduler.scheduleStepEndBeeps(
            stepStartMonotonicMs = stepStartMonotonicMs,
            effectiveDurationSec = step.effectiveDurationSec,
            onBeep = onPlayBeep,
        )
    }

    /**
     * Given (stepIndex, iteration), compute the next position in runtime
     * order (with repeat blocks unrolled). Returns null when the workout is
     * complete.
     *
     *  - In a repeat block, advancing past the last member step loops back to
     *    the first member step with iteration+1 (up to iterationCount).
     *  - When all iterations of a block are done, advance to the step right
     *    after the block.
     *  - Outside a repeat, just advance to the next authored step.
     *  - When the new current step is in a (different) repeat block, the
     *    iteration resets to 1.
     */
    private fun nextPosition(currentIdx: Int, currentIter: Int): Pair<Int, Int>? {
        val w = workout ?: return null
        val curStep = w.steps.getOrNull(currentIdx - 1) ?: return null
        val curGroupId = curStep.inRepeat?.repeatGroupId
        val nextStep = w.steps.getOrNull(currentIdx)  // currentIdx is 1-based; .getOrNull(currentIdx) returns the next

        if (curGroupId != null) {
            val curGroup = w.repeatGroups.firstOrNull { it.id == curGroupId } ?: return null
            val isLastInGroup = nextStep == null || nextStep.inRepeat?.repeatGroupId != curGroupId
            if (isLastInGroup) {
                if (currentIter < curGroup.iterationCount) {
                    // Loop back to the first step of this group.
                    val firstIdxOfGroup = w.steps.indexOfFirst {
                        it.inRepeat?.repeatGroupId == curGroupId
                    } + 1  // back to 1-based
                    return firstIdxOfGroup to (currentIter + 1)
                }
                // All iterations done; move past the block.
                return if (nextStep == null) null else (currentIdx + 1) to 1
            } else {
                // Same iteration, next step within the block.
                return (currentIdx + 1) to currentIter
            }
        } else {
            // Not in a repeat — linear advance.
            return if (nextStep == null) null else (currentIdx + 1) to 1
        }
    }

    private fun stepElapsedMs(): Long {
        if (sessionStartMonotonicMs == 0L) return 0L
        return when (state) {
            RunState.RUNNING -> clockProvider() - stepStartMonotonicMs
            RunState.PAUSED -> pauseStartMonotonicMs - stepStartMonotonicMs
            else -> 0L
        }
    }

    private fun sessionElapsedMs(): Long {
        if (state == RunState.IDLE || workout == null) return 0L
        return elapsedTimeFromCompletedStepsMs + stepElapsedMs()
    }

    private fun emit() {
        _ui.value = buildUiState()
    }

    private fun buildUiState(): RunUiState {
        val w = workout ?: return emptyUiState()
        val step = w.steps.getOrNull(currentStepIndex - 1) ?: return emptyUiState()

        val stepElapsed = stepElapsedMs()
        val stepDurMs = (step.effectiveDurationSec ?: 0) * 1000L
        val stepRemaining = (stepDurMs - stepElapsed).coerceAtLeast(0L)

        val sessionElapsed = sessionElapsedMs()
        val totalPlannedMs = w.plannedDurationSec * 1000L
        val sessionRemaining = (totalPlannedMs - sessionElapsed).coerceAtLeast(0L)

        val nextStep = w.steps.getOrNull(currentStepIndex)
        val nextTarget = nextStep?.let { formatTarget(it.target) }

        val repeatLabel = step.inRepeat?.let { rp ->
            val group = w.repeatGroups.firstOrNull { it.id == rp.repeatGroupId }
            if (group != null) "$currentRepeatIteration of ${group.iterationCount}" else null
        }

        val playheadFraction = if (totalPlannedMs > 0L) {
            (sessionElapsed.toFloat() / totalPlannedMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val controls = RunControlsState(
            startEnabled = state == RunState.IDLE || state == RunState.PAUSED,
            pauseEnabled = state == RunState.RUNNING,
            stopEnabled = state == RunState.RUNNING || state == RunState.PAUSED,
            stepForwardEnabled = (state == RunState.RUNNING || state == RunState.PAUSED) &&
                nextPosition(currentStepIndex, currentRepeatIteration) != null,
            stepBackwardEnabled = (state == RunState.RUNNING || state == RunState.PAUSED) &&
                isStepBackUndoable,
        )

        return RunUiState(
            workoutDisplayName = w.displayName,
            totalAuthoredSteps = w.totalAuthoredSteps,
            currentStepIndex = currentStepIndex,
            currentStepName = step.name,
            currentIntensity = step.intensity,
            currentTargetDisplay = formatTarget(step.target),
            nextTargetDisplay = nextTarget,
            currentRepeatLabel = repeatLabel,
            stepElapsedSec = (stepElapsed / 1000L).toInt(),
            stepRemainingSec = (stepRemaining / 1000L).toInt(),
            sessionElapsedSec = (sessionElapsed / 1000L).toInt(),
            sessionRemainingSec = (sessionRemaining / 1000L).toInt(),
            hrBpm = hrSample?.bpm,
            hrSignalLost = hrSignalLost,
            state = state,
            controls = controls,
            playheadFraction = playheadFraction,
            wasStoppedEarly = wasStoppedEarly,
        )
    }

    private fun formatTarget(target: Target): String = when (target) {
        is Target.Pace -> when (displayUnit) {
            DisplayUnit.PACE -> Format.formatPaceRange(target.lowerSecPerKm, target.upperSecPerKm)
            DisplayUnit.SPEED -> Format.formatSpeedRangeFromPace(target.lowerSecPerKm, target.upperSecPerKm)
        }
        Target.Open -> "\u2014"
    }

    private fun emptyUiState(): RunUiState = RunUiState(
        workoutDisplayName = workout?.displayName ?: "",
        totalAuthoredSteps = workout?.totalAuthoredSteps ?: 0,
        currentStepIndex = 1,
        currentStepName = null,
        currentIntensity = Intensity.OTHER,
        currentTargetDisplay = "\u2014",
        nextTargetDisplay = null,
        currentRepeatLabel = null,
        stepElapsedSec = 0,
        stepRemainingSec = workout?.steps?.firstOrNull()?.effectiveDurationSec ?: 0,
        sessionElapsedSec = 0,
        sessionRemainingSec = workout?.plannedDurationSec ?: 0,
        hrBpm = null,
        hrSignalLost = false,
        state = RunState.IDLE,
        controls = RunControlsState(
            startEnabled = workout?.isRunnable == true,
            pauseEnabled = false,
            stopEnabled = false,
            stepForwardEnabled = false,
            stepBackwardEnabled = false,
        ),
        playheadFraction = 0f,
    )

    /** Tear down the engine. Call from service.onDestroy. */
    fun shutdown() {
        stopTicker()
        beepScheduler.cancelAll()
        scope.cancel()
    }

    /** Workout-completion payload used by CompleteScreen (data is purely in-memory). */
    data class CompletionSummary(
        val workoutDisplayName: String,
        val plannedDurationSec: Int,
        val actualElapsedSec: Int,
        val averageHrBpm: Int?,         // null when not eligible (< 50% of session)
        val plannedTss: Double?,
        /** True when the user pressed Stop before the final step's timer reached zero. */
        val wasStoppedEarly: Boolean,
    )

    /** Compute the completion summary card payload. */
    fun buildCompletionSummary(): CompletionSummary? {
        val w = workout ?: return null
        if (state != RunState.COMPLETE) return null
        val actualMs = elapsedTimeFromCompletedStepsMs + stepElapsedMs()
        val actualSec = (actualMs / 1000L).toInt()
        // Eligibility: at least 30 s of HR samples, and coverage of at least
        // 50% of the actual session duration (assuming ~1 sample/s). This
        // avoids showing a misleading avg from a short or sparse signal.
        val avgHr: Int? = if (hrSampleCount >= 30 && hrSampleCount * 2 >= actualSec) {
            (hrBpmSum / hrSampleCount).toInt()
        } else null
        return CompletionSummary(
            workoutDisplayName = w.displayName,
            plannedDurationSec = w.plannedDurationSec,
            actualElapsedSec = actualSec,
            averageHrBpm = avgHr,
            plannedTss = w.tss,
            wasStoppedEarly = wasStoppedEarly,
        )
    }
}
