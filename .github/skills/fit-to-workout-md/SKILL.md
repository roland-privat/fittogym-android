---
name: fit-to-workout-md
description: 'Interpret a Garmin .fit file containing a running workout (planned or recorded) and produce a human-readable workout description in Markdown. Use when the user asks to "summarize a .fit file", "convert fit to markdown", "describe a Garmin workout", "explain this run workout", or points at a `.fit` running file and wants a written-out structure (warm-up, intervals, recovery, cool-down, targets). Handles both Workout files (planned, with workout_step messages) and Activity files (recorded, with record/lap/session messages). Output is a single .md file next to the input.'
argument-hint: '<path-to-.fit-file> [output.md]'
---

# Garmin .fit → Markdown Workout Description

Turn a binary Garmin `.fit` file (running) into a clear, human-readable Markdown workout description.

## When to Use

Trigger this skill when the user:
- Provides a `.fit` file and asks for a summary, description, or write-up of the workout.
- Says "convert / interpret / explain / describe this fit file" for a running workout.
- Wants a Markdown version of a planned Garmin running workout (warm-up / intervals / cool-down with targets).
- Asks to compare a `.fit` against a `.zwo` (Zwift) workout in this repo.

Do **not** use for:
- Cycling power-based workouts where the user wants `.zwo` / Zwift output (different format, different targets).
- Bulk export of raw record-by-record GPS/HR data to CSV — use `FitCSVTool` directly.

## Two Kinds of .fit Files

`.fit` is one container with two very different payloads. **Detect which one you have before formatting:**

| Type | Key messages | What it represents |
|------|--------------|--------------------|
| **Workout** (planned) | `workout`, `workout_step` | Structured plan: steps with duration/intensity targets. No GPS/HR samples. |
| **Activity** (recorded) | `file_id.type == activity`, `session`, `lap`, `record` | A completed run: per-second GPS/HR/pace samples + lap summaries. |

The MD output differs:
- **Workout** → list of steps with their *targets* (HR zone, pace range, time/distance).
- **Activity** → summary (total time, distance, avg/max HR, avg pace) + per-lap table + optional notes.

If both kinds of data exist in one file (rare; some devices embed the plan into the activity), produce **both** sections: "Planned" and "Recorded".

## Procedure

### 1. Inspect inputs

1. Confirm the input path ends in `.fit`. If the user gave a folder, list `*.fit` and ask which one (or process all if they said "all").
2. Default output path: same folder, same stem, `.md` extension. Override with the second argument.

### 2. Parse the file

Use the Python helper [`scripts/fit_to_md.py`](./scripts/fit_to_md.py). It depends on the `fitparse` package.

```powershell
# one-time
python -m pip install --user fitparse

# run
python .github/skills/fit-to-workout-md/scripts/fit_to_md.py `
    "TrainingPeaksData/2026-05-27_NoLetUp.fit" `
    "TrainingPeaksData/2026-05-27_NoLetUp.md"
```

If `fitparse` cannot be installed (offline / restricted env), fall back to Garmin's official **FitCSVTool** (`FitCSVTool.jar` — requires Java). Convert to CSV first, then parse the CSV. See [`references/fit-format.md`](./references/fit-format.md) for the message/field cheat-sheet.

### 3. Map raw fields → Markdown

For a **Workout file**, emit:

```markdown
# {workout_name}

- **Sport:** Running
- **Steps:** {num_valid_steps}
- **Source file:** `{relative path to .fit}`

## Steps

| # | Name | Intensity | Duration / Distance | Target | Repeat |
|---|------|-----------|---------------------|--------|--------|
| 1 | Warm up | warmup | 10:00 | HR Zone 1–2 (110–140 bpm) | — |
| 2 | Interval | active  | 1.00 km | Pace 4:00–4:15 /km | ×5 |
| 3 | Recovery | rest    | 2:00 | open | (part of repeat) |
| 4 | Cool down | cooldown | 10:00 | open | — |

## Notes
{workout_step.notes if present, otherwise omit}
```

Field mapping (workout_step):
- `duration_type` ∈ {`time`, `distance`, `open`, `repeat_until_steps_cmplt`, …} → choose the duration column accordingly.
- `target_type` ∈ {`heart_rate`, `speed`, `power`, `open`}; combine with `custom_target_*_low/high` or `target_hr_zone` / `target_speed_zone` to render the **Target** column. Convert speed (m/s) to pace (`mm:ss /km`).
- Repeats: a step with `duration_type == repeat_until_steps_cmplt` collapses the previous N steps into a single row with `×N` in the Repeat column.
- **Render what is in the file, don't translate.** If the step stores a zone (e.g. `target_hr_zone = 3`), render "HR Zone 3". If it stores raw values (e.g. `custom_target_heart_rate_low/high = 145/160`), render "HR 145–160 bpm". Do not invent the other.
- After the table, append a **Hints** section telling the user how to get the *other* representation:
  - If any step used a zone → hint: "to render absolute bpm/pace, drop a `zones.yaml` next to the .fit with your max HR / pace zones and rerun."
  - If any step used absolute values → hint: "to render these as zones (Z1–Z5), drop a `zones.yaml` with your zone boundaries and rerun."
  (The `zones.yaml` consumer is a future extension; the hint already documents the contract.)

For an **Activity file**, emit:

```markdown
# Run — {session.start_time:YYYY-MM-DD}

- **Sport:** Running ({sub_sport})
- **Duration:** {total_timer_time → h:mm:ss}
- **Distance:** {total_distance/1000:.2f} km
- **Avg pace:** {avg_speed → mm:ss /km}
- **Avg / Max HR:** {avg_heart_rate} / {max_heart_rate} bpm
- **Elevation gain:** {total_ascent} m
- **Calories:** {total_calories} kcal

## Laps

| # | Time | Distance | Avg pace | Avg HR | Max HR |
|---|------|----------|----------|--------|--------|
| 1 | 10:00 | 1.80 km | 5:33 /km | 132 | 145 |
| … |
```

Skip a column if every lap has the field empty. Round distances to 0.01 km, pace to whole seconds, HR to integer.

Also append a **Profile** section with one-line Unicode sparklines (8 levels: `▁▂▃▄▅▆▇█`) for `record.heart_rate` and `record.enhanced_altitude` (falling back to `altitude`). Down-sample to ~60 columns by mean. Skip a series that has fewer than 2 samples.

### 4. Sanity checks before writing

- Total of lap times ≈ session `total_timer_time` (±2 s). If off, add a `> Note: lap sum differs from session total by Xs` line.
- If `sport != running`, stop and tell the user — this skill is running-only.
- Pace conversion: `pace_sec_per_km = 1000 / speed_m_per_s`. Guard against `speed == 0`.

### 5. Write and confirm

Write the file with UTF-8, LF line endings, no BOM. Print the output path back to the user and a 3-line preview of the Markdown.

## Edge Cases

- **Multi-sport / triathlon .fit**: only emit the running portion(s); note that other sports were skipped.
- **Power-based running targets** (Stryd): render as `Power 230–250 W` in the Target column.
- **Missing workout name**: derive from filename stem.
- **Locale / units**: always output metric (km, m, bpm, mm:ss /km). If the user explicitly asks for imperial, convert in the formatter, never in the parser.

## References

- [`references/fit-format.md`](./references/fit-format.md) — message types and field names used by this skill.
- [`scripts/fit_to_md.py`](./scripts/fit_to_md.py) — implementation.
