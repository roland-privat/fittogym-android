# Contract: Run-session state machine (workout engine)

**Date**: 2026-05-25 · **Feature**: [../spec.md](../spec.md)

This document fixes the formal state machine of `RunSession` (see
[../data-model.md](../data-model.md) §3). It is the **internal contract**
between `WorkoutForegroundService`, the workout run UI, the mini view, and
the unit tests in `app/src/test/.../RunSessionTest.kt`.

## States

| State | Meaning | Allowed user actions |
|---|---|---|
| `IDLE` | Workout loaded into the engine, nothing started yet, playhead at step 1 / t=0. | **Start** |
| `RUNNING` | Timer is advancing, beeps may fire, HR collected if HRM connected. | **Pause**, **Stop**, **Step Forward** (if not final step), **Step Backward** (if FR-019b conditions are met) |
| `PAUSED` | Counters frozen at their last value. Audio focus released. HR still received passively for display, but not counted toward `hrConnectedDurationMs`. | **Start** (resume), **Stop**, **Step Forward**, **Step Backward** (same gates) |
| `COMPLETE` | The last step's timer reached zero. Completion-summary card is visible. | **Done** (returns to selection) |

Out-of-band terminal pseudo-state:

- `EXITED` — engine has been torn down; service stops itself. The UI returns
  to the training selection page.

## Transitions

```text
                +-------- Stop --------+
                |                      |
                v                      |
   load(workout) +---- Start ---->  +--+---+
                 |                  |      |
                 |                  |  RUNNING <----+
                 |                  |      |        |
            +---+IDLE+----+         |  ^    \       |
            |             |         |  |     Pause  |
            |   Stop      |         |  |      v     |
            |   (no-op    |         |  +--- PAUSED  |
            |    in IDLE) |         |   Start (resume)
            v             v         |
         EXITED          ...        |
                                    |
                last step timer hits zero
                                    |
                                    v
                                COMPLETE -- Done --> EXITED
```

### Event table

| From | Event | Guard | To | Side effects |
|---|---|---|---|---|
| `IDLE` | `Start` | — | `RUNNING` | Set `sessionStartMonotonicMs`, set `stepStartMonotonicMs`, schedule 5-beep countdown for step 1 (if step 1 is runnable), enter immersive fullscreen, request audio focus *transiently per beep* (not held), bind `HrmClient` if a pairing exists. |
| `IDLE` | `Stop` | — | `EXITED` | No-op equivalent — just tear down. |
| `RUNNING` | `Pause` | — | `PAUSED` | Snapshot `stepElapsedMs` into `pausedAccumulatedMsForCurrentStep`; snapshot `sessionPausedAccumulatedMs`; cancel any pending beeps for the current step; keep `HrmClient` subscribed but stop counting `hrConnectedDurationMs`. |
| `PAUSED` | `Start` | — | `RUNNING` | Rewrite `stepStartMonotonicMs = now - pausedAccumulatedMsForCurrentStep`; reschedule the next 5-beep countdown if the step has > 5 s remaining; resume counting `hrConnectedDurationMs`. |
| `RUNNING` or `PAUSED` | `Stop` | — | `EXITED` | Tear down, release audio focus, release overlay if shown, return UI to selection page. **Do not** show the completion card (Spec FR-022). |
| `RUNNING` | `Step Forward` | `currentStepIndex < totalAuthoredSteps` *AND* in v1: ignore repeat-iteration counter (we count steps, not iterations) | `RUNNING` | Capture `SkipCheckpoint(skippedStepIndex = currentStepIndex, elapsedMsAtSkip = stepElapsedMs, accumulatedHrConnectedMsAtSkip = hrConnectedDurationMs)`. Cancel pending beeps for current step. Advance `currentStepIndex`. Reset `stepStartMonotonicMs = now`, `pausedAccumulatedMsForCurrentStep = 0`. Schedule the new step's 5-beep countdown. Set `isStepBackUndoable = true`. |
| `PAUSED` | `Step Forward` | same guards | `PAUSED` | Same checkpoint capture and index advance, but no beeps scheduled (state is still paused). Set `isStepBackUndoable = true`. |
| `RUNNING` or `PAUSED` | `Step Backward` | `isStepBackUndoable == true` | same state | Restore `currentStepIndex = lastSkippedStepCheckpoint.skippedStepIndex`. Restore `stepStartMonotonicMs = now - lastSkippedStepCheckpoint.elapsedMsAtSkip`. Restore `pausedAccumulatedMsForCurrentStep = 0`. Restore `hrConnectedDurationMs = lastSkippedStepCheckpoint.accumulatedHrConnectedMsAtSkip`. Clear `lastSkippedStepCheckpoint`. Set `isStepBackUndoable = false`. Reschedule beeps for the restored step (only if `RUNNING` and step has > 5 s remaining). |
| `RUNNING` | natural step end (`stepElapsedMs >= effectiveDurationSec * 1000`) AND `currentStepIndex < totalAuthoredSteps` | — | `RUNNING` | Advance `currentStepIndex`. Reset clocks. **Clear** `isStepBackUndoable` (FR-019b: any natural transition clears the undo flag). Schedule new step's 5-beep countdown. |
| `RUNNING` | natural step end AND `currentStepIndex == totalAuthoredSteps` | — | `COMPLETE` | Stop scheduling beeps. Compute the completion-card payload from in-memory state (planned vs actual time, avg HR if `hrConnectedDurationMs >= 0.5 * elapsedSessionMs`, planned TSS from Workout). Keep the foreground notification visible. |
| `COMPLETE` | `Done` | — | `EXITED` | Tear down, return to selection. |
| any | HR sample received | engine bound to `HrmClient` | same state | Update `hrSample`. If state is `RUNNING`, advance `hrConnectedDurationMs` by the delta since the previous sample (capped to keep noise bounded). |
| any | HR signal lost / Bluetooth off / permission revoked | — | same state | Set `hrSample = null`. UI renders `—` (Spec FR-017). No state change. |

