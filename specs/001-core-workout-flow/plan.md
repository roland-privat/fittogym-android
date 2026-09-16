# Implementation Plan: Core Workout Flow

**Branch**: `001-core-workout-flow` | **Date**: 2026-05-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-core-workout-flow/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Build a single-module Android app, **Kotlin + Jetpack Compose**, that (a) imports planned-running workouts from `.fit` files received via Android share / open intents, (b) runs them with a graphical timeline, live step counters, 5-beep step-end countdown, BLE heart-rate display, and an always-on-top mini view, and (c) stays fully offline. Targets are pace ranges (display-toggle pace ⇄ speed in Options). Distance-based steps are converted to effective-duration at import. Distribution is **`adb install` over USB only** — built and deployed from the developer's VS Code integrated terminal via `./gradlew installDebug`.

Technical approach pillars:

- **One Gradle module `app/`**, no DI framework, no multi-module split. Compose UI + a small Kotlin "workout engine" service that owns the run-session state machine.
- **Garmin FIT SDK** (`com.garmin:fit` from Maven Central) for `.fit` decoding — the FIT format is non-trivial enough that a hand-rolled parser would violate Constitution §III in the other direction (large code volume + correctness risk).
- **Platform-native BLE** via `BluetoothLeScanner` + `BluetoothGatt` for the Heart Rate Service (`0x180D`) — no Nordic/Kable library.
- **Foreground service** of type `health` to keep the run-session alive while the screen is off or the app is backgrounded (mandatory on Android 14+ for long-running workout tracking).
- **Persistence**: Room database for workout metadata (one row per workout, child rows for steps) + raw `.fit` blobs kept as files in app-private storage keyed by content hash. App Settings in a tiny `DataStore` preferences file.
- **Audio cues**: `ToneGenerator` on the `STREAM_MUSIC` stream with transient audio focus per beep (Spec FR-023b/c).
- **Mini view**: a small `WindowManager` overlay window of type `TYPE_APPLICATION_OVERLAY`, driven by the same foreground service.
- **Build/deploy**: standard Gradle wrapper, version catalog at `gradle/libs.versions.toml`, debug keystore only. From VS Code, the developer runs `./gradlew installDebug` in the integrated terminal with an Android device in USB-debugging mode; no IDE-specific deploy magic is required.

## Technical Context

> **Superseded values (2026-09-16):** the app now builds on **AGP 8.13.2 / Gradle 8.13**, **targetSdk/compileSdk 36**, and the foreground service is type **`specialUse`** (subtype `personal_workout_timer`), not `health`. A signed **`release` variant + Google Play (closed testing)** is in use, permitted by constitution v2.0.0 (§IV / Distribution). The original values below are kept for historical context.

**Language/Version**: Kotlin 2.0.x (stable for AGP 8.7+); Java toolchain 17 (required by AGP 8.x).

**Primary Dependencies**:

