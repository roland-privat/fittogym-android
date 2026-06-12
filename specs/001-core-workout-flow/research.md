# Phase 0 Research: Core Workout Flow

**Date**: 2026-05-25 · **Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

This document resolves the technology and design unknowns implied by the
spec + constitution. Each section follows: **Decision · Rationale · Alternatives
considered**.

## 1. Toolchain & SDK pinning

**Decision**: Kotlin **2.0.x**, AGP **8.7.x** (or latest stable available at
first `./gradlew --version`), Compose BOM **latest stable** (e.g.
`2025.x.00`), JDK **17** toolchain, **minSdk = 31** (Android 12),
**targetSdk = compileSdk = 35** (Android 15), Gradle wrapper **8.10+**.
Pin all of these in `gradle/libs.versions.toml` at first build and **do not
upgrade casually**.

**Rationale**:

- minSdk 31 is the lowest level that exposes the new `BLUETOOTH_SCAN` /
  `BLUETOOTH_CONNECT` runtime permissions, letting us drop the legacy
  `ACCESS_FINE_LOCATION` requirement entirely (Spec FR-025, Constitution
  §"Permissions").
- Kotlin 2.0 + Compose Compiler plugin (now part of the Kotlin distribution)
  removes the AGP/Kotlin/Compose-Compiler version-matching headache that bit
  AGP 8.4 and earlier.
- targetSdk 35 satisfies the Play "latest API" rule even though we don't ship
  to Play — staying current keeps the Android 14+ foreground-service-type
  rules from being a surprise later (§4 below).

**Alternatives considered**:

- minSdk 26 (Android 8) — would require legacy `ACCESS_FINE_LOCATION` *just*
  for BLE scanning. Rejected: violates Spec FR-025 spirit and adds a scary
  permission prompt for no benefit.
- targetSdk 33 — older but still common. Rejected: misses
  `FOREGROUND_SERVICE_TYPE_HEALTH` semantics introduced in Android 14 which
  we want to use for the workout service.
- Kotlin 1.9 — works fine but locks us to old Compose Compiler version pairs.
  Rejected for the same reason as above.

## 2. FIT decoder

**Decision**: Add the **official Garmin FIT Java SDK** as a Maven Central
dependency:

```kotlin
// gradle/libs.versions.toml
[versions]
fit = "21.205.0"

[libraries]
fit = { module = "com.garmin:fit", version.ref = "fit" }

// app/build.gradle.kts
dependencies {
    implementation(libs.fit)
}
```

Wrap the SDK behind a thin `FitDecoder` Kotlin object that exposes only the
small surface the app needs:

```kotlin
data class DecodedWorkout(
    val name: String?,
    val sport: Sport,
    val steps: List<DecodedStep>,
    val repeats: List<DecodedRepeat>,
)

object FitDecoder {
    sealed class Result {
        data class Ok(val workout: DecodedWorkout) : Result()
        data class Rejected(val reason: RejectReason) : Result()
    }
    fun decode(bytes: ByteArray): Result
}
```

**Rationale**:

- The FIT format is binary, has versioned message/field definitions, and
  encodes durations + targets across `workout_step`, `repeat_step`, and field
  enums in ways that are tedious to replicate. A hand-rolled parser would be
  hundreds of lines of code and a correctness risk — Constitution §III
  explicitly allows a library when "the alternative is demonstrably more
  code".
