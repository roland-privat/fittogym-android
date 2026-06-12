"""Convert a Garmin .fit running file (workout or activity) to a Markdown description.

Usage:
    python fit_to_md.py <input.fit> [output.md]

Requires: fitparse  (pip install fitparse)
"""

from __future__ import annotations

import sys
from datetime import timedelta
from pathlib import Path
from typing import Any, Iterable

try:
    from fitparse import FitFile
except ImportError:  # pragma: no cover
    sys.stderr.write(
        "ERROR: fitparse is not installed. Run: python -m pip install --user fitparse\n"
    )
    sys.exit(2)


# ---------- formatting helpers ----------


def fmt_duration(seconds: float | None) -> str:
    if seconds is None:
        return "—"
    s = int(round(float(seconds)))
    h, rem = divmod(s, 3600)
    m, sec = divmod(rem, 60)
    return f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"


def fmt_distance_km(meters: float | None) -> str:
    if meters is None:
        return "—"
    return f"{meters / 1000:.2f} km"


def fmt_pace(speed_mps: float | None) -> str:
    if not speed_mps or speed_mps <= 0:
        return "—"
    sec_per_km = 1000.0 / float(speed_mps)
    m, s = divmod(int(round(sec_per_km)), 60)
    return f"{m}:{s:02d} /km"


def first_value(msg, name: str) -> Any:
    f = msg.get(name)
    return f.value if f else None


# ---------- zones overlay (optional) ----------


def load_zones(src_path: Path) -> dict[str, Any]:
    """Find and load an optional sibling `zones.yaml`. Returns {} on miss/error."""
    candidates = [
        src_path.with_name("zones.yaml"),
        src_path.parent.parent / "zones.yaml",
        Path.cwd() / "zones.yaml",
    ]
    for c in candidates:
        if c.is_file():
            try:
                return _parse_zones(c.read_text(encoding="utf-8"))
            except Exception as e:  # noqa: BLE001
                sys.stderr.write(f"WARN: ignoring malformed {c}: {e}\n")
                return {}
    return {}


def _parse_zones(text: str) -> dict[str, Any]:
    try:
        import yaml  # type: ignore

        return yaml.safe_load(text) or {}
    except ImportError:
        pass
    out: dict[str, Any] = {}
    cur_key: str | None = None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if not line.startswith((" ", "\t")):
            k, _, v = line.partition(":")
            k, v = k.strip(), v.strip()
            if v:
                out[k] = _coerce_scalar(v)
                cur_key = None
            else:
                out[k] = {}
                cur_key = k
        else:
            if cur_key is None:
                continue
            k, _, v = line.strip().partition(":")
            out[cur_key][k.strip()] = _coerce_list(v.strip())
    return out


def _coerce_scalar(v: str) -> Any:
    try:
        return int(v)
    except ValueError:
        pass
    try:
        return float(v)
    except ValueError:
        pass
    return v.strip("'\"")


def _coerce_list(v: str) -> Any:
    v = v.strip()
    if v.startswith("[") and v.endswith("]"):
        inner = v[1:-1]
        return [_coerce_scalar(p.strip()) for p in inner.split(",") if p.strip()]
    return _coerce_scalar(v)


def zone_for_range(lo: float, hi: float, zone_map: dict[str, Any] | None) -> str | None:
    """Return the narrowest zone label whose [lo,hi] fully contains [lo,hi] input."""
    if not zone_map:
        return None
    if lo > hi:
        lo, hi = hi, lo
    best: tuple[float, str] | None = None
    for name, rng in zone_map.items():
        if not isinstance(rng, (list, tuple)) or len(rng) != 2:
            continue
        zlo, zhi = float(rng[0]), float(rng[1])
        if zlo > zhi:
            zlo, zhi = zhi, zlo
        if zlo <= lo and hi <= zhi:
            span = zhi - zlo
            if best is None or span < best[0]:
                best = (span, str(name).upper())
    return best[1] if best else None


_SPARK_BLOCKS = "▁▂▃▄▅▆▇█"