- Android Gradle Plugin (AGP) 8.7.x+ (verify latest at first build via the version catalog)
- Jetpack Compose via Compose BOM (latest stable; expected 2025.x at build time) — `compose-ui`, `compose-material3`, `compose-foundation`, `compose-ui-tooling-preview` (debug)
- `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `lifecycle-service`
- `androidx.navigation:navigation-compose`
- `androidx.room:room-runtime` + `room-ktx` + `room-compiler` (KSP)
- `androidx.datastore:datastore-preferences` (for App Settings only)
- `com.garmin:fit` — **Garmin FIT SDK Java, from Maven Central** (latest at the time of this plan: `21.205.0`; license: Garmin FIT SDK License, permissive for FIT-compatible apps)
- Platform-only for BLE (no library), audio (`android.media.ToneGenerator`, `AudioManager`), overlay (`WindowManager`), notifications (`androidx.core.app.NotificationCompat`)

**Storage**:

- `Room` SQLite database (app-private) → workout metadata + steps + repeat-groups + HR pairing
- Files in `context.filesDir/workouts/<sha256>.fit` → raw imported `.fit` blobs
- `DataStore` preferences (`app-settings.preferences_pb`) → threshold pace, display unit, last-paired-HRM device id (cached for fast cold start)

**Testing**:

- **Canonical**: manual on-device manual-test recipes in `quickstart.md` (per Constitution §V). Three recipes: HRM connect, FIT share/open, immersive fullscreen + mini view + beeps.
- **Optional**: JVM unit tests in `app/src/test/` covering pure logic only — FIT-decode → `Workout` mapping, distance-based-step duration derivation, TSS computation, run-session state machine transitions. JUnit 4 + `kotlin-test`. No instrumented tests in v1.

**Target Platform**:

- **Min SDK 31** (Android 12) — needed for the modern `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` runtime permissions with `usesPermissionFlags="neverForLocation"`, avoiding the legacy `ACCESS_FINE_LOCATION` requirement (Constitution §"Permissions" / Spec FR-025).
- **Target SDK 35** (Android 15) at the time of this plan — pin to latest stable in `gradle/libs.versions.toml` at first build; verify Android 16 if released.
- **Compile SDK** = target SDK.

**Project Type**: Single Android Gradle module mobile app (no API backend, no second module).

**Performance Goals**:

- Counter / playhead updates at 1 Hz with drift ≤ 1 s per 30 min (Spec SC-004).
- 5-beep countdown last beep within ±100 ms of step transition (Spec FR-023a).
- Cold-start → training selection page < 1 s on a modern phone.
- Tap-workout → timeline rendered < 1 s (Spec SC-003).
- Screen-on sustained for ≥ 60 min during an active run (Spec SC-005).

**Constraints**:

- Offline-only. Manifest MUST NOT declare `INTERNET` (Spec FR-034).
- A signed `release` build type + Google Play (closed testing) is now used, permitted by constitution v2.0.0 (§IV / Distribution): signing material lives in a gitignored `keystore.properties`, the release variant falls back to the debug keystore when it's absent, and R8 + resource-shrinking rules are committed. The clean-clone `gradlew installDebug` path still needs no secrets.
- No GPS, no telephony, no microphone, no camera. Only Bluetooth + overlay + audio playback + foreground-service permissions.
- VS Code terminal is the canonical build/deploy surface; the project MUST work without Android Studio installed.

**Scale/Scope**:

- One user, one phone (Constitution §I).
- ~6 screens: Training Selection, Training Details (import landing & edit), Workout Run, Mini View (overlay), Options, Workout Complete card.
- Expected workout library size: tens to low hundreds, each `.fit` blob < 50 KB.
- Up to ~60 authored steps per workout in pathological cases (typical: 5–10).

## Constitution Check

*GATE: Initial check before Phase 0. Re-checked after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Personal Single-User Scope | **Pass** | Zero account, auth, multi-profile, distribution-channel, or telemetry surface. App-private storage, single device. |
| II | Local-First & Privacy by Default | **Pass** | No `INTERNET` permission declared. All FIT decoding, HR processing, TSS math is on-device. No Firebase/Crashlytics/Analytics. |
| III | Simplicity & YAGNI | **Pass (with one justified library)** | One Gradle module, no DI framework, no repository/use-case layers. Three third-party libraries: (a) Compose BOM (single artifact dragging UI deps, unavoidable for the UI choice), (b) Room (replaces a hand-rolled JSON-file persistence layer with materially less code once steps + repeat-groups + indices are involved), (c) Garmin FIT SDK via Maven Central `com.garmin:fit` (a hand-rolled FIT parser would be substantially more code than the rest of the app combined; see [research.md](research.md) §FIT parser). No other third-party libs. |
| IV | Reproducible Local Build & USB Deploy | **Pass** | `./gradlew installDebug` is the clean-clone deploy path (no secrets). A signed `release` variant + Play (closed testing) is also used per constitution v2.0.0; keystore is gitignored, release falls back to the debug keystore when absent. Gradle wrapper, `libs.versions.toml`, pinned SDK levels checked in. |
| V | Hardware Integration Honesty | **Pass** | Manual-test recipes for HRM, FIT share/open, immersive fullscreen + mini view + beeps are mandated by [quickstart.md](quickstart.md) (Phase 1 output). Graceful-degradation FRs (FR-033, US3 ASs 4–6, US4 ASs 6) are codified in the spec and traced into the run-session state machine. |

No violations require a Complexity Tracking entry.

## Project Structure

### Documentation (this feature)

```text
specs/001-core-workout-flow/
├── plan.md                          # This file
├── spec.md                          # Feature spec (already exists)
├── research.md                      # Phase 0 output
├── data-model.md                    # Phase 1 output
├── contracts/
│   ├── intent-filters.md            # External contract: Android intents accepted
│   └── run-session-state-machine.md # Internal contract: workout-engine FSM
├── quickstart.md                    # Phase 1 output: build/deploy + manual test recipes
├── checklists/
│   └── requirements.md              # From /speckit.specify (already exists)
└── tasks.md                         # Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
.
├── build.gradle.kts                 # Root build; AGP + Kotlin plugin pins
├── settings.gradle.kts              # Includes :app only
├── gradle.properties                # JVM args + AndroidX flags
├── gradle/
│   ├── wrapper/                     # Gradle wrapper (committed)
│   └── libs.versions.toml           # Version catalog: AGP, Kotlin, Compose BOM, Room, etc.
├── gradlew / gradlew.bat            # Wrapper scripts
├── local.properties                 # NOT committed — generated by `sdkmanager` / user; sets sdk.dir
└── app/
    ├── build.gradle.kts             # Android library plugin, minSdk=31, targetSdk=35, namespace
    ├── proguard-rules.pro           # Empty in v1 (no release variant)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # Permissions + intent filters + FileProvider + Service
        │   ├── kotlin/
        │   │   └── com/example/runtraining/
        │   │       ├── MainActivity.kt              # Single Activity, hosts NavHost
        │   │       ├── nav/                         # Compose NavHost + routes
        │   │       ├── ui/
        │   │       │   ├── selection/               # Training Selection screen
        │   │       │   ├── details/                 # Training Details screen
        │   │       │   ├── run/                     # Workout Run screen (timeline, metrics, controls)
        │   │       │   ├── complete/                # Workout Complete summary card
        │   │       │   ├── options/                 # Options screen (HRM + threshold + display unit)
        │   │       │   └── theme/                   # Material 3 theme
        │   │       ├── workout/
        │   │       │   ├── model/                   # Workout / Step / RepeatGroup data classes
        │   │       │   ├── fit/                     # FitDecoder wrapping the Garmin SDK
        │   │       │   ├── engine/                  # RunSession FSM + Ticker
        │   │       │   └── tss/                     # TSS calculator
        │   │       ├── persistence/
        │   │       │   ├── db/                      # Room database + DAOs + entities
        │   │       │   └── files/                   # .fit blob store keyed by sha256
        │   │       ├── ble/                         # HRM scanner + GATT client
        │   │       ├── audio/                       # Countdown-beep player (ToneGenerator + focus)
        │   │       ├── overlay/                     # Mini view WindowManager overlay
        │   │       ├── service/                     # WorkoutForegroundService
        │   │       └── settings/                    # DataStore App Settings repository
        │   └── res/
        │       ├── values/                          # strings, themes (Material 3, dark + light)
        │       ├── drawable/                        # icons (vector)
        │       └── xml/
        │           └── file_paths.xml               # FileProvider paths
        └── test/
            └── kotlin/com/example/runtraining/
                ├── workout/fit/FitDecoderTest.kt
                ├── workout/tss/TssCalculatorTest.kt
                ├── workout/engine/RunSessionTest.kt
                └── workout/model/DistanceStepDerivationTest.kt