### Invariants (asserted in tests)

1. `stepElapsedMs + remainingMs == effectiveDurationMs` at every tick.
2. Drift between `wallClock.elapsedRealtime()` and the engine's "ideal"
   step-elapsed never exceeds 100 ms over any contiguous 5-minute window
   (Spec SC-004 + FR-023a tolerance).
3. After any `Step Forward`, `isStepBackUndoable == true` until **either**
   the next natural transition, a `Step Backward`, or `Stop`.
4. `currentStepIndex` is always in `[1, totalAuthoredSteps]`.
5. `RunState.COMPLETE` implies a one-way transition to `EXITED` on `Done`;
   no path returns to `RUNNING`.

### Beep scheduler contract

The countdown beep scheduler is bound to the FSM as follows:

- On entering `RUNNING` for a step `s` whose `effectiveDurationSec` is
  defined and is > 5, schedule 5 beep targets at monotonic times
  `stepStartMonotonicMs + (effectiveDurationSec - 5 + k) * 1000` for
  `k ∈ {0,1,2,3,4}`.
- On entering `RUNNING` for a step with `effectiveDurationSec` defined and
  `<= 5`, schedule beeps only for the seconds that fit (e.g., a 3-second
  step gets the last 3 beeps).
- On entering `RUNNING` for a step with `effectiveDurationSec == null`
  ("open"), schedule **no** beeps (FR-023a explicitly excludes "open" steps).
- On `Pause`, `Stop`, `Step Forward`, `Step Backward`, or service shutdown:
  cancel all pending beep tasks for the current step.

## Observability surface

The UI and mini view subscribe to one type — `StateFlow<RunUiState>` —
projected from `RunSession` by the service:

```kotlin
data class RunUiState(
    val workoutDisplayName: String,
    val totalAuthoredSteps: Int,           // N in "step n of N", FR-017a
    val currentStepIndex: Int,
    val currentStepName: String?,          // null → "—"
    val currentIntensity: Intensity,
    val currentTargetDisplay: String,      // formatted by DisplayUnit, FR-013a
    val nextTargetDisplay: String?,        // null → final step → render "—"
    val currentRepeatLabel: String?,       // "3 of 6" or null, FR-018
    val stepElapsedSec: Int,
    val stepRemainingSec: Int,
    val sessionElapsedSec: Int,
    val sessionRemainingSec: Int,
    val hrBpm: Int?,                       // null → "—"
    val state: RunState,
    val controls: RunControlsState,        // Start/Pause/Stop/Forward/Backward enabled flags
    val playheadFraction: Float,           // 0.0..1.0 across the *expanded* timeline
)
```

This is the **only** data flow from engine → UI. The UI never reads
`RunSession` directly, which keeps the FSM testable in isolation.
