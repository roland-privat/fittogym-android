"""Convert a Zwift .zwo running workout XML file to a Markdown description.

Usage:
    python zwo_to_md.py <input.zwo> [output.md]

Stdlib-only.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Iterable


# ---------- formatting ----------


def fmt_duration(seconds: float | None) -> str:
    if seconds is None:
        return "—"
    s = int(round(float(seconds)))
    h, rem = divmod(s, 3600)
    m, sec = divmod(rem, 60)
    return f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}"


def fmt_pace_from_power(threshold_sec_per_km: float | None, power: float | None) -> str:
    if not threshold_sec_per_km or not power or power <= 0:
        return "—"
    sec = threshold_sec_per_km / power
    m, s = divmod(int(round(sec)), 60)
    return f"{m}:{s:02d} /km"


def fmt_pct(power: float | None) -> str:
    if power is None:
        return "—"
    return f"{int(round(power * 100))} %"


def textevents(elem: ET.Element) -> str:
    parts: list[str] = []
    for te in elem.findall("textevent"):
        msg = te.get("message", "").strip()
        off = te.get("timeoffset", "0")
        try:
            off_i = int(float(off))
        except ValueError:
            off_i = 0
        if not msg:
            continue
        parts.append(f"{msg} ({off_i}s)" if off_i else msg)
    return "; ".join(parts)


# ---------- zones overlay (optional) ----------


def load_zones(zwo_path: Path) -> dict[str, Any]:
    """Look for a sibling `zones.yaml` (same folder, then workspace root) and load it.

    Uses PyYAML if available, falls back to a tiny inline parser sufficient for the
    documented schema. Returns {} if no file is found or parsing fails.
    """
    candidates = [
        zwo_path.with_name("zones.yaml"),
        zwo_path.parent.parent / "zones.yaml",
        Path.cwd() / "zones.yaml",
    ]
    for c in candidates:
        if c.is_file():
            try:
                return _parse_zones(c.read_text(encoding="utf-8"))
            except Exception as e:  # noqa: BLE001 — tolerant by design
                sys.stderr.write(f"WARN: ignoring malformed {c}: {e}\n")
                return {}
    return {}


def _parse_zones(text: str) -> dict[str, Any]:
    try:
        import yaml  # type: ignore

        return yaml.safe_load(text) or {}
    except ImportError:
        pass
    # Minimal fallback: supports `key: value`, `key:` then `  z1: [a, b]` indented lines.
    out: dict[str, Any] = {}
    cur_key: str | None = None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if not line.startswith((" ", "\t")):
            k, _, v = line.partition(":")
            k = k.strip()
            v = v.strip()
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


def zone_for_pace(pace_sec_per_km: float, zones: dict[str, Any]) -> str | None:
    pz = zones.get("pace_zones") if zones else None
    if not pz:
        return None
    best: tuple[float, str] | None = None  # (span, label)
    for name, rng in pz.items():
        if not isinstance(rng, (list, tuple)) or len(rng) != 2:
            continue
        lo, hi = float(rng[0]), float(rng[1])
        if lo > hi:  # tolerate either order
            lo, hi = hi, lo
        if lo <= pace_sec_per_km <= hi:
            span = hi - lo
            if best is None or span < best[0]:
                best = (span, str(name).upper())
    return best[1] if best else None


def append_zone(pace_str: str, threshold: float | None, power: float | None,
                zones: dict[str, Any]) -> str:
    if pace_str == "—" or not threshold or not power or not zones.get("pace_zones"):
        return pace_str
    sec = threshold / power
    z = zone_for_pace(sec, zones)
    return f"{pace_str} ({z})" if z else pace_str


# ---------- block rendering ----------


def render_block(idx: int, blk: ET.Element, threshold: float | None,
                 zones: dict[str, Any]) -> tuple[str, float]:
    """Render one workout block. Returns (markdown_row, work_seconds)."""
    tag = blk.tag
    notes = textevents(blk)

    def power(name: str) -> float | None:
        v = blk.get(name)
        return float(v) if v is not None else None

    def fnum(name: str) -> float | None:
        v = blk.get(name)
        return float(v) if v is not None else None

    if tag == "Warmup" or tag == "Cooldown":
        dur = fnum("Duration") or 0
        pl, ph = power("PowerLow"), power("PowerHigh")
        block_label = "Warm-up ramp" if tag == "Warmup" else "Cool-down ramp"
        power_col = f"{fmt_pct(pl)} → {fmt_pct(ph)}"
        pace_col = (
            f"{fmt_pace_from_power(threshold, pl)} → {fmt_pace_from_power(threshold, ph)}"
        )
        if zones.get("pace_zones") and threshold and pl and ph:
            pace_col = (
                f"{append_zone(fmt_pace_from_power(threshold, pl), threshold, pl, zones)}"
                f" → {append_zone(fmt_pace_from_power(threshold, ph), threshold, ph, zones)}"
            )
        return (
            f"| {idx} | {block_label} | {fmt_duration(dur)} | {power_col} | {pace_col} | {notes} |",
            dur,
        )

    if tag == "SteadyState":
        dur = fnum("Duration") or 0
        p = power("Power")
        pace = fmt_pace_from_power(threshold, p)
        pace = append_zone(pace, threshold, p, zones)
        return (
            f"| {idx} | Steady | {fmt_duration(dur)} | {fmt_pct(p)} | {pace} | {notes} |",
            dur,
        )

    if tag == "IntervalsT":
        repeat = int(float(blk.get("Repeat", "1")))
        on_d = fnum("OnDuration") or 0
        off_d = fnum("OffDuration") or 0
        on_p = power("OnPower")
        off_p = power("OffPower")
        total = repeat * (on_d + off_d)
        dur_col = (
            f"{fmt_duration(on_d)} + {fmt_duration(off_d)}"
            if off_d
            else fmt_duration(on_d)
        )
        power_col = (
            f"{fmt_pct(on_p)} / {fmt_pct(off_p)}" if off_d else fmt_pct(on_p)
        )
        on_pace = append_zone(fmt_pace_from_power(threshold, on_p), threshold, on_p, zones)
        off_pace = append_zone(fmt_pace_from_power(threshold, off_p), threshold, off_p, zones)
        pace_col = f"{on_pace} / {off_pace}" if off_d else on_pace
        block_label = f"Intervals ×{repeat}"
        return (
            f"| {idx} | {block_label} | {dur_col} | {power_col} | {pace_col} | {notes} |",
            total,
        )

    if tag == "FreeRide":
        dur = fnum("Duration") or 0
        n = notes or "Free ride (no target)"
        return (
            f"| {idx} | Free ride | {fmt_duration(dur)} | — | — | {n} |",
            dur,
        )

    # Unknown block — emit raw tag
    return (
        f"| {idx} | {tag} | — | — | — | (unrecognized block) |",
        0.0,
    )


# ---------- main ----------


def convert(src: Path, dst: Path) -> None:
    tree = ET.parse(str(src))
    root = tree.getroot()

    name = (root.findtext("name") or src.stem).strip()
    sport = (root.findtext("sportType") or "").strip() or "run"
    description = (root.findtext("description") or "").strip()
    threshold_txt = (root.findtext("thresholdSecPerKm") or "").strip()
    threshold = float(threshold_txt) if threshold_txt else None

    workout = root.find("workout")
    blocks = list(workout) if workout is not None else []

    zones = load_zones(src)

    lines: list[str] = []
    lines.append(f"# {name}")
    lines.append("")
    lines.append(f"- **Sport:** {sport}")
    if threshold:
        lines.append(
            f"- **Threshold pace:** {int(threshold)} s/km → "
            f"{fmt_pace_from_power(threshold, 1.0)}"
        )
    lines.append(f"- **Blocks:** {len(blocks)}")
    lines.append(f"- **Source file:** `{src.as_posix()}`")
    lines.append("")

    if sport != "run":
        lines.append(f"> Note: sportType is `{sport}`, not `run`. Pace conversion may be misleading.")
        lines.append("")

    if description:
        lines.append("## Description")
        lines.append("")
        for line in description.splitlines():
            lines.append(f"> {line}" if line.strip() else ">")
        lines.append("")

    if not blocks:
        lines.append("> No workout blocks found.")
    else:
        lines.append("## Steps")
        lines.append("")
        has_pace_col = bool(threshold)
        if has_pace_col:
            lines.append("| # | Block | Duration | Power | Pace | Notes |")
            lines.append("|---|-------|----------|-------|------|-------|")
        else:
            lines.append("| # | Block | Duration | Power | Notes |")
            lines.append("|---|-------|----------|-------|-------|")

        total_seconds = 0.0
        for i, blk in enumerate(blocks, start=1):
            row, work = render_block(i, blk, threshold, zones)
            if not has_pace_col:
                # Drop the Pace column (5th |...|) for files without threshold.
                row_parts = row.split(" | ")
                # row_parts: ['| 1', 'Block', 'Duration', 'Power', 'Pace', 'Notes |']
                if len(row_parts) >= 6:
                    row = " | ".join(row_parts[:4] + row_parts[5:])
            lines.append(row)
            total_seconds += work

        lines.append("")
        lines.append(f"**Total time:** {fmt_duration(total_seconds)}")

    # Hints
    hints: list[str] = []
    if not threshold:
        hints.append(
            "No `thresholdSecPerKm` in the file. Add a sibling `zones.yaml` with "
            "`threshold_pace_sec_per_km:` to render absolute pace."
        )
    if threshold and not zones.get("pace_zones"):
        hints.append(
            "This workout uses **absolute** paces (computed from threshold). To also tag each "
            "row with a zone (Z1–Z5), drop a sibling `zones.yaml` with `pace_zones:` and rerun."
        )
    if hints:
        lines.append("")
        lines.append("## Hints")
        lines.append("")
        for h in hints:
            lines.append(f"> {h}")

    lines.append("")
    dst.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    sys.stdout.write(f"Wrote {dst}\n")
    sys.stdout.write("\n".join("\n".join(lines).splitlines()[:3]) + "\n…\n")


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