```

**Structure Decision**: Single Android Gradle module (`:app`) per Constitution §III. Package structure is sliced by *concern* (workout / persistence / ble / audio / overlay / service / ui), not by Clean-Architecture layers — there are no second consumers to justify a more elaborate layering. The single `MainActivity` hosts a Compose `NavHost`; a separate `WorkoutForegroundService` owns the long-running run session and BLE/audio/overlay resources.

## Complexity Tracking

> No Constitution Check violations to justify. Section left intentionally empty.

## Post-Design Constitution Re-Check (Phase 1)

Re-evaluated after writing [research.md](research.md), [data-model.md](data-model.md), [contracts/intent-filters.md](contracts/intent-filters.md), [contracts/run-session-state-machine.md](contracts/run-session-state-machine.md), and [quickstart.md](quickstart.md). No new violations surfaced.

| # | Principle | Status | Confirmation |
|---|-----------|--------|--------------|
| I | Personal Single-User Scope | **Pass** | The intent-filter contract accepts files from a single device; no second-user surfaces (account, share-out, distribution channel) appear anywhere in the design. |
| II | Local-First & Privacy by Default | **Pass** | Intent contract explicitly does **not** declare `INTERNET`. No outbound network calls anywhere in the design. The Garmin FIT SDK is a local-only decoder. HR processing and TSS math are pure on-device functions. |
| III | Simplicity & YAGNI | **Pass** | Library list is closed at three (Compose BOM, Room, Garmin FIT JAR) — each justified in [research.md](research.md). No DI framework, no repository layer, no Clean Architecture split. The run-session FSM is one class behind one `StateFlow<RunUiState>`. |
| IV | Reproducible Local Build & USB Deploy | **Pass** | [quickstart.md](quickstart.md) §1–2 lay out a Studio-free `./gradlew installDebug` flow that needs only JDK 17 + Android cmdline-tools + `ANDROID_HOME`. A signed `release` variant + Play upload (`bundleRelease`) is additionally used per constitution v2.0.0; secrets stay in a gitignored `keystore.properties`. |
| V | Hardware Integration Honesty | **Pass** | [quickstart.md](quickstart.md) §3 provides four manual-test recipes (FIT share/open, BLE HRM, immersive + beeps + mini view, TSS & threshold pace) covering every Spec graceful-degradation FR. The run-session state machine ([contracts/run-session-state-machine.md](contracts/run-session-state-machine.md)) makes each failure mode an explicit non-crashing transition. |

Phase 2 planning (task generation) is **not** done here — it belongs to `/speckit.tasks`. This plan stops at the end of Phase 1.

---

## Increment — Tester-feedback requirements (2026-09-16)

**Scope**: Adds the three requirements introduced by the Session 2026-09-16 clarifications in [spec.md](spec.md): **FR-040** (in-app "Rate app"), **FR-041** (offline Help/FAQ + Privacy), **FR-042** (minimal TalkBack accessibility). The core feature above is already implemented; this increment is **additive** — it does not touch the run engine, import pipeline, or data model.

### Summary

Three small, offline-preserving additions surfaced by closed-testing feedback:

- **FR-040 Rate app** — an Options row that launches the Google **Play In-App Review** flow, with a Play Store deep-link fallback. Uses `com.google.android.play:review-ktx`, which does **not** add the `INTERNET` permission (the Play Store app brokers the review), so FR-034 holds.
- **FR-041 Help/FAQ + Privacy** — a static, on-device Help/FAQ + Privacy screen reachable from Options, reusing the wording already in `docs/privacy-policy.md`. No network, and no survey/feedback upload (excluded by the clarification).
- **FR-042 Accessibility (minimal)** — content descriptions / semantics for every interactive control so the app is TalkBack-navigable. Dynamic font-scaling and WCAG AA contrast are explicitly **deferred**.

### Technical Context (delta only)

- **New dependency**: `com.google.android.play:review-ktx` (Play In-App Review) via `gradle/libs.versions.toml`. Post-merge manifest check MUST confirm it introduces no `INTERNET` or tracking permission.
- **No new module, no new persistence, no new domain entities.** Help/Privacy content is static Compose (or a bundled asset).
- Everything else unchanged from the core Technical Context (Kotlin 2.0.21, Compose, AGP 8.13.2 / Gradle 8.13, JDK 17, minSdk 31 / target 36).

### Constitution / spec-guardrail check

| Guardrail | Status | Notes |
|---|---|---|
| FR-034 — no `INTERNET` | **Pass (verify)** | Play In-App Review needs no `INTERNET` in the app manifest; confirm via the merged release manifest after adding the dependency. |
| FR-006 — fully offline | **Pass** | Help/Privacy is static on-device; no survey/feedback upload (excluded per clarification). Rating is brokered by the Play Store app, not the app's own network. |
| §III Simplicity/YAGNI | **Pass** | One new library (Play Review), one new screen, no new architecture layers. |
| §II Privacy by default | **Pass** | No new data collected; the privacy screen restates the on-device-only guarantee. |

### Design & approach

**FR-040 Rate app**

- Add `libs.play.review` to the version catalog + `implementation` in `app/build.gradle.kts`.
- Options gains a "Rate app" row. On tap: `ReviewManagerFactory.create(context)` → `requestReviewFlow()` → `launchReviewFlow(activity, info)`; on any failure, fall back to an `ACTION_VIEW` intent to `market://details?id=com.fittogym.runtraining` (then `https://play.google.com/store/apps/details?id=…`).
- Play caps how often the in-app card appears; the fallback deep-link guarantees the listing is always reachable. The post-workout prompt stays an optional future toggle (FR-040 "MAY").