def sparkline(values: Iterable[float | None], width: int = 60) -> str:
    """Render a one-line Unicode sparkline. `width` is the target column count."""
    vals = [float(v) for v in values if v is not None]
    if len(vals) < 2:
        return ""
    # Down-sample to `width` buckets by averaging.
    n = len(vals)
    if n > width:
        bucket = n / width
        buckets: list[float] = []
        for i in range(width):
            lo = int(i * bucket)
            hi = int((i + 1) * bucket) or lo + 1
            chunk = vals[lo:hi]
            if chunk:
                buckets.append(sum(chunk) / len(chunk))
        vals = buckets
    lo, hi = min(vals), max(vals)
    if hi - lo < 1e-9:
        return _SPARK_BLOCKS[0] * len(vals)
    span = hi - lo
    out = []
    for v in vals:
        idx = int((v - lo) / span * (len(_SPARK_BLOCKS) - 1))
        out.append(_SPARK_BLOCKS[idx])
    return "".join(out)


# ---------- workout (planned) ----------


def render_workout_target(step, zones: dict[str, Any] | None = None) -> str:
    ttype = first_value(step, "target_type")
    if ttype in (None, "open"):
        return "open"

    if ttype == "heart_rate":
        zone = first_value(step, "target_hr_zone")
        lo = first_value(step, "custom_target_heart_rate_low")
        hi = first_value(step, "custom_target_heart_rate_high")
        if zone and zone != 0:
            return f"HR Zone {zone}"
        if lo is not None and hi is not None:
            # >100 → raw bpm, else % of max
            unit = "bpm" if lo > 100 else "% max"
            base = f"HR {int(lo)}–{int(hi)} {unit}"
            if unit == "bpm" and zones:
                z = zone_for_range(float(lo), float(hi), zones.get("hr_zones"))
                if z:
                    return f"{base} ({z})"
            return base
        return "HR (unspecified)"

    if ttype == "speed":
        lo = first_value(step, "custom_target_speed_low")
        hi = first_value(step, "custom_target_speed_high")
        if lo and hi:
            # fastest pace comes from highest speed → render hi..lo
            base = f"Pace {fmt_pace(hi).replace(' /km', '')}–{fmt_pace(lo)}"
            if zones:
                # Pace seconds/km: faster speed → lower seconds. Range covers [1000/hi .. 1000/lo].
                pace_lo = 1000.0 / float(hi)
                pace_hi = 1000.0 / float(lo)
                z = zone_for_range(pace_lo, pace_hi, zones.get("pace_zones"))
                if z:
                    return f"{base} ({z})"
            return base
        return "Pace (unspecified)"

    if ttype == "power":
        lo = first_value(step, "custom_target_power_low")
        hi = first_value(step, "custom_target_power_high")
        if lo is not None and hi is not None:
            return f"Power {int(lo)}–{int(hi)} W"
        return "Power (unspecified)"

    if ttype == "cadence":
        lo = first_value(step, "custom_target_cadence_low")
        hi = first_value(step, "custom_target_cadence_high")
        if lo is not None and hi is not None:
            return f"Cadence {int(lo)}–{int(hi)} rpm"
        return "Cadence (unspecified)"

    return str(ttype)


def render_workout_duration(step) -> str:
    dtype = first_value(step, "duration_type")
    if dtype == "time":
        return fmt_duration(first_value(step, "duration_time"))
    if dtype == "distance":
        d = first_value(step, "duration_distance")
        # Some files store cm — heuristic correction
        if d is not None and d > 100_000:
            d = d / 100
        return fmt_distance_km(d)
    if dtype == "open":
        return "open"
    if dtype and dtype.startswith("hr_"):
        return f"until {dtype.replace('_', ' ')} {first_value(step, 'duration_value')}"
    if dtype == "repeat_until_steps_cmplt":
        return "(repeat)"
    return str(dtype) if dtype else "—"


