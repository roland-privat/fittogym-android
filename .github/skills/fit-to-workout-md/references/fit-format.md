# Garmin .fit message/field cheat-sheet (running)

Only the fields this skill consumes. Full spec: <https://developer.garmin.com/fit/protocol/>.

## File type detection

Read the single `file_id` message:

| `file_id.type` | Meaning |
|----------------|---------|
| `activity`     | Recorded session (records + laps + session). |
| `workout`      | Planned workout (workout + workout_step). |
| `course`       | Course/route. Ignore for this skill. |

## Workout files

### `workout` (1 message)
- `wkt_name` — string, workout title.
- `sport` — should be `running`.
- `num_valid_steps` — int.

### `workout_step` (N messages, ordered by `message_index`)
- `wkt_step_name` — string, optional.
- `intensity` — one of `active`, `rest`, `warmup`, `cooldown`, `recovery`.
- `duration_type`:
  - `time` → `duration_time` (seconds).
  - `distance` → `duration_distance` (meters; some files store cm — divide by 100 if value > 100 000).
  - `open` → no fixed duration (user presses lap).
  - `repeat_until_steps_cmplt` → `duration_value` = index of step to repeat from; `target_value` = repeat count.
  - `hr_less_than` / `hr_greater_than` → `duration_value` = bpm threshold.
- `target_type`:
  - `open` → no target.
  - `heart_rate` →
    - if `target_hr_zone` ∈ 1..5 → preset zone.
    - if `target_hr_zone == 0` → custom: `custom_target_heart_rate_low/high` (raw bpm if > 100, else %max).
  - `speed` → `custom_target_speed_low/high` in m/s. Convert to pace.
  - `power` → `custom_target_power_low/high` in W (Stryd / running power).
  - `cadence` → `custom_target_cadence_low/high` rpm.
- `notes` — string, optional.

## Activity files

### `session` (1 message, end of file)
- `sport`, `sub_sport`
- `start_time` (datetime)
- `total_timer_time` (s), `total_elapsed_time` (s)
- `total_distance` (m)
- `avg_speed`, `max_speed` (m/s)
- `avg_heart_rate`, `max_heart_rate` (bpm)
- `total_ascent`, `total_descent` (m)
- `total_calories` (kcal)
- `avg_cadence`, `max_cadence` (rpm — note: running cadence in fit is *strides per minute = steps/2*; multiply by 2 for spm).

### `lap` (N messages)
Same fields as session, scoped to one lap. Use these to build the lap table.

### `record` (many, ~1 Hz)
Not used by this skill (would explode the MD). Reserved for future "elevation profile" / "HR chart" extensions.

## Conversions

- Pace: `seconds_per_km = 1000 / speed_m_per_s`; format as `mm:ss` (e.g. `5:33`).
- Distance display: km with 2 decimals.
- Duration display: `h:mm:ss` if ≥ 1 h, else `mm:ss`.
- Garmin semicircles → degrees (only if record-level GPS ever gets used): `deg = semicircles * (180 / 2^31)`.

## Sentinel / invalid values

`fitparse` already strips them, but if parsing CSV from FitCSVTool, treat these as missing:

| Type | Invalid sentinel |
|------|------------------|
| uint8  | 0xFF |
| uint16 | 0xFFFF |
| uint32 | 0xFFFFFFFF |
| sint*  | most-negative value |
| string | empty |
