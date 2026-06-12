# Phase 1 Data Model: Core Workout Flow

**Date**: 2026-05-25 · **Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)

This document defines the persistent and in-memory data shapes that back the
Spec's Key Entities. Two storage surfaces:

1. **Room SQLite** database (`run-training.db`) for structured data.
2. **DataStore Preferences** file (`app-settings.preferences_pb`) for the
   small App-Settings tuple.
3. **App-private files** under `filesDir/workouts/<sha256>.fit` for raw
   imported `.fit` blobs.

In-memory `RunSession` state is **not persisted** (Spec Assumption "No
workout resume after process death").

## 1. Room schema (v1)

### `workout`

| column | type | notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | stable internal ID |
| `content_hash` | `TEXT NOT NULL UNIQUE` | SHA-256 hex of the raw `.fit` bytes |
| `original_filename` | `TEXT NOT NULL` | as received from the share/open intent |
| `display_name` | `TEXT NOT NULL` | initially `original_filename` without `.fit`; user-editable |
| `imported_at_epoch_ms` | `INTEGER NOT NULL` | timestamp at successful import |
| `sport` | `TEXT NOT NULL` | always `"running"` in v1 (Spec Assumption) |
| `planned_duration_sec` | `INTEGER NOT NULL` | sum of step effective durations |
| `planned_distance_m` | `INTEGER NOT NULL` | computed per Spec FR-004a / Assumptions |
| `tss` | `REAL` (nullable) | `null` ⇒ render `—` on UI |
| `blob_relative_path` | `TEXT NOT NULL` | e.g. `workouts/<sha256>.fit` |

**Indexes**: implicit unique on `content_hash`; non-unique on
`imported_at_epoch_ms DESC` to make the selection-page sort fast.

**Maps to**: Spec Key Entity *Workout*. `tss` nullable matches Spec FR-004
"unknown" marker.

### `repeat_group`

| column | type | notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | |
| `workout_id` | `INTEGER NOT NULL` | FK → `workout.id`, `ON DELETE CASCADE` |
| `order_in_workout` | `INTEGER NOT NULL` | 0-based among the workout's repeat groups |
| `iteration_count` | `INTEGER NOT NULL CHECK (iteration_count >= 1)` | the `N` in `n of N` |

**Index**: `(workout_id, order_in_workout)`.

**Maps to**: Spec Key Entity *Repeat Group*.

### `workout_step`

| column | type | notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | |
| `workout_id` | `INTEGER NOT NULL` | FK → `workout.id`, `ON DELETE CASCADE` |
| `step_index` | `INTEGER NOT NULL` | 1-based ordinal in the authored step list (Spec FR-017a) |
| `step_name` | `TEXT` (nullable) | from FIT `workout_step.name`; `null` ⇒ render `—` |
| `intensity` | `TEXT NOT NULL` | one of `warmup`,`active`,`rest`,`cooldown`,`other` |
| `source_duration_kind` | `TEXT NOT NULL` | `time` \| `distance` \| `open` |
| `source_duration_sec` | `INTEGER` (nullable) | populated iff `source_duration_kind = 'time'` |
| `source_distance_m` | `INTEGER` (nullable) | populated iff `source_duration_kind = 'distance'` |
| `effective_duration_sec` | `INTEGER` (nullable) | derived per FR-004a; `null` if the step is effectively "open" |
| `target_kind` | `TEXT NOT NULL` | `pace` \| `open` |
| `target_pace_lower_sec_per_km` | `INTEGER` (nullable) | faster bound (smaller sec/km) |
| `target_pace_upper_sec_per_km` | `INTEGER` (nullable) | slower bound |
| `zone_label` | `TEXT` (nullable) | e.g. `"Z2"`, free text from the FIT step |
| `repeat_group_id` | `INTEGER` (nullable) | FK → `repeat_group.id`, `ON DELETE SET NULL`; non-null iff the step is inside a repeat |
| `position_in_repeat` | `INTEGER` (nullable) | 0-based position within its repeat group; non-null iff `repeat_group_id` is non-null |

**Indexes**: `(workout_id, step_index)`, `(repeat_group_id, position_in_repeat)`.

**Invariants** (enforced in Kotlin at import time, not as DB `CHECK`
constraints — keep schema simple):

- `step_index` is contiguous starting at 1 within a `workout_id`.
- If `source_duration_kind = 'open'`, both `source_duration_sec` and
  `source_distance_m` are `NULL` and `effective_duration_sec` is `NULL`.
- If `target_kind = 'open'`, both pace columns are `NULL`. If `'pace'`, both
  are `NOT NULL` and `lower <= upper`.
- A step has at most one `repeat_group_id`; repeat groups don't nest in v1.
- Steps inside a repeat group are contiguous in `step_index`.

**Maps to**: Spec Key Entity *Step*, extended per the Q6 clarification
(ordinal index + step name).

### `hr_monitor_pairing`

| column | type | notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY CHECK (id = 1)` | singleton row |
| `device_id` | `TEXT NOT NULL` | BLE device address |
| `last_known_name` | `TEXT NOT NULL` | as advertised |
| `last_connected_epoch_ms` | `INTEGER NOT NULL` | for auto-reconnect heuristics |

**Maps to**: Spec Key Entity *HR Monitor Pairing*.

## 2. DataStore: `app-settings.preferences_pb`

| preference key | type | default | notes |
|---|---|---|---|
| `threshold_pace_sec_per_km` | `Int?` | `null` (unset) | Spec Assumption "Threshold pace source" |
| `display_unit` | `String` | `"pace"` | one of `"pace"`, `"speed"` (FR-013a) |
| `last_paired_device_id` | `String?` | `null` | cached for instant cold-start reconnect; canonical pairing record remains in `hr_monitor_pairing` |

**Maps to**: Spec Key Entity *App Settings*.

## 3. Domain (in-memory) types

These are the Kotlin data classes the UI and engine work with. They mirror
the Room rows but with **non-nullable** invariants enforced at the boundary
(DAO → Domain mapping in `persistence/db/Mappers.kt`).

```kotlin
data class Workout(
    val id: Long,
    val contentHash: String,
    val originalFilename: String,
    val displayName: String,
    val importedAtEpochMs: Long,
    val plannedDurationSec: Int,
    val plannedDistanceM: Int,
    val tss: Double?,             // null → render "—"
    val steps: List<Step>,        // ordered by step_index, size >= 0
    val repeatGroups: List<RepeatGroup>,
)

data class Step(
    val id: Long,
    val stepIndex: Int,           // 1-based, Spec FR-017a
    val name: String?,            // null → render "—"
    val intensity: Intensity,
    val sourceDuration: SourceDuration,
    val effectiveDurationSec: Int?,   // null → step is "open"; un-runnable
    val target: Target,
    val zoneLabel: String?,
    val inRepeat: RepeatPosition?,    // null → step is not inside a repeat
)

enum class Intensity { WARMUP, ACTIVE, REST, COOLDOWN, OTHER }

sealed interface SourceDuration {
    data class Time(val seconds: Int) : SourceDuration
    data class Distance(val meters: Int) : SourceDuration
    data object Open : SourceDuration
}

sealed interface Target {
    data class Pace(val lowerSecPerKm: Int, val upperSecPerKm: Int) : Target {
        val midpointSecPerKm: Int get() = (lowerSecPerKm + upperSecPerKm) / 2
    }
    data object Open : Target
}

data class RepeatPosition(
    val repeatGroupId: Long,
    val positionInRepeat: Int,    // 0-based
)

data class RepeatGroup(
    val id: Long,
    val orderInWorkout: Int,
    val iterationCount: Int,      // the "N" in "n of N", Spec FR-018
)

data class HrMonitorPairing(
    val deviceId: String,
    val lastKnownName: String,
    val lastConnectedEpochMs: Long,
)

data class AppSettings(
    val thresholdPaceSecPerKm: Int?,
    val displayUnit: DisplayUnit,
    val lastPairedDeviceId: String?,
)

enum class DisplayUnit { PACE, SPEED }     // FR-013a
```

### `RunSession` (transient — owned by `WorkoutForegroundService`)

```kotlin
data class RunSession(
    val workout: Workout,
    val state: RunState,
    val currentStepIndex: Int,            // 1-based, matches Step.stepIndex
    val currentRepeatIteration: Int?,     // 1..N for steps inside a repeat, else null
    val stepStartMonotonicMs: Long,       // SystemClock.elapsedRealtime() at step start
    val pausedAccumulatedMsForCurrentStep: Long,
    val sessionStartMonotonicMs: Long,    // for elapsed/avg-HR math
    val sessionPausedAccumulatedMs: Long,
    val isStepBackUndoable: Boolean,      // Spec FR-019b
    val lastSkippedStepCheckpoint: SkipCheckpoint?,
    val hrSample: HrSample?,              // last received from HrmClient
    val hrConnectedDurationMs: Long,      // for the "≥ 50% of session" rule in FR-022a
)

enum class RunState { IDLE, RUNNING, PAUSED, COMPLETE }

data class SkipCheckpoint(               // backing FR-019b
    val skippedStepIndex: Int,
    val elapsedMsAtSkip: Long,
    val accumulatedHrConnectedMsAtSkip: Long,
)

data class HrSample(
    val bpm: Int,                         // 0..255
    val monotonicTimestampMs: Long,
)
```

The transient nature is critical — see `RunState` transitions in
[contracts/run-session-state-machine.md](contracts/run-session-state-machine.md).

## 4. Validation rules (boundary)

These run at the import boundary (`FitDecoder.Result.Ok → persisted Workout`)
and must reject the import on failure:

- **Sport must be `running`** (Spec Assumption). Any other sport → import
  fails with a visible error.
- **`workout` row** values must satisfy: `planned_duration_sec >= 0`,
  `planned_distance_m >= 0`, `display_name` non-empty (default to
  `original_filename` without extension if empty after trim).
- **`workout_step` rows**: at least 1 step. `step_index` contiguous from 1.
  Pace bounds, if present, satisfy `lower <= upper` and both in
  `[120, 1200]` sec/km (i.e., 2:00–20:00 /km). Outside this range the step's
  pace target is treated as missing (`target_kind = 'open'`) and the user
  sees a soft warning on the details page.
- **`repeat_group` rows**: `iteration_count` in `[2, 99]`. A group of
  `iteration_count = 1` is unwrapped at import (just inline the steps with
  no group).
- **`Workout.steps`** must not all be `effective_duration_sec = NULL`; if
  they are, the workout is still importable but `Workout.isRunnable = false`
  in the domain layer (Spec Edge Case "Workout with zero steps or only
  'open' steps").

## 5. Derived / read-only fields the UI consumes

Computed at query time or in the ViewModel — **not stored**:

- `Workout.totalAuthoredSteps` ⇒ `steps.size` (the `N` in `step n of N`).
- `Workout.totalExpandedSteps` ⇒ sum over steps of `(repeat_iteration_count if in repeat else 1)`. Used **only** to compute timeline rectangle widths after expansion; not shown as a counter.
- `Workout.isRunnable` ⇒ `steps.any { it.effectiveDurationSec != null }`.
- `Workout.displayedTargetForRunPage(step, settings.displayUnit)` ⇒ formatted
  pace `min:ss/km` or speed `km/h` per FR-013a.
- `RunSession.elapsedSessionSec` ⇒ derived from monotonic clocks.
- `RunSession.remainingSessionSec` ⇒ planned − elapsed (with respect to the
  current step's elapsed and any prior step skips).
- `RunSession.averageHrBpm` ⇒ running mean of received samples while
  `state = RUNNING` (paused time excluded). Used for the completion-card
  HR row when `hrConnectedDurationMs >= 0.5 * elapsedSessionMs`
  (Spec FR-022a).

## 6. Migration policy

- v1 ships with the schema above and **no migrations**.
- The next schema change MUST be a Room `Migration(from, to)` with an
  explicit `ALTER TABLE` or data-rewrite — never `fallbackToDestructiveMigration()`
  in production. (See user memory rule: data-preserving migrations are the
  default.)
- Raw `.fit` blobs are content-addressed and immutable, so they require no
  migration story.

## 7. Storage size budget

- One workout row: ~150 bytes
- One step row: ~120 bytes; pathological workout (60 steps) ≈ 7 kB
- Raw `.fit` blob: ~5–30 kB typical
- 200 workouts ≈ 200 × (150 + 7 000 + 30 000) ≈ 7.5 MB — well below any
  device storage concern.