def render_workout_file(ff: FitFile, src: Path) -> str:
    wkt = next(ff.get_messages("workout"), None)
    steps = list(ff.get_messages("workout_step"))
    zones = load_zones(src)

    title = (wkt and first_value(wkt, "wkt_name")) or src.stem
    sport = (wkt and first_value(wkt, "sport")) or "running"

    lines: list[str] = []
    lines.append(f"# {title}")
    lines.append("")
    lines.append(f"- **Sport:** {sport}")
    lines.append(f"- **Steps:** {len(steps)}")
    lines.append(f"- **Source file:** `{src.as_posix()}`")
    lines.append("")
    lines.append("## Steps")
    lines.append("")
    lines.append("| # | Name | Intensity | Duration / Distance | Target | Repeat |")
    lines.append("|---|------|-----------|---------------------|--------|--------|")

    # Detect repeats. A step with duration_type == repeat_until_steps_cmplt sits AFTER
    # the loop body and points back to the first step of the loop via `duration_value`
    # (0-based message_index). `target_value` (== `repeat_steps`) is the iteration count.
    # We store: key = 1-based position of the repeat marker step, value = (loop_start_1b, count)
    repeat_info: dict[int, tuple[int, int]] = {}
    for idx, step in enumerate(steps, start=1):
        if first_value(step, "duration_type") == "repeat_until_steps_cmplt":
            frm = int(first_value(step, "duration_value") or 0) + 1  # 0-based → 1-based
            cnt = int(first_value(step, "target_value") or 1)
            repeat_info[idx] = (frm, cnt)

    notes_collected: list[str] = []
    row_num = 0
    skip_indices = set(idx for idx, step in enumerate(steps, start=1)
                       if first_value(step, "duration_type") == "repeat_until_steps_cmplt")
    # Map original 1-based step index → row number in the rendered table, filled as we go.
    row_for_orig: dict[int, int] = {}

    for idx, step in enumerate(steps, start=1):
        if idx in skip_indices:
            continue
        row_num += 1
        row_for_orig[idx] = row_num
        name = first_value(step, "wkt_step_name") or ""
        intensity = first_value(step, "intensity") or ""
        duration = render_workout_duration(step)
        target = render_workout_target(step, zones)

        # Is this step inside a repeat block? The loop body is [frm .. rep_pos-1].
        repeat_marker = "—"
        for rep_pos, (frm, cnt) in repeat_info.items():
            loop_end = rep_pos - 1
            if frm <= idx <= loop_end:
                if idx == loop_end:
                    if frm == loop_end:
                        repeat_marker = f"×{cnt}"
                    else:
                        frm_row = row_for_orig.get(frm, frm)
                        repeat_marker = f"×{cnt} (rows {frm_row}–{row_num})"
                else:
                    repeat_marker = f"(in ×{cnt})"
                break

        lines.append(
            f"| {row_num} | {name} | {intensity} | {duration} | {target} | {repeat_marker} |"
        )

        notes = first_value(step, "notes")
        if notes:
            notes_collected.append(f"- Step {row_num}: {notes}")

    if notes_collected:
        lines.append("")
        lines.append("## Notes")
        lines.append("")
        lines.extend(notes_collected)

    # Heuristic: figure out if targets are zone-based or absolute, and hint the user
    # how to get the other representation. Suppress hints when a zones.yaml is already
    # active for the side that's covered.
    target_kinds = {render_workout_target_kind(s) for s in steps}
    target_kinds.discard("open")
    target_kinds.discard("repeat")
    have_pace_zones = bool(zones.get("pace_zones"))
    have_hr_zones = bool(zones.get("hr_zones"))
    hints: list[str] = []
    if "zone" in target_kinds:
        hints.append(
            "This workout uses **zone-based** targets. To render absolute bpm / pace, "
            "configure your personal HR max and pace zones in a `zones.yaml` next to "
            "the .fit file and rerun."
        )
    if "absolute" in target_kinds and not (have_pace_zones or have_hr_zones):
        hints.append(
            "This workout uses **absolute** targets (raw bpm / pace / watts). To also tag "
            "each row with a zone (Z1–Z5), drop a sibling `zones.yaml` (see "
            "`references/zones.md`) and rerun."
        )
    if hints:
        lines.append("")
        lines.append("## Hints")
        lines.append("")
        for h in hints:
            lines.append(f"> {h}")

    lines.append("")
    return "\n".join(lines)


def render_workout_target_kind(step) -> str:
    """Return 'zone', 'absolute', 'open', or 'repeat' for a workout step."""
    dtype = first_value(step, "duration_type")
    if dtype == "repeat_until_steps_cmplt":
        return "repeat"
    ttype = first_value(step, "target_type")
    if ttype in (None, "open"):
        return "open"
    if ttype == "heart_rate":
        zone = first_value(step, "target_hr_zone")
        if zone and zone != 0:
            return "zone"
        return "absolute"
    # speed / power / cadence are always rendered as absolute ranges here
    return "absolute"


# ---------- activity (recorded) ----------