**FR-041 Help/FAQ + Privacy**

- New route `Routes.HELP` + `HelpScreen` (static Compose): a short FAQ (import a .fit, set threshold pace, beeps, HR pairing, mini view, why offline) and a privacy summary ported from `docs/privacy-policy.md`.
- Add a "Help & privacy" row in Options that navigates to it.

**FR-042 Accessibility (TalkBack)**

- Audit icon-only controls for `contentDescription`: Selection app bar (History, Options, the `+` FAB), Run page (Back, mini-view toggle, transport controls), History/Detail actions, and the mini-view overlay.
- Add `Modifier.semantics { … }` (`contentDescription`, plus `stateDescription` where a control conveys state — e.g., selected sort chip, running/paused).
- Verify with TalkBack on-device; add a manual-test recipe row to `quickstart.md`.

### Task outline (for `/speckit.tasks`)

1. Add Play In-App Review dependency (catalog + module); confirm the merged manifest still has **no** `INTERNET`.
2. Options: "Rate app" row → in-app review flow + Play-listing fallback.
3. `HelpScreen` + `Routes.HELP` + Options "Help & privacy" row; port privacy wording from `docs/privacy-policy.md`.
4. Accessibility pass: content descriptions/semantics for all icon-only controls + the mini view.
5. Manual-test recipe: TalkBack navigation of every screen; Rate flow (and the fallback when Play is unavailable); Help/Privacy renders offline.

This increment stops at Phase 1 design; task breakdown belongs to `/speckit.tasks`.