- Garmin publishes the official SDK to **Maven Central** as
  `com.garmin:fit` (see [garmin/fit-java-sdk](https://github.com/garmin/fit-java-sdk)).
  No vendored JAR, no manual download, no license click-through — it's just a
  Gradle dependency line, fully reproducible per Constitution §IV.
- Wrapping the SDK behind `FitDecoder` keeps the surface area small enough
  that swapping it later (e.g. for a Kotlin-native parser if one matures) is
  a single-file change.
- License (Garmin FIT SDK License, bundled in the artifact) is permissive
  for apps that interoperate with FIT files and does not require attribution
  UI on consumer apps.

**Alternatives considered**:

- Hand-roll a minimal FIT parser. Rejected: ~10× the code of the rest of the
  decode layer, and any field-definition mismatch corrupts workouts silently.
  (Considered again after a Maven Central confusion; rejected once Maven
  Central availability was confirmed.)
- Vendor the JAR under `app/libs/`. Rejected once we discovered Garmin
  publishes to Maven Central: vendoring is strictly worse (download friction,
  manual SHA tracking, no automatic version updates from the version
  catalog).
- `garmin-fit-rs` via JNI. Rejected: adds a native build step and ABI matrix
  — pure overkill for a personal app.

## 3. BLE Heart Rate Service client

**Decision**: Use the **Android platform BLE API directly**
(`BluetoothManager`, `BluetoothLeScanner`, `BluetoothGatt`). Encapsulate it
in a small `HrmClient` Kotlin class exposing a `StateFlow<HrSample?>` and
`connect(deviceId) / disconnect()`. Implement only:

- Scanning with a `ScanFilter` for the Heart Rate Service UUID
  `0000180D-0000-1000-8000-00805F9B34FB`.
- Connecting + enabling notifications on the Heart Rate Measurement
  characteristic `0x2A37` and decoding flags byte 0 (UINT8 vs UINT16 BPM,
  energy expenditure / RR-intervals ignored).
- Reconnect-on-disconnect-while-active with exponential backoff capped at
  30 s.

**Rationale**:

- HRS is a tiny, stable, well-documented GATT profile — one notifiable
  characteristic, ≤ 20 bytes per sample. Pulling in a library to read it
  would violate Constitution §III.
- Permissions: declare `BLUETOOTH_SCAN` (`usesPermissionFlags="neverForLocation"`)
  and `BLUETOOTH_CONNECT` only. No `ACCESS_FINE_LOCATION` — `neverForLocation`
  is honored from API 31.
- **Signal-loss threshold**: if no Heart Rate Measurement notification arrives
  for **3 consecutive seconds**, treat the link as lost — emit `hrSample = null`
  and let the UI render `— (signal lost)`. The threshold is short enough to be
  visible to the user when an HRM strap loses contact, long enough to absorb
  the natural ~1 s notification cadence of a healthy HRS link. Referenced from
  Spec FR-026 (“tolerate transient signal loss”) and from tasks.md T077.

**Alternatives considered**:

- Nordic BLE Library — feature-rich, well-tested, but adds dependency weight
  and an opinionated callback model. Rejected: HRS is too simple to justify.
- Kable (Kotlin Multiplatform BLE). Rejected: we are Android-only and don't
  benefit from KMP abstractions.

## 4. Foreground service & keep-running behavior

**Decision**: Implement a single `WorkoutForegroundService` (subclass of
`androidx.lifecycle.LifecycleService`) that:

1. Is started in `START_STICKY` mode by the workout-run screen when the user
   presses **Start**.
2. Calls `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`
   on Android 14+ (API 34+); on API 31–33 it omits the type argument.
3. Owns the **run-session state machine**, the BLE `HrmClient`, the
   countdown-beep `TonePlayer`, and the overlay `MiniViewController`.
4. Posts an ongoing notification showing the current step name + time
   remaining; tapping it deep-links into the workout run screen.
5. Stops itself on Stop, on natural completion (after the completion card is
   dismissed), or if the spec FR-033 graceful-degradation says so.

Declare in the manifest:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<service
    android:name=".service.WorkoutForegroundService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="personal_workout_timer"/>
</service>
```

**Rationale**:

- The spec demands counters keep advancing when the screen turns off and the
  app is backgrounded (Edge Case "Phone screen turns off", US4). Without a
  foreground service Android will paus / kill background work, and HR
  notifications would stop arriving on Android 12+ when the app is not
  visible.
- `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is the right Android-14+ category for
  *us*: a personal workout *timer* whose primary job is to keep counters
  ticking and beeps firing while the app is backgrounded. The `health` type
  was the original choice but it requires a sensor runtime permission
  (`ACTIVITY_RECOGNITION` / `BODY_SENSORS` / `HIGH_SAMPLING_RATE_SENSORS`)
  even when no body sensors are actually used; we don't read body sensors in
  v1 so triggering a dangerous-permission dialog just to satisfy the FGS
  validator would be honest theater. Play Store scrutiny on `specialUse` is
  irrelevant per Constitution §IV (no Play distribution).

**Alternatives considered**:

- `health` — the semantically perfect type, rejected because it requires an
  unrelated sensor permission that we don't otherwise need. Re-evaluate if a
  future feature (e.g., footpod for stride cadence) brings a sensor
  permission organically.
- `connectedDevice` — reasonable once US3 lands (we'll have
  `BLUETOOTH_CONNECT` runtime grant). Defer: it would couple the FGS
  lifecycle to BLE state instead of to the workout, which is the wrong
  granularity (the run page works even with no HRM).
- WorkManager — wrong tool: WorkManager schedules deferrable batched work,
  not real-time 1-Hz updates.
- AlarmManager + WakeLock + no service — possible on older Androids, but
  fights the Android 14+ background-execution rules. Rejected.

## 5. Audio cues (5-beep step-end countdown)

**Decision**: Use `android.media.ToneGenerator(AudioManager.STREAM_MUSIC,
volume)` and play `TONE_PROP_BEEP` (a short ~150 ms beep). For each beep,
request **transient audio focus** via
`AudioManager.requestAudioFocus(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)` for the
duration of the tone and `abandonAudioFocus()` immediately after.

Scheduling: the run-session state machine fires a "5s-left" tick at
`stepStartMonotonicMs + stepDurationMs - 5000` and then schedules four more
beeps at 1-second intervals. The scheduler uses
`Handler(Looper.getMainLooper())` for v1 and switches to `ScheduledExecutor`
only if drift measurements (Spec SC-004) require it.

**Rationale**:

- `ToneGenerator` is platform, has no dependencies, plays through `STREAM_MUSIC`
  so it ignores ringer mute and respects media volume — matches FR-023b/c.
- Transient-may-duck audio focus interleaves cleanly with music apps (Spec
  FR-023b): the user's music dips briefly per beep rather than being stopped.

**Alternatives considered**:

- `SoundPool` with a packaged WAV — more flexible (custom tones) but adds an
  asset and a load step. Rejected: `ToneGenerator` is good enough; can
  upgrade later.
- `MediaPlayer` — overkill for 150 ms beeps; higher latency and resource
  cost.

## 6. Mini view (always-on-top overlay)

**Decision**: A `WindowManager`-managed window of type
`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` that hosts a Compose
content owner via `ComposeView` + `ViewTreeLifecycleOwner` + a tiny
`ViewModelStoreOwner`. The overlay is created/destroyed by
`WorkoutForegroundService` and updated by collecting the same `StateFlow`
the workout-run screen subscribes to.

Permission flow:

1. On toggle-on, check `Settings.canDrawOverlays(context)`.
2. If false, route the user via
   `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))`
   with a short pre-permission rationale screen (FR-029).
3. On return (`onResume`), re-check and only then attach the overlay.

Manifest:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
```

**Rationale**:

- `TYPE_APPLICATION_OVERLAY` is the only type still permitted from API 26+
  for third-party apps. `SYSTEM_ALERT_WINDOW` is a runtime permission since
  API 23 and is requested via the settings-page route, not the standard
  permission dialog — this is by design.
- Hosting Compose inside the overlay (rather than a plain `View`) lets us
  reuse the same composables that render `MiniMetrics` on the workout-run
  screen.

**Alternatives considered**:

- Picture-in-Picture (PiP) — looks great but only activates when the user
  leaves the app via Home (not when they switch via Recents) and cannot be
  user-positioned freely. Rejected: doesn't meet "stays visible on top of
  the underlying app" in all switch scenarios.
- A regular `Toast`/`Snackbar` — wrong primitive; cannot persist.

## 7. Persistence

**Decision**: **Room** for structured data, **file blobs** in `filesDir/workouts/`
for raw `.fit` bytes, **DataStore Preferences** for App Settings.

Schema (see [data-model.md](data-model.md) for full details):

- `workout` (1 row per Workout)
- `workout_step` (n rows per Workout, ordered, with optional repeat-group id)
- `repeat_group` (0..n rows per Workout)
- `hr_monitor_pairing` (0..1 row)

Migrations: Room with explicit `Migration` objects per schema bump.
For v1.0 we just `fallbackToDestructiveMigrationOnDowngrade()` and ship
schema v1 in `app/schemas/`. The user's persistent-data rule (always plan a
data-preserving path) applies once we ship a second migration; v1 has none.

**Rationale**:

- One table per workout would suffice for ~100 workouts, but steps + repeat
  groups need a join model; doing that in flat JSON forces a re-parse on
  every screen open. Room's generated DAOs cut the per-query boilerplate.
- The raw `.fit` blob is intentionally kept on disk separately so the DB
  doesn't bloat with binary BLOBs and so the SHA-256 dedup check can be done
  before Room sees the bytes.

**Alternatives considered**:

- Flat JSON files per workout — simpler at first, but joins across
  steps + repeat_groups in JSON get awkward and any "list by date imported"
  query has to read every file. Rejected at this scale.
- SQLDelight — fine library but Room is the Android default and we already
  pay the KSP processor cost via Compose tooling.

## 8. TSS (Training Stress Score) formula

**Decision**: Use the standard **rTSS** (Running TSS) formula applied to the
*planned* workout:

For each step `i` that has both a usable duration (in seconds) and a usable
target-pace midpoint (in sec/km):

```
IF_i  = threshold_pace_sec_per_km / step_pace_midpoint_sec_per_km
        // (faster pace → smaller sec/km → larger IF)
tss_i = (seconds_i * IF_i * IF_i) / 36
        // i.e. (seconds_i * IF_i^2 / 3600) * 100, simplified
```

Open-duration steps and distance-based steps with no pace target contribute
**0**. Distance-based steps with a pace target contribute via their effective
duration `distance / pace_midpoint` per Spec FR-004a, then by the formula
above.

Total `TSS = round(sum(tss_i))`. If `threshold_pace` is unset in App
Settings, return `null` and let the UI render `—` (Spec edge case).

**Rationale**:

- This is the canonical Joe Friel / TrainingPeaks "rTSS by planned NGP"
  approximation. NGP is replaced by the planned pace midpoint because the
  app is not doing realized pace at v1 (no GPS, no realized data — Spec
  Assumptions). Result is deterministic given the same `(steps, threshold)`
  pair, satisfying Spec Assumption "TSS definition".
- The formula has the property `IF = 1.0 → 100 TSS / hour`, which matches
  the well-known rule of thumb and gives the user a recognizable number.

**Alternatives considered**:

- Garmin / HRSS (HR-based stress) at runtime — would require recording HR
  per second, which contradicts "no activity recording" (Spec Assumptions).
  Rejected.
- IF computed as `step_pace_midpoint / threshold_pace` (i.e., the inverse) —
  that is the *cycling* TSS convention applied to power; for pace, the
  faster-is-harder direction is the inverse. Rejecting the inverse avoids
  shipping a TSS that *decreases* with harder workouts.

## 9. Build & deploy from VS Code (no Android Studio)

**Decision**: The canonical edit-build-deploy loop is:

1. Install **JDK 17** (Microsoft Build of OpenJDK or Temurin) and set
   `JAVA_HOME`.
2. Install the **Android SDK command-line tools** (no Android Studio). Set
   `ANDROID_HOME` (Windows: `%LOCALAPPDATA%\Android\Sdk`). Use
   `sdkmanager` to install `platforms;android-35`, `build-tools;35.0.0`, and
   `platform-tools` (for `adb`).
3. Open the workspace in VS Code. Recommended (not required) extensions:
   - **Kotlin Language** (`fwcd.kotlin`) — semantic highlights / completion
     via a community Kotlin LS.
   - **Gradle for Java** (`vscjava.vscode-gradle`) — Gradle task runner with
     a tree view.
   - **XML** (`redhat.vscode-xml`) — for manifest editing.
   - The big "Extension Pack for Java" is **not** needed.
4. In the integrated terminal: `./gradlew installDebug`. The Gradle wrapper
   self-bootstraps. With the phone in USB-debugging mode and the developer's
   USB cable connected, this builds the debug APK and `adb install`s it.
5. Run/relaunch on the device: `adb shell am start -n
   com.example.runtraining/.MainActivity`.
6. Live logs while running: `adb logcat -v color RunTraining:V *:S` (using a
   single shared log tag `RunTraining`).

We do **not** depend on the Android Studio "Run" action, on AGP's
`installDebug` IDE integration, or on any Android Studio-only feature
(Layout Inspector, Profiler). Compose previews work only via
`@Preview` + Kotlin LS hover; the developer accepts the slightly slower
iteration loop in exchange for staying inside VS Code.

**Rationale**:

- The user explicitly stated VS Code is the dev surface. Constitution §IV
  already mandates the build succeed via the Gradle wrapper from a clean
  clone with only `ANDROID_HOME` + JDK set; that exactly fits a VS Code-only
  setup.
- All Android tooling that matters at the command line (`adb`,
  `sdkmanager`, `gradlew`) is independent of any IDE.

**Alternatives considered**:

- Require Android Studio for at least the first build to generate IDE
  metadata. Rejected: contradicts the user's stated preference and adds a
  ~10 GB install requirement.
- Use the (Microsoft-maintained) **"Android" / "Android Tools" VS Code
  extension** for an in-IDE deploy button. Considered, but extensions in
  this space are immature and version-fragile. Keeping the canonical path on
  `./gradlew installDebug` is robust.

## 10. Timer & drift management

**Decision**: The run-session FSM maintains `stepStartMonotonicMs` (from
`SystemClock.elapsedRealtime()`) for the current step and computes
`stepElapsedMs = now - stepStartMonotonicMs - pausedAccumulatedMs`. UI
collects a `StateFlow<RunUiState>` that emits at 4 Hz (every 250 ms) for
smooth playhead animation; persisted state updates only on transitions and
Pause/Resume.

**Rationale**:

- `elapsedRealtime()` is immune to system clock changes, NTP corrections, and
  daylight-savings discontinuities (unlike `System.currentTimeMillis()`).
- 4 Hz UI tick keeps the playhead visibly smooth without burning battery; the
  human-visible target of "at least once per second" (Spec FR-020, SC-004) is
  easily met.
- Step transitions are scheduled at monotonic-ms targets rather than
  computed cumulatively, so drift cannot accumulate across hundreds of
  ticks.

**Alternatives considered**:

- `CountDownTimer` — coarse, fires "approximately", well-known to drift
  hundreds of ms over a 5-minute step. Rejected for the 5-beep precision
  requirement.

## 11. UI rendering of the timeline chart

**Decision**: A custom Compose `Canvas` composable
`TimelineChart(workout, playheadFraction, modifier)` that draws one
rectangle per *expanded* step (i.e., repeat blocks are unrolled for the
visual), widths proportional to the step's effective-duration. Intensity
class maps to a fixed color set inside the Material 3 palette; pace zone
maps to a thin top stripe. The playhead is a 2 dp vertical line in the
primary color.

**Rationale**:

- Compose `Canvas` (Skia) handles 60+ rectangles trivially. No need for a
  charting library — the visual is essentially `n` filled rectangles plus a
  playhead. Constitution §III dependency-budget argument.

**Alternatives considered**:

- MPAndroidChart — heavy, requires a `ChartView` interop, way overpowered.
  Rejected.
- Vico (Compose-native chart lib) — small but still a dependency for what is
  effectively `drawRect` × n. Rejected.

## 12. Threshold pace UI

**Decision**: One `OutlinedTextField` in **Options** captioned
"Threshold pace (`mm:ss`/km)" with input mask `m:ss` or `mm:ss`; parsed to
`Int` sec/km and stored in DataStore as `threshold_pace_sec_per_km`. An
"Unset" affordance writes `null`. Inline validation: 3:00–10:00 /km
acceptable; outside this range shows a "Are you sure?" confirmation but
still saves on confirm — we don't pretend to know all users.

**Rationale**:

- One value drives Spec FR-004 (TSS for new and existing workouts on
  re-open) and is the canonical input. Storing it in DataStore Preferences
  (not Room) keeps it trivially mutable from any screen via a
  `SettingsRepository` singleton.

**Alternatives considered**:

- Triple sliders (zones) — would let the user mirror their TrainingPeaks
  zones. Out of scope for v1 (TSS only needs one threshold).