def render_activity_file(ff: FitFile, src: Path) -> str:
    session = next(ff.get_messages("session"), None)
    laps = list(ff.get_messages("lap"))

    if session is None:
        return f"# {src.stem}\n\n> No `session` message found — cannot summarise activity.\n"

    sport = first_value(session, "sport") or "running"
    sub_sport = first_value(session, "sub_sport") or ""
    start = first_value(session, "start_time")
    date_str = start.strftime("%Y-%m-%d") if start else src.stem

    total_time = first_value(session, "total_timer_time")
    total_dist = first_value(session, "total_distance")
    avg_speed = first_value(session, "avg_speed")
    avg_hr = first_value(session, "avg_heart_rate")
    max_hr = first_value(session, "max_heart_rate")
    ascent = first_value(session, "total_ascent")
    cals = first_value(session, "total_calories")

    lines: list[str] = []
    lines.append(f"# Run — {date_str}")
    lines.append("")
    sport_line = f"Running ({sub_sport})" if sub_sport and sub_sport != "generic" else "Running"
    lines.append(f"- **Sport:** {sport_line}")
    lines.append(f"- **Duration:** {fmt_duration(total_time)}")
    lines.append(f"- **Distance:** {fmt_distance_km(total_dist)}")
    lines.append(f"- **Avg pace:** {fmt_pace(avg_speed)}")
    if avg_hr or max_hr:
        lines.append(f"- **Avg / Max HR:** {avg_hr or '—'} / {max_hr or '—'} bpm")
    if ascent is not None:
        lines.append(f"- **Elevation gain:** {int(ascent)} m")
    if cals is not None:
        lines.append(f"- **Calories:** {int(cals)} kcal")
    lines.append(f"- **Source file:** `{src.as_posix()}`")
    lines.append("")

    if laps:
        lines.append("## Laps")
        lines.append("")
        lines.append("| # | Time | Distance | Avg pace | Avg HR | Max HR |")
        lines.append("|---|------|----------|----------|--------|--------|")
        for i, lap in enumerate(laps, start=1):
            lines.append(
                "| {n} | {t} | {d} | {p} | {ahr} | {mhr} |".format(
                    n=i,
                    t=fmt_duration(first_value(lap, "total_timer_time")),
                    d=fmt_distance_km(first_value(lap, "total_distance")),
                    p=fmt_pace(first_value(lap, "avg_speed")),
                    ahr=first_value(lap, "avg_heart_rate") or "—",
                    mhr=first_value(lap, "max_heart_rate") or "—",
                )
            )
        lines.append("")

        # Sanity: laps vs session total
        lap_sum = sum(
            (first_value(lap, "total_timer_time") or 0) for lap in laps
        )
        if total_time and abs(lap_sum - total_time) > 2:
            lines.append(
                f"> Note: lap sum differs from session total by {lap_sum - total_time:+.1f}s."
            )
            lines.append("")

    # Sparklines from record messages (one pass, then re-parsing not needed since we kept the FitFile object).
    hr_series: list[float] = []
    alt_series: list[float] = []
    for rec in ff.get_messages("record"):
        hr = first_value(rec, "heart_rate")
        alt = first_value(rec, "enhanced_altitude") or first_value(rec, "altitude")
        if hr is not None:
            hr_series.append(float(hr))
        if alt is not None:
            alt_series.append(float(alt))

    if hr_series or alt_series:
        lines.append("## Profile")
        lines.append("")
        if hr_series:
            lines.append(
                f"- **HR** {int(min(hr_series))}–{int(max(hr_series))} bpm  `{sparkline(hr_series)}`"
            )
        if alt_series:
            lines.append(
                f"- **Elev** {int(min(alt_series))}–{int(max(alt_series))} m  `{sparkline(alt_series)}`"
            )
        lines.append("")

    return "\n".join(lines)


# ---------- dispatch ----------


def detect_kind(ff: FitFile) -> str:
    fid = next(ff.get_messages("file_id"), None)
    return (fid and first_value(fid, "type")) or "unknown"


def convert(src: Path, dst: Path) -> None:
    ff = FitFile(str(src))
    ff.parse()
    kind = detect_kind(ff)

    if kind == "workout":
        md = render_workout_file(ff, src)
    elif kind == "activity":
        md = render_activity_file(ff, src)
    else:
        md = (
            f"# {src.stem}\n\n"
            f"> Unsupported .fit type: `{kind}`. This skill handles `workout` and `activity` files.\n"
        )

    dst.write_text(md, encoding="utf-8", newline="\n")
    sys.stdout.write(f"Wrote {dst}\n")
    sys.stdout.write("\n".join(md.splitlines()[:3]) + "\n…\n")


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        sys.stderr.write(__doc__ or "")
        return 2
    src = Path(argv[1]).expanduser().resolve()
    if not src.is_file():
        sys.stderr.write(f"Input not found: {src}\n")
        return 2
    dst = Path(argv[2]).expanduser().resolve() if len(argv) >= 3 else src.with_suffix(".md")
    convert(src, dst)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
