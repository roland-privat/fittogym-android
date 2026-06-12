---
name: zwo-to-workout-md
description: 'Interpret a Zwift `.zwo` workout XML file (TrainingPeaks export, running) and produce a human-readable workout description in Markdown. Use when the user asks to "summarize a zwo file", "convert zwo to markdown", "describe this Zwift workout", or points at a `.zwo` running file and wants a written-out structure (warm-up, intervals, recovery, cool-down, paces). Converts Zwift Power fractions to pace using `thresholdSecPerKm` from the file (or from a sibling `zones.yaml`). Output is a single .md file next to the input.'
argument-hint: '<path-to-.zwo-file> [output.md]'
---

# Zwift .zwo → Markdown Workout Description

Turn a `.zwo` Zwift workout XML (running) into a clear, human-readable Markdown workout description.

## When to Use

Trigger this skill when the user:
- Provides a `.zwo` file and asks for a summary, description, or write-up.
- Says "convert / interpret / explain / describe this zwo file" for a running workout.
- Wants a Markdown version of a Zwift running workout (warm-up / intervals / cool-down with paces).
- Asks to compare a `.zwo` against a `.fit` workout (pair with the `fit-to-workout-md` skill).

Do **not** use for:
- Cycling power-based workouts — possible, but pace conversion is meaningless; the user probably wants raw FTP %.
- Workouts with `<FreeRide>` blocks (Zwift-only); render them but note they have no defined pace.

## The .zwo Format (running)

`.zwo` is plain XML. The relevant tree:

```xml
<workout_file>
  <name>...</name>
  <description>...</description>            <!-- multi-line text, often the human-readable plan -->
  <sportType>run</sportType>
  <thresholdSecPerKm>330</thresholdSecPerKm> <!-- the user's threshold pace; 1.00 power = this -->
  <workout>
    <Warmup     Duration="..." PowerLow="..." PowerHigh="..." />
    <SteadyState Duration="..." Power="..."                    />
    <IntervalsT Repeat="..." OnDuration="..." OnPower="..."
                              OffDuration="..." OffPower="..." />
    <Cooldown   Duration="..." PowerLow="..." PowerHigh="..." />
    <FreeRide   Duration="..."                                 />
    <!-- each block may contain <textevent timeoffset="s" message="..."/> children -->
  </workout>
</workout_file>
```

- `Power` is a fraction of `thresholdSecPerKm`. **Pace = `thresholdSecPerKm / Power`** (seconds per km).
  - Power = 1.00 → threshold pace.
  - Power = 0.80 → much slower (easy).
  - Power = 1.10 → much faster (VO2).
- `Warmup` / `Cooldown` are *ramps* — `PowerLow` to `PowerHigh` over `Duration`.
- `IntervalsT` is the canonical interval block: `Repeat` x (`OnDuration` at `OnPower`, then `OffDuration` at `OffPower`).
- `<textevent>` elements give per-block coaching cues at `timeoffset` seconds; preserve them.

## Procedure

### 1. Inspect input

1. Confirm the input path ends in `.zwo`. If the user gave a folder, list `*.zwo` and ask which one.
2. Default output path: same folder, same stem, `.md` extension.

### 2. Parse

Use [`scripts/zwo_to_md.py`](./scripts/zwo_to_md.py). Pure stdlib — no extra packages.

```powershell
python .github/skills/zwo-to-workout-md/scripts/zwo_to_md.py `
    "TrainingPeaksData/2026-05-27_NoLetUp.zwo"
```

### 3. Render

Emit:

```markdown
# {name}

- **Sport:** {sportType}
- **Threshold pace:** {thresholdSecPerKm} → `mm:ss /km`
- **Source file:** `{relative path}`

> {description, verbatim, indented as a blockquote}

## Steps

| # | Block | Duration | Power | Pace | Notes |
|---|-------|----------|-------|------|-------|
| 1 | Warm-up ramp | 5:00 | 75 → 95 % | 7:20 → 5:47 /km | Steady |
| 2 | Steady       | 3:00 | 92 %      | 5:59 /km        | |
| 3 | Intervals ×6 | 2:00 + 5:00 | 88 % / 96 % | 6:15 / 5:44 /km | Build to this pace (0s) |
| 4 | Cool-down    | 7:00 | 95 → 75 % | 5:47 → 7:20 /km | |
```

Formatting rules:
- Duration: `mm:ss` if < 1 h, else `h:mm:ss`.
- Power as percent, rounded to nearest integer.
- Pace: compute from `thresholdSecPerKm / power`, format `mm:ss /km`.
- For ramps, render both endpoints with `→`.
- For `IntervalsT`, render as a single row with `On + Off` in the Duration column and `OnPower / OffPower` in Power; put `×N` in the Block column. Total work time = `N * (On + Off)`.
- `<textevent>` children → Notes column, joined with `;` and tagged with their `timeoffset` if non-zero.
- Skip a column entirely if every block leaves it empty.

### 4. Sanity checks

- If `sportType` is not `run`, warn at the top of the output but still render.
- If `thresholdSecPerKm` is missing or zero, omit the Pace column and add a hint about configuring a sibling `zones.yaml` (see below) with `threshold_pace_sec_per_km:`.
- Sum block durations and print total workout time in the header.

### 5. Optional `zones.yaml` overlay

If a sibling file `zones.yaml` (same folder as the .zwo, or workspace root) exists, also render zone labels in the Pace column. Format and behaviour are documented in the [`fit-to-workout-md` zones reference](../fit-to-workout-md/references/zones.md).

For a Pace zone whose range covers the computed pace, append ` (Zn)`. If multiple zones overlap, pick the narrowest. If no overlay file → render only the absolute pace and add the "configure `zones.yaml` to also show zones" hint, mirroring the .fit skill.

### 6. Write and confirm

UTF-8, LF, no BOM. Print the output path and a 3-line preview.

## Edge Cases

- **Description with blank lines**: render as a single blockquote, blank lines become `>` lines so Markdown keeps it as one block.
- **`<FreeRide>`**: emit row with Duration only; Power and Pace columns are `—`; Notes: "Free ride (no target)".
- **`<IntervalsT>` with `OffDuration="0"`**: render as just the On segment ×N.
- **Power > 1.5 or < 0.4**: render but include `> Note: unusual power value` after the table.
- **Locale**: always metric. If the input uses `units="0"` (imperial), the underlying durations are still seconds — no conversion needed.

## References

- [`scripts/zwo_to_md.py`](./scripts/zwo_to_md.py) — implementation.
- [Zwift .zwo schema (community docs)](https://github.com/h4l/zwift-workout-file-reference)
