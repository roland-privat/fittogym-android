# `zones.yaml` reference

Optional sidecar that lets the `fit-to-workout-md` and `zwo-to-workout-md`
skills convert between **absolute** values (raw bpm, mm:ss /km) and **zone**
labels (Z1–Z5). All fields are optional; missing fields simply disable that
conversion direction.

## Discovery order

The renderer looks for `zones.yaml` in this order and uses the first match:

1. Same folder as the input `.fit` / `.zwo` file.
2. The workspace root (parent of the input folder).
3. The current working directory.

## Schema

```yaml
# Personal thresholds — only used when the input file doesn't carry them.
threshold_pace_sec_per_km: 330   # 5:30/km
max_hr: 185
threshold_hr: 165

# HR zones in raw bpm. Each value is [lo, hi] inclusive.
hr_zones:
  z1: [0, 144]
  z2: [145, 159]
  z3: [160, 169]
  z4: [170, 179]
  z5: [180, 220]

# Pace zones in seconds per km. lo = faster (smaller number), hi = slower.
# (Tip: a hard interval at 5:30/km = 330 s/km.)
pace_zones:
  z1: [410, 999]   # recovery (slower than ~6:50/km)
  z2: [355, 410]   # endurance
  z3: [335, 355]   # tempo
  z4: [315, 335]   # threshold
  z5: [0,   315]   # VO2 / anaerobic (faster than ~5:15/km)
```

## How the renderers use it

- **Absolute → Zone (annotation)**: when a workout step has a raw bpm or pace
  range, the renderer looks up the matching zone and appends `(Zn)` to the
  Target / Pace column. If the range straddles multiple zones, the **narrowest**
  matching zone wins.
- **Zone → Absolute** (future): the renderer would consult `hr_zones` /
  `pace_zones` to expand a zone-only target (e.g. `HR Zone 3`) into
  `HR 160–169 bpm (Z3)`. Currently the skills only emit the *hint* — the
  conversion itself is not yet implemented.

## YAML vs JSON

The renderers prefer `PyYAML` (`pip install pyyaml`) but fall back to a tiny
built-in parser that supports exactly the shape shown above (top-level scalars
and nested `key: [a, b]` maps). If neither works, the file is ignored with a
warning to stderr.
