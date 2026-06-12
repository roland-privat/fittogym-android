---
description: "Task list for feature implementation: core workout flow"
---

# Tasks: Core Workout Flow

**Input**: Design documents from [specs/001-core-workout-flow/](.)

**Prerequisites**:

- [spec.md](spec.md) — feature specification with the 4 user stories (P1, P1, P2, P2)
- [plan.md](plan.md) — implementation plan, structure decision, dependency list
- [research.md](research.md) — 12 technical decisions resolved in Phase 0
- [data-model.md](data-model.md) — Room schema, DataStore keys, domain types
- [contracts/intent-filters.md](contracts/intent-filters.md) — Android intent + permission surface
- [contracts/run-session-state-machine.md](contracts/run-session-state-machine.md) — workout-engine FSM
- [quickstart.md](quickstart.md) — VS Code build/deploy + manual test recipes A/B/C/D

**Tests**: Plan-pinned JVM unit tests for pure logic (`FitDecoder`, `DistanceStepDerivation`, `TssCalculator`, `RunSessionEngine`). No instrumented tests in v1; manual on-device recipes from `quickstart.md` are the canonical verification gate per Constitution §V.

**Organization**: Tasks are grouped by user story. Each story (especially US1 and US2) is independently testable and corresponds to one manual-test recipe.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps task to spec user story (US1 / US2 / US3 / US4)
- Each task includes the exact file path it produces or modifies

## Path Conventions

- Single-module Android app, code at `app/src/main/kotlin/com/example/runtraining/...`, resources at `app/src/main/res/`, manifest at `app/src/main/AndroidManifest.xml`, JVM unit tests at `app/src/test/kotlin/...`, test fixtures at `app/src/test/resources/`.
- Package layout sliced by concern: `ui/`, `nav/`, `workout/`, `persistence/`, `ble/`, `audio/`, `overlay/`, `service/`, `settings/`, `util/` (per [plan.md](plan.md) Project Structure).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring the project from "empty git repo" to "builds an empty debug APK that launches a stub Compose screen on the phone". No application logic yet.

- [X] T001 Generate Gradle wrapper at the project root using a pinned distribution: produce `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (pin `distributionUrl` to Gradle 8.10.x+), `gradlew`, and `gradlew.bat`. Commit all four.
- [X] T002 Create [settings.gradle.kts](settings.gradle.kts) at the project root: `rootProject.name = "RunTraining"`, `pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }`, `dependencyResolutionManagement { repositoriesMode.set(FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }`, `include(":app")`.
- [X] T003 [P] Create [gradle.properties](gradle.properties) with `org.gradle.jvmargs=-Xmx2g`, `android.useAndroidX=true`, `kotlin.code.style=official`, `org.gradle.parallel=true`, `org.gradle.caching=true`.
- [X] T004 [P] Create root [build.gradle.kts](build.gradle.kts) declaring `plugins { id("com.android.application") apply false; id("org.jetbrains.kotlin.android") apply false; id("com.google.devtools.ksp") apply false }` with versions referenced from the catalog.
- [X] T005 Create [gradle/libs.versions.toml](gradle/libs.versions.toml) with the **full version catalog** (AGP 8.7.x, Kotlin 2.0.x, KSP matching Kotlin, Compose BOM latest stable, Compose Compiler, Activity-Compose, Lifecycle-runtime-ktx / -viewmodel-compose / -service, Navigation-Compose, Room runtime/ktx/compiler, DataStore-preferences, Material3, and a `fit = "21.205.0"` entry for `com.garmin:fit`). All version refs documented inline.
- [X] T006 Create [app/build.gradle.kts](app/build.gradle.kts): `plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.google.devtools.ksp") }`, `android { namespace = "com.example.runtraining"; compileSdk = 35; defaultConfig { applicationId = "com.example.runtraining"; minSdk = 31; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget = "17" }; buildFeatures { compose = true } }`, source-sets pointed at `src/main/kotlin` and `src/test/kotlin`, Room schema export `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, **no** release variant configured, dependencies left empty (added per-story).
- [X] T007 [P] Create [app/proguard-rules.pro](app/proguard-rules.pro) as an empty placeholder (no release variant in v1).
- [X] T008 [P] Create [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) skeleton: `<manifest>` with `<application>` declaring `android:label="@string/app_name"`, `android:theme="@style/Theme.RunTraining"`, `android:allowBackup="false"`, and an empty `<activity android:name=".MainActivity" android:exported="true">` (intent filters added later by T031, T041–T044).
- [X] T009 [P] Create [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) with `<string name="app_name">Run Training</string>` (other strings added per story).
- [X] T010 [P] Create [app/src/main/res/values/themes.xml](app/src/main/res/values/themes.xml) with `<style name="Theme.RunTraining" parent="android:Theme.Material.Light.NoActionBar">` — bare-bones; the Compose Material 3 theme is set up in T026.
- [X] T011 [P] Create [app/src/main/res/xml/file_paths.xml](app/src/main/res/xml/file_paths.xml) with `<paths><files-path name="workouts" path="workouts/"/></paths>` (used by T044's FileProvider).
- [X] T012 [P] Append to [.gitignore](.gitignore): `.gradle/`, `build/`, `local.properties`, `app/build/`, `.idea/`, `*.iml`, `captures/`, `.cxx/`, `*.apk`. (Do **not** ignore `app/schemas/` — the Room schema dumps in there are committed so future migrations can be diff-reviewed.)
- [X] T013 [P] Create [README.md](README.md) at project root: one paragraph + link to [specs/001-core-workout-flow/quickstart.md](specs/001-core-workout-flow/quickstart.md) for build/deploy instructions.
- [X] T014 Verify build green: run `./gradlew assembleDebug` from VS Code terminal; resolve any wrapper / SDK-mismatch issues. **Checkpoint**: APK builds, even if empty.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the layer every user story depends on — domain types, persistence, settings, navigation, theme. Nothing user-facing yet.

**⚠️ CRITICAL**: No US1–US4 work can begin until this phase completes (T032 is the gate).

- [X] T015 Create empty package directories under `app/src/main/kotlin/com/example/runtraining/`: `ui/`, `ui/theme/`, `ui/common/`, `nav/`, `workout/model/`, `workout/engine/` (placeholder), `persistence/db/`, `persistence/db/entities/`, `persistence/files/`, `settings/`, `service/` (placeholder), `util/`. Add a one-line `package.kt` doc file in each so Git tracks them.
- [X] T016 [P] Create [app/src/main/kotlin/com/example/runtraining/util/Log.kt](app/src/main/kotlin/com/example/runtraining/util/Log.kt) — an `object Log` exposing `const val TAG = "RunTraining"` and thin wrappers `d(msg)`, `w(msg)`, `e(msg, t)` over `android.util.Log` using the shared tag.
- [X] T017 [P] Create [app/src/main/kotlin/com/example/runtraining/util/Format.kt](app/src/main/kotlin/com/example/runtraining/util/Format.kt) with pure helpers: `formatDuration(sec: Int): String` → `"m:ss"` or `"h:mm:ss"`, `formatPace(secPerKm: Int): String` → `"m:ss/km"`, `paceSecPerKmToKmh(secPerKm: Int): Double`, `formatSpeed(kmh: Double): String` → `"km/h"` with one decimal.
- [X] T018 [P] Create [app/src/main/kotlin/com/example/runtraining/workout/model/Models.kt](app/src/main/kotlin/com/example/runtraining/workout/model/Models.kt) with all domain types from [data-model.md §3](data-model.md): `enum Intensity`, `sealed interface SourceDuration` (`Time`/`Distance`/`Open`), `sealed interface Target` (`Pace`/`Open`) with `midpointSecPerKm`, `data class RepeatPosition`, `data class RepeatGroup`, `data class Step`, `data class Workout` (with `totalAuthoredSteps`, `isRunnable` as `val`/computed property).
- [X] T019 [P] Create [app/src/main/kotlin/com/example/runtraining/ble/HrSample.kt](app/src/main/kotlin/com/example/runtraining/ble/HrSample.kt) with `data class HrSample(val bpm: Int, val monotonicTimestampMs: Long)` and `data class HrMonitorPairing(val deviceId: String, val lastKnownName: String, val lastConnectedEpochMs: Long)`.
- [X] T020 [P] Create [app/src/main/kotlin/com/example/runtraining/settings/AppSettings.kt](app/src/main/kotlin/com/example/runtraining/settings/AppSettings.kt) with `enum class DisplayUnit { PACE, SPEED }` and `data class AppSettings(val thresholdPaceSecPerKm: Int?, val displayUnit: DisplayUnit, val lastPairedDeviceId: String?)`.
- [X] T021 [P] Create Room entities under `app/src/main/kotlin/com/example/runtraining/persistence/db/entities/`: [WorkoutEntity.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/entities/WorkoutEntity.kt), [WorkoutStepEntity.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/entities/WorkoutStepEntity.kt), [RepeatGroupEntity.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/entities/RepeatGroupEntity.kt), [HrMonitorPairingEntity.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/entities/HrMonitorPairingEntity.kt) — exact columns and indexes per [data-model.md §1](data-model.md), with foreign keys + cascade rules.
- [X] T022 [P] Create Room DAOs under `app/src/main/kotlin/com/example/runtraining/persistence/db/`: [WorkoutDao.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/WorkoutDao.kt) (insert with `OnConflictStrategy.ABORT`, query by id with relations, query all ordered by `imported_at_epoch_ms DESC`, delete by id, update `display_name`, update `tss`), [StepDao.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/StepDao.kt) (`insertAll`, `findByWorkoutId`), [RepeatGroupDao.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/RepeatGroupDao.kt), [HrMonitorPairingDao.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/HrMonitorPairingDao.kt).
- [X] T023 Create [app/src/main/kotlin/com/example/runtraining/persistence/db/RunTrainingDatabase.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/RunTrainingDatabase.kt) — `abstract class RunTrainingDatabase : RoomDatabase()` with the four DAO accessors, `@Database(entities = [...], version = 1, exportSchema = true)`. Provide a single-instance builder (`Room.databaseBuilder(...).build()`); add a thread-safe `fun get(context): RunTrainingDatabase`.
- [X] T024 [P] Create [app/src/main/kotlin/com/example/runtraining/persistence/db/Mappers.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/Mappers.kt) with entity↔domain mappers (`WorkoutWithChildren.toDomain()`, `Workout.toEntities()` returning `(WorkoutEntity, List<StepEntity>, List<RepeatGroupEntity>)`) and the boundary validation rules from [data-model.md §4](data-model.md) (contiguous step_index, pace bounds 120–1200, repeat 2..99, etc.). Validation throws `IllegalArgumentException` on violation; callers translate that to a Rejected import result.
- [X] T025 Create [app/src/main/kotlin/com/example/runtraining/settings/AppSettingsRepository.kt](app/src/main/kotlin/com/example/runtraining/settings/AppSettingsRepository.kt): wraps `DataStore<Preferences>` named `app-settings.preferences_pb`, exposes `val settings: Flow<AppSettings>` and suspend updaters `setThresholdPace(Int?)`, `setDisplayUnit(DisplayUnit)`, `setLastPairedDeviceId(String?)`. Keys per [data-model.md §2](data-model.md).
- [X] T026 Create [app/src/main/kotlin/com/example/runtraining/persistence/files/WorkoutBlobStore.kt](app/src/main/kotlin/com/example/runtraining/persistence/files/WorkoutBlobStore.kt): `fun sha256(bytes: ByteArray): String`, `fun save(bytes: ByteArray): String /*sha*/` (writes to `context.filesDir/workouts/<sha>.fit`, mkdirs parent, no-op if file exists, returns sha), `fun load(sha: String): ByteArray?`, `fun delete(sha: String)`.
- [X] T027 Create [app/src/main/kotlin/com/example/runtraining/persistence/WorkoutRepository.kt](app/src/main/kotlin/com/example/runtraining/persistence/WorkoutRepository.kt): orchestrates DAOs + `WorkoutBlobStore`. API: `suspend fun upsert(workout: Workout, rawBytes: ByteArray): UpsertResult` (where `UpsertResult` is `NewImport(id)` | `AlreadyImported(id)`, dedup by `content_hash` via DAO unique constraint), `fun observeAll(): Flow<List<Workout>>` (joined query, mapped via `Mappers`), `suspend fun get(id: Long): Workout?`, `suspend fun rename(id: Long, newName: String)`, `suspend fun updateTss(id: Long, tss: Double?)`, `suspend fun delete(id: Long)` (Room cascade for child rows + `WorkoutBlobStore.delete(hash)`).
- [X] T028 [P] Create theme files: [app/src/main/kotlin/com/example/runtraining/ui/theme/Color.kt](app/src/main/kotlin/com/example/runtraining/ui/theme/Color.kt) (Material 3 color tokens; intensity color map: warmup blue, active deep blue, rest lighter blue, cooldown muted blue, other gray — matching the spec's reference image), [Type.kt](app/src/main/kotlin/com/example/runtraining/ui/theme/Type.kt), [Theme.kt](app/src/main/kotlin/com/example/runtraining/ui/theme/Theme.kt) exposing a `@Composable fun RunTrainingTheme(content: @Composable () -> Unit)` with light + dark dynamic color.
- [X] T029 [P] Create shared composables under `app/src/main/kotlin/com/example/runtraining/ui/common/`: [IntensityBadge.kt](app/src/main/kotlin/com/example/runtraining/ui/common/IntensityBadge.kt) (small chip showing the `Intensity` label with the matching color from T028), [TargetDisplay.kt](app/src/main/kotlin/com/example/runtraining/ui/common/TargetDisplay.kt) (renders a `Target` per `DisplayUnit`: pace "5:40–5:47 /km" or speed "10.36–10.58 km/h"), [DurationText.kt](app/src/main/kotlin/com/example/runtraining/ui/common/DurationText.kt) (uses `Format.formatDuration`).
- [X] T030 Create [app/src/main/kotlin/com/example/runtraining/nav/Routes.kt](app/src/main/kotlin/com/example/runtraining/nav/Routes.kt) with sealed `Routes` constants: `Selection`, `Details(workoutId)`, `Run(workoutId)`, `Options`, and route-pattern strings for `NavHost`.
- [X] T031 Create [app/src/main/kotlin/com/example/runtraining/MainActivity.kt](app/src/main/kotlin/com/example/runtraining/MainActivity.kt) and [app/src/main/kotlin/com/example/runtraining/nav/AppNavHost.kt](app/src/main/kotlin/com/example/runtraining/nav/AppNavHost.kt): a single Activity hosting `RunTrainingTheme { AppNavHost(navController) }`. NavHost composes stub placeholders for every route (`Text("Selection placeholder")` etc.) — real screens land in their stories. Add `<intent-filter>` for `MAIN`+`LAUNCHER` to `MainActivity` in [AndroidManifest.xml](app/src/main/AndroidManifest.xml).
- [X] T032 Verify build + smoke-run: `./gradlew installDebug` deploys; `adb shell am start ...MainActivity` opens the stub. **Checkpoint — foundation ready.**

---

## Phase 3: User Story 1 — Import a workout from a shared/opened FIT file (Priority: P1) 🎯 MVP

**Goal**: A `.fit` file shared from another app (WhatsApp, Files, Drive) lands in the app, gets parsed, summarized, and stored. Training Details shows it; Training Selection lists it on next cold start. Threshold pace is editable in Options so TSS becomes meaningful.

**Independent Test**: Manual recipe **A** (FIT share & open) and recipe **D** (TSS & threshold pace) from [quickstart.md §3](quickstart.md). Plus the JVM unit tests T036–T038.

### Implementation for User Story 1

- [X] T033 [US1] Add `libs.fit` from the version catalog to `app/build.gradle.kts` `dependencies { implementation(libs.fit) }`, plus Room, DataStore, Compose-foundation, Navigation-Compose, Lifecycle-viewmodel-compose.
- [X] T034 [US1] Create [app/src/main/kotlin/com/example/runtraining/workout/fit/FitDecoder.kt](app/src/main/kotlin/com/example/runtraining/workout/fit/FitDecoder.kt): a `data class DecodedWorkout(name, sport, steps, repeats)`, sealed `Result.Ok` / `Result.Rejected(RejectReason)`, and `fun decode(bytes: ByteArray): Result`. Uses Garmin's `Decode` + `MesgBroadcaster` with `FileIdMesgListener`, `WorkoutMesgListener`, `WorkoutStepMesgListener` to harvest `wkt_name`, `sport`, `num_valid_steps`, and each step's `wkt_step_name`, `intensity`, `duration_type`, `duration_value`, `target_type`, `target_value`, `custom_target_value_low/high`. Detect repeat blocks via `duration_type ∈ {repeat_until_step_complete, repeat_until_steps_complete}` and the step's `duration_value` (target step index) — assemble `DecodedRepeat(startIndex, endIndex, iterationCount)`. Reject if sport ≠ running or steps empty.
- [X] T035 [US1] Create [app/src/main/kotlin/com/example/runtraining/workout/fit/DecodedToDomain.kt](app/src/main/kotlin/com/example/runtraining/workout/fit/DecodedToDomain.kt): pure function `DecodedWorkout.toDomain(originalFilename, importedAtEpochMs, contentHash): Workout`. Maps intensity enum, computes per-step `effective_duration_sec` per **FR-004a** (distance + pace → `distance / pace_midpoint`; distance + no pace → null/"open"), computes planned overall duration + distance per the **Distance computation** assumption, attaches `RepeatPosition` to steps inside groups, **does not** compute TSS here (T040 owns that).
- [X] T036 [P] [US1] Add bundled fixtures: copy `TrainingPeaksData/2026-05-21_FitnessTes.fit` and `TrainingPeaksData/2026-05-27_NoLetUp.fit` into [app/src/test/resources/fixtures/](app/src/test/resources/fixtures/) (committed alongside the test code).
- [X] T037 [P] [US1] Create [app/src/test/kotlin/com/example/runtraining/workout/fit/FitDecoderTest.kt](app/src/test/kotlin/com/example/runtraining/workout/fit/FitDecoderTest.kt) — JUnit 4 + kotlin-test. For each bundled `.fit` fixture: assert sport == running, assert step count and order match the corresponding `.md` ground-truth in `TrainingPeaksData/`, assert each step's intensity / duration / target-pace range / repeat membership match the `.md` table verbatim. Also: `decode(byteArrayOf(0, 1, 2))` returns `Rejected(MalformedFit)`; `decode(emptyArray)` returns `Rejected(MalformedFit)`.
- [X] T038 [P] [US1] Create [app/src/test/kotlin/com/example/runtraining/workout/fit/DistanceStepDerivationTest.kt](app/src/test/kotlin/com/example/runtraining/workout/fit/DistanceStepDerivationTest.kt) — synthesize `DecodedStep` instances directly: distance + pace → assert `effective_duration_sec == distance / pace_midpoint` (e.g., 1000 m @ 5:00/km → 300 s); distance + no pace → `effective_duration_sec == null`; time-based with pace → unchanged; "open" → `null`.
- [X] T039 [US1] Create [app/src/main/kotlin/com/example/runtraining/workout/tss/TssCalculator.kt](app/src/main/kotlin/com/example/runtraining/workout/tss/TssCalculator.kt) with `fun computeTss(workout: Workout, thresholdPaceSecPerKm: Int?): Double?` per [research.md §8](research.md). Returns `null` if threshold is null; per-step `tss_i = (sec_i * IF_i² ) / 36` where `IF_i = threshold / pace_midpoint_i`. Skips steps with `effective_duration_sec == null` or `Target.Open`.
- [X] T040 [P] [US1] Create [app/src/test/kotlin/com/example/runtraining/workout/tss/TssCalculatorTest.kt](app/src/test/kotlin/com/example/runtraining/workout/tss/TssCalculatorTest.kt): (a) one-hour step at threshold pace → TSS ≈ 100; (b) `threshold == null` → returns null; (c) all-open workout → 0; (d) faster than threshold → larger TSS than slower; (e) repeat-style mixed workout (4 × 30s at threshold + 4 × 30s easy + 30 min steady) gives a deterministic value that matches a manual hand-calculation in the test comments.
- [X] T041 [US1] Create [app/src/main/kotlin/com/example/runtraining/workout/import/ImportWorkoutUseCase.kt](app/src/main/kotlin/com/example/runtraining/workout/import/ImportWorkoutUseCase.kt): `suspend operator fun invoke(uri: Uri): ImportResult` where `ImportResult` is `NewImport(workoutId)` | `AlreadyImported(workoutId)` | `Rejected(reason)`. Steps: read bytes via `ContentResolver.openInputStream`, compute SHA-256 via `WorkoutBlobStore`, look up by hash via `WorkoutRepository.observeAll`/`findByHash` (add a DAO query), if found → `AlreadyImported`; else `FitDecoder.decode` → on `Ok` map via `DecodedToDomain.toDomain` → read current threshold from `AppSettingsRepository.settings.first()` → compute TSS → `WorkoutRepository.upsert(workout, bytes)` → return `NewImport`. On any decode `Rejected` or mapper validation throw → `Rejected(reason)`.
- [X] T042 [US1] Add `findByHash(sha: String): WorkoutEntity?` query to [WorkoutDao.kt](app/src/main/kotlin/com/example/runtraining/persistence/db/WorkoutDao.kt) and a corresponding `WorkoutRepository.findByHash(sha)` helper used by T041.
- [X] T043 [US1] Add intent filters to [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) under `MainActivity`: `ACTION_SEND` with the three FIT MIME types from [contracts/intent-filters.md §A](contracts/intent-filters.md). Add `<string name="share_fit_label">` to [strings.xml](app/src/main/res/values/strings.xml).
- [X] T044 [US1] Add intent filters to [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) for `ACTION_VIEW` with `scheme="content"` + FIT MIME types ([contracts/intent-filters.md §B](contracts/intent-filters.md)) **and** the `pathPattern` ladder for `*.fit` ([§C](contracts/intent-filters.md)). Add `<string name="open_fit_label">`.
- [X] T045 [US1] Declare the `FileProvider` in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) per [contracts/intent-filters.md §FileProvider](contracts/intent-filters.md), authority `${applicationId}.fileprovider`, pointing at `res/xml/file_paths.xml` (already created in T011).
- [X] T046 [US1] Implement intent dispatch in [MainActivity.kt](app/src/main/kotlin/com/example/runtraining/MainActivity.kt) `handleIntent(intent: Intent)`: detect `ACTION_SEND` (extract `EXTRA_STREAM` Uri) and `ACTION_VIEW` (extract `intent.data`); if present launch `ImportWorkoutUseCase` on `Dispatchers.IO`; on `NewImport` or `AlreadyImported` → `navController.navigate(Routes.Details(workoutId))` (clearing back-stack so back returns to selection); on `Rejected` → show a snackbar `"This isn't a valid running workout file"` and stay on selection. **Cold-start routing (FR-009)**: if no FIT intent, start on `Routes.Selection`.
- [X] T047 [US1] Create [app/src/main/kotlin/com/example/runtraining/ui/details/DetailsViewModel.kt](app/src/main/kotlin/com/example/runtraining/ui/details/DetailsViewModel.kt): `Flow<DetailsUiState>` exposing `workout`, `isAlreadyImported: Boolean` (passed in via nav-arg or last import-result), `displayName: TextFieldState`, `onRenameConfirm(newName)`, `onDelete()`, `onTapRun()` (will route to `Routes.Run(workoutId)` — Run page itself is US2 so until then it shows a placeholder).
- [X] T048 [US1] Create [app/src/main/kotlin/com/example/runtraining/ui/details/DetailsScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/details/DetailsScreen.kt): editable name field (autofocused on first import per US1 AS1), read-only rows: overall duration, overall distance, date imported, TSS (or `—`), step table (step #, name, intensity badge, duration, target). "Already imported" banner when `isAlreadyImported`. Save + Cancel + Delete + Run (disabled if `!workout.isRunnable`) buttons.
- [X] T049 [US1] Create [app/src/main/kotlin/com/example/runtraining/ui/selection/SelectionViewModel.kt](app/src/main/kotlin/com/example/runtraining/ui/selection/SelectionViewModel.kt): collects `WorkoutRepository.observeAll()` into a `StateFlow<SelectionUiState>`; exposes `onTapRow(workoutId)`, `onLongPressRow(workoutId)` (delete confirm + open-details menu), `onTapOptions()`.
- [X] T050 [US1] Create [app/src/main/kotlin/com/example/runtraining/ui/selection/SelectionScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/selection/SelectionScreen.kt): `LazyColumn` of rows (display name + duration + TSS/`—`), an overflow menu → "Options" (routes to `Routes.Options`), and an empty-state message ("No workouts yet — share a .fit file from another app").
- [X] T051 [US1] Replace the Selection / Details route placeholders in [AppNavHost.kt](app/src/main/kotlin/com/example/runtraining/nav/AppNavHost.kt) with the real screens; wire navigation actions from the view models.
- [X] T052 [US1] Create [app/src/main/kotlin/com/example/runtraining/ui/options/OptionsViewModel.kt](app/src/main/kotlin/com/example/runtraining/ui/options/OptionsViewModel.kt) and [OptionsScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/options/OptionsScreen.kt) with **only** the Threshold Pace input for now (input mask `m:ss`, parse to `Int` sec/km, validation range 120–600 with a soft-confirm dialog outside that range per [research.md §12](research.md)). Save writes via `AppSettingsRepository.setThresholdPace(...)`. The HRM section (US3) and display-unit toggle (US2/FR-013a) are added by later tasks.
- [X] T053 [US1] On any `setThresholdPace(...)` change in `AppSettingsRepository`, trigger a `TssRecomputeJob` (`suspend fun recomputeAllTss()` on `WorkoutRepository`) that iterates every workout, recomputes via `TssCalculator`, and persists via `updateTss(...)`. Invoked from `OptionsViewModel` on Save.
- [X] T054 [US1] On `DetailsViewModel.onDelete()`, call `WorkoutRepository.delete(id)` (which also deletes the blob file). Confirm with a dialog before deleting.
- [X] T055 [US1] **Checkpoint — manual recipe A + D**: run [quickstart.md §3 Recipe A](quickstart.md) (share / open / dedup / malformed) and **Recipe D** (TSS becomes numeric after setting threshold). All steps must pass before moving to US2.

---

## Phase 4: User Story 2 — Pick and execute a workout with live timeline and controls (Priority: P1)

**Goal**: From the selection page, tap a workout → run page → start / pause / stop / step-forward / step-backward, with 5-beep step-end countdown, immersive fullscreen, foreground service that survives screen-off, and a completion summary card on natural end.

**Independent Test**: Manual recipe **C** ("Immersive fullscreen, keep-screen-on, beeps, and mini view"; the mini-view portion is US4 — verify C1–C6 and C12–C13 here, defer C7–C11 to US4) from [quickstart.md §3](quickstart.md). Plus the JVM unit test T058.

### Implementation for User Story 2

- [X] T056 [P] [US2] Create [app/src/main/kotlin/com/example/runtraining/workout/engine/RunState.kt](app/src/main/kotlin/com/example/runtraining/workout/engine/RunState.kt) with `enum class RunState { IDLE, RUNNING, PAUSED, COMPLETE }` and `data class SkipCheckpoint(skippedStepIndex, elapsedMsAtSkip, accumulatedHrConnectedMsAtSkip)` and `data class RunControlsState(startEnabled, pauseEnabled, stopEnabled, stepForwardEnabled, stepBackwardEnabled)`.
- [X] T057 [P] [US2] Create [app/src/main/kotlin/com/example/runtraining/workout/engine/RunUiState.kt](app/src/main/kotlin/com/example/runtraining/workout/engine/RunUiState.kt) — the exact shape from [contracts/run-session-state-machine.md "Observability surface"](contracts/run-session-state-machine.md) (workoutDisplayName, totalAuthoredSteps, currentStepIndex, currentStepName, currentIntensity, currentTargetDisplay, nextTargetDisplay, currentRepeatLabel, stepElapsedSec, stepRemainingSec, sessionElapsedSec, sessionRemainingSec, hrBpm, state, controls, playheadFraction).
- [X] T058 [US2] Create [app/src/main/kotlin/com/example/runtraining/workout/engine/RunSessionEngine.kt](app/src/main/kotlin/com/example/runtraining/workout/engine/RunSessionEngine.kt) — full FSM per [contracts/run-session-state-machine.md](contracts/run-session-state-machine.md). Public API: `fun load(workout: Workout)`, `fun start()`, `fun pause()`, `fun stop()`, `fun stepForward()`, `fun stepBackward()`, `fun onHrSample(sample: HrSample?)`, `val uiState: StateFlow<RunUiState>`. Internal: injectable `Clock` (`SystemClock.elapsedRealtime()` wrapper), injectable `BeepScheduler`, monotonic per-step + per-session bookkeeping, `isStepBackUndoable`, `lastSkippedStepCheckpoint`, HR-connected ms accumulator (only while `RUNNING`). Emits at 4 Hz via a launched coroutine ticker; suspends ticker on PAUSED/IDLE/COMPLETE.
- [X] T059 [P] [US2] Create [app/src/test/kotlin/com/example/runtraining/workout/engine/RunSessionEngineTest.kt](app/src/test/kotlin/com/example/runtraining/workout/engine/RunSessionEngineTest.kt) with `FakeClock` + `FakeBeepScheduler`: every transition in the FSM event table, plus the five invariants from [contracts/run-session-state-machine.md "Invariants"](contracts/run-session-state-machine.md), plus drift bound over a simulated 5-minute step.
- [X] T060 [US2] Create [app/src/main/kotlin/com/example/runtraining/audio/TonePlayer.kt](app/src/main/kotlin/com/example/runtraining/audio/TonePlayer.kt): wraps `ToneGenerator(AudioManager.STREAM_MUSIC, 80)`; `fun beep()` requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, plays `TONE_PROP_BEEP` for ~150 ms, then `abandonAudioFocus()`. Safe to call from any thread (post to a single-thread executor).
- [X] T061 [US2] Create [app/src/main/kotlin/com/example/runtraining/audio/BeepScheduler.kt](app/src/main/kotlin/com/example/runtraining/audio/BeepScheduler.kt) interface + a `HandlerBeepScheduler` impl backed by `Handler(Looper.getMainLooper())`: `fun scheduleStepEndBeeps(stepStartMonotonicMs, effectiveDurationSec, onBeep: () -> Unit)` (schedules 5 callbacks at the precise monotonic ms targets), `fun cancelAll()`. The `RunSessionEngine` uses this interface; tests inject a fake.
- [X] T062 [US2] Create [app/src/main/kotlin/com/example/runtraining/service/WorkoutForegroundService.kt](app/src/main/kotlin/com/example/runtraining/service/WorkoutForegroundService.kt) as `LifecycleService`. Holds singleton `RunSessionEngine`, `TonePlayer`, a `Binder` exposing `engine: RunSessionEngine` to the UI. On `onStartCommand(ACTION_PRIME, workoutId)` → load workout via repository, `startForeground(NOTIFICATION_ID, buildNotification(...), FOREGROUND_SERVICE_TYPE_HEALTH)` on API 34+ (omit type on 31–33). On `ACTION_START`, `ACTION_PAUSE`, `ACTION_STOP`, `ACTION_STEP_FWD`, `ACTION_STEP_BACK` → forward to engine. Collects `engine.uiState` to refresh notification each tick (step name + remaining). `stopForeground + stopSelf` on Stop or after Complete's Done.
- [X] T063 [US2] Declare the service in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) with `<service android:name=".service.WorkoutForegroundService" android:exported="false" android:foregroundServiceType="health"/>` and add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`, `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH"/>`, `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` per [contracts/intent-filters.md](contracts/intent-filters.md).
- [X] T064 [US2] Create [app/src/main/kotlin/com/example/runtraining/service/NotificationBuilder.kt](app/src/main/kotlin/com/example/runtraining/service/NotificationBuilder.kt) building the ongoing notification (channel `workout`, importance HIGH, no vibration, no sound — the beeps are separate). `contentText = "Step ${n}/${N} • ${stepRemainingSec} left"`. `contentIntent` deep-links to `Routes.Run` via `ACTION_OPEN_RUN_PAGE`.
- [X] T065 [US2] Create [app/src/main/kotlin/com/example/runtraining/ui/run/RunViewModel.kt](app/src/main/kotlin/com/example/runtraining/ui/run/RunViewModel.kt): binds to `WorkoutForegroundService` via `bindService` on enter; sends `ACTION_PRIME` with `workoutId`; collects `engine.uiState` → re-emits to UI; exposes `onStart/onPause/onStop/onStepFwd/onStepBack` which call `startService(action)`. Unbinds on `onCleared()` (service keeps running standalone).
- [X] T066 [US2] Create [app/src/main/kotlin/com/example/runtraining/ui/run/TimelineChart.kt](app/src/main/kotlin/com/example/runtraining/ui/run/TimelineChart.kt) — a `Canvas` composable per [research.md §11](research.md): expand repeat blocks into the visual, draw rectangle per expanded step (width ∝ effective duration, fill color = intensity from theme), draw a thin top stripe per pace zone (light overlay), draw the playhead as a 2 dp `primary` vertical line at `playheadFraction`. Step boundaries visually distinct (1 dp surface-color separator).
- [X] T067 [US2] Create [app/src/main/kotlin/com/example/runtraining/ui/run/RunControls.kt](app/src/main/kotlin/com/example/runtraining/ui/run/RunControls.kt): five icon buttons (Start, Pause, Stop, StepBackward, StepForward) whose `enabled` is driven directly by `RunUiState.controls.*Enabled`. Layout: a single row, big touch targets (48 dp+).
- [X] T068 [US2] Create [app/src/main/kotlin/com/example/runtraining/ui/run/RunScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/run/RunScreen.kt): top app bar with workout display name + back, middle = `TimelineChart`, key-metrics block (large `step n of N` + step name + `IntensityBadge`; large step-remaining; rows for step-elapsed, current target via `TargetDisplay`, next target, overall elapsed, overall remaining, HR, repeat-lap counter), bottom = `RunControls`. **Immersive fullscreen + KEEP_SCREEN_ON** wired via `DisposableEffect`: on enter call `WindowInsetsControllerCompat.hide(systemBars)` and `activity.window.addFlags(FLAG_KEEP_SCREEN_ON)`; on exit, reverse both. Navigates to `CompleteScreen` when `state == COMPLETE`.
- [X] T069 [US2] Create [app/src/main/kotlin/com/example/runtraining/ui/complete/CompleteScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/complete/CompleteScreen.kt) and `CompleteViewModel.kt`: shows summary card with workout display name, planned vs actual elapsed (paused excluded), avg HR row **if** `hrConnectedDurationMs >= 0.5 * elapsedSessionMs` (else omitted), planned TSS (or `—`). Single tap-anywhere or **Done** button → navigates back to `Routes.Selection`. Implements **FR-022a** exactly.
- [X] T070 [US2] Wire selection-row tap → `Routes.Run(workoutId)`; in `RunScreen` on enter, send `ACTION_PRIME` to the service; the service binder gives back the engine so the screen can collect state immediately even before `Start` is pressed (Spec FR-017 covers "running or paused"; idle state shows zeroed counters but the rest of the screen renders).
- [X] T071 [US2] Add the **target-intensity display unit toggle** to [OptionsScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/options/OptionsScreen.kt) (pace ⇄ speed). On change, persist via `AppSettingsRepository.setDisplayUnit(...)`. `TargetDisplay` (T029) already reads the unit. Verifies **FR-013a**.
- [X] T072 [US2] Implement "workout with all-open / zero-runnable steps" handling in Selection: when `workout.isRunnable == false`, the Run button on the row tap shows a small toast/tooltip "This workout has no runnable steps" and does NOT navigate to Run. **Edge case** from spec.
- [X] T073 [US2] Wire `MainActivity`'s `ACTION_OPEN_RUN_PAGE` deep-link from the notification (T064) → if a workout is currently primed in the service, navigate to `Routes.Run` for that workout.
- [X] T074 [US2] **Checkpoint — manual recipe C (audio + immersive parts)**: run [quickstart.md §3 Recipe C](quickstart.md) steps C1–C6 and C12–C13. The mini-view-specific steps (C7–C11) are deferred to US4. Must pass before US3.

---

## Phase 5: User Story 3 — Connect a broadcast heart rate monitor and see live HR (Priority: P2)

**Goal**: From Options, scan + pair with a broadcast BLE HRM; the live BPM appears on the Workout Run page (idle or running) and recovers gracefully from signal loss / Bluetooth off / permission denial.

**Independent Test**: Manual recipe **B** ("Connect & use a Bluetooth-LE heart-rate monitor") from [quickstart.md §3](quickstart.md).

### Implementation for User Story 3

- [X] T075 [US3] Add BLE permissions to [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml): `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>`, `<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>`, `<uses-feature android:name="android.hardware.bluetooth_le" android:required="false"/>`. Verifies **FR-025**.
- [X] T076 [US3] Create [app/src/main/kotlin/com/example/runtraining/ble/HrmClient.kt](app/src/main/kotlin/com/example/runtraining/ble/HrmClient.kt): `class HrmClient(context)`. `fun scan(): Flow<List<DiscoveredDevice>>` — uses `BluetoothLeScanner` with a `ScanFilter.Builder().setServiceUuid(ParcelUuid("0000180D-0000-1000-8000-00805F9B34FB"))`. `suspend fun connect(deviceId: String)` — `BluetoothManager.getRemoteDevice(deviceId).connectGatt(...)`, on `onServicesDiscovered` enable notifications on `0x2A37`. `val hrSample: StateFlow<HrSample?>` parses HRS-flags byte 0 (bit 0: UINT8 vs UINT16; ignore RR + EE). `fun disconnect()`. Reconnect-on-disconnect logic: `Flow` of connection state, exponential backoff capped at 30 s, only while a workout session is active.
- [X] T077 [US3] Inject `HrmClient` into `WorkoutForegroundService` (T062). On service create + auto-reconnect (T079) + when the user explicitly connects from Options, call `engine.onHrSample(...)` for every emitted sample. Set `hrSample = null` on disconnect or no-sample-for-3-seconds.
- [X] T078 [US3] Extend [OptionsScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/options/OptionsScreen.kt) and [OptionsViewModel.kt](app/src/main/kotlin/com/example/runtraining/ui/options/OptionsViewModel.kt) with an **HR monitor** section: "Connect HR monitor" action that requests runtime permissions (`BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`) via `ActivityResultContracts.RequestMultiplePermissions`, on grant starts `HrmClient.scan()`, lists `DiscoveredDevice`s as they arrive, tap → `connect(deviceId)` → on success persist `HrMonitorPairing` via `HrMonitorPairingDao` AND `AppSettingsRepository.setLastPairedDeviceId(...)`. When already paired: show device name + "Connected" + Disconnect button. Verifies **FR-024**, **FR-026**, **FR-027**.
- [X] T079 [US3] Implement auto-reconnect: in `WorkoutForegroundService.onCreate`, read `AppSettings.lastPairedDeviceId`; if non-null and `BluetoothAdapter.isEnabled` and permissions present, call `HrmClient.connect(...)` quietly. Failures are silent (no toast); the UI just shows `—` until success.
- [X] T080 [US3] Render the HR value on the Run page: `RunUiState.hrBpm == null` → `—` (or `— (signal lost)` after T077's 3-second-no-sample detection sets a `hrLostSignal` flag in `RunUiState`; add it to the data class). Spec acceptance scenarios US3 AS3 / AS4.
- [X] T081 [US3] Handle permission-denied path in Options: if the user denies `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`, show a clearly disabled state with a rationale ("Heart rate monitor needs Nearby Devices permission. The rest of the app keeps working.") and a "Open app info" button that launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. Verifies **US3 AS6 / FR-033**.
- [X] T082 [US3] Handle Bluetooth-off path: observe `BluetoothAdapter.ACTION_STATE_CHANGED` via a `BroadcastReceiver` registered by the service; when `STATE_OFF`, set `hrSample = null` and have the Run page render `— (Bluetooth off)`; on `STATE_ON`, attempt auto-reconnect. Verifies **US3 AS5**.
- [ ] T083 [US3] **Checkpoint — manual recipe B**: run all 8 steps (B1–B8) of [quickstart.md §3 Recipe B](quickstart.md). Especially verify B5 (out-of-range recovery within 10 s, **SC-006**) and B8 (permission-denied path doesn't crash other features).

---

## Phase 6: User Story 4 — Always-on-top mini view during a workout (Priority: P2)

**Goal**: While a workout is running, a toggle on the Run page enables a floating overlay that stays visible on top of other apps showing the four required values. Dismisses on Stop / Complete; degrades gracefully if overlay permission is denied.

**Independent Test**: Manual recipe **C** steps **C7–C11** from [quickstart.md §3](quickstart.md).

### Implementation for User Story 4

- [X] T084 [US4] Add `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>` to [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml).
- [X] T085 [US4] Create [app/src/main/kotlin/com/example/runtraining/overlay/MiniViewContent.kt](app/src/main/kotlin/com/example/runtraining/overlay/MiniViewContent.kt) — a small `@Composable fun MiniViewContent(state: RunUiState)` rendering exactly the four fields from **FR-030**: current step elapsed, current step remaining, current target (via `TargetDisplay` so it respects `AppSettings.displayUnit`), next target. Compact layout (~200 × 100 dp), high-contrast.
- [X] T086 [US4] Create [app/src/main/kotlin/com/example/runtraining/overlay/MiniViewController.kt](app/src/main/kotlin/com/example/runtraining/overlay/MiniViewController.kt): owns a `WindowManager.LayoutParams` of `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS` (per [research.md §6](research.md) and the keyboard-collision edge case), `gravity = TOP|START`, user-draggable via a touch listener. Hosts a `ComposeView` with a proper `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` tree (so Compose state survives drags). `fun attach(state: StateFlow<RunUiState>)` adds the view and collects `state` into the `ComposeView`'s setContent; `fun detach()` removes the view; `fun bringHostToForeground()` fires a `PendingIntent` to `MainActivity` with `ACTION_OPEN_RUN_PAGE`. Click on the overlay calls `bringHostToForeground` then `detach`.
- [X] T087 [US4] Wire `MiniViewController` into `WorkoutForegroundService` (T062): a new `Binder` flag `setMiniViewEnabled(enabled: Boolean)`; when enabled AND state is `RUNNING` or `PAUSED`, call `attach(engine.uiState)`; on `Stop` or transition to `COMPLETE` or disable, call `detach()`. Persists across config changes because the service owns it.
- [X] T088 [US4] Add the **Mini View** toggle to [RunScreen.kt](app/src/main/kotlin/com/example/runtraining/ui/run/RunScreen.kt) (a small switch in the top app bar overflow). On first tap, if `Settings.canDrawOverlays(context) == false`: show a rationale dialog → on confirm, launch `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))`. On return (via `ActivityResultContracts.StartActivityForResult`), re-check; on grant set `setMiniViewEnabled(true)` and finish the toggle. Verifies **FR-029**.
- [X] T089 [US4] Implement permission-denied UX: if the user backs out of the overlay-permission screen without granting, the toggle stays off with a small inline message "Mini view needs system-overlay permission" and a button to retry. Rest of the workout flow continues unchanged. Verifies **US4 AS6**.
- [X] T090 [US4] On the `ACTION_OPEN_RUN_PAGE` deep-link received in `MainActivity` (already wired by T046/T073 for the notification, extend here for the overlay), additionally call `WorkoutForegroundService.setMiniViewEnabled(false)` so the overlay vanishes once the user is back in the foreground. Verifies **FR-032**.
- [X] T091 [US4] **Checkpoint — manual recipe C (mini-view parts)**: run [quickstart.md §3 Recipe C](quickstart.md) steps C7–C11 (rationale → settings page → overlay appears → keyboard collision → tap-to-foreground).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validate the spec's success criteria, walk through every Constitution §V graceful-degradation path, sand off rough edges.

- [ ] T092 [P] Walk through all four manual-test recipes (A, B, C, D) end-to-end on the USB-connected phone in one continuous session. Record any deviations as follow-up tasks here; fix or document each.
- [ ] T093 [P] Walk through every **FR-033** scenario (permission denied, Bluetooth off, no HRM in range, malformed FIT, oversized FIT, denied overlay permission, source `Uri` revoked) and confirm each produces a **visible non-crashing state**. **Also verify these three Spec Edge Cases**: (a) **app killed during workout** — force-stop the app mid-workout via `adb shell am force-stop com.example.runtraining`; relaunch — expect the user to land on Training Selection (no auto-resume); (b) **rotation / multi-window / split-screen during workout** — rotate the device and enter split-screen while a workout is running; expect the workout to keep running with timeline + counters continuing to update, and no data loss; (c) **share-during-running-workout** — with a workout running, share a `.fit` file from another app to this app; expect the in-progress workout to be preserved (Run state intact) and dismissing the Details page to return the user to the running workout. Any silent failure or crash is a release blocker.
- [ ] T094 Measure spec success criteria on the phone: **SC-001** (share → details < 5 s), **SC-003** (selection-tap → run < 1 s), **SC-004** (counter drift ≤ 1 s / 30 min — leave a workout running for 30 min and inspect), **SC-005** (screen-on ≥ 60 min during run), **SC-006** (HR recovers ≤ 10 s after coming back in range), **SC-010** (cold-app → Start ≤ 5 taps + 10 s).
- [ ] T095 [P] Spot-check Constitution principles in the final build: no `INTERNET` permission in [AndroidManifest.xml](app/src/main/AndroidManifest.xml), no `release` block in [app/build.gradle.kts](app/build.gradle.kts), no `ACCESS_FINE_LOCATION`, no `WAKE_LOCK`. Run `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk` to confirm.
- [ ] T096 [P] Verify APK size is reasonable (< 30 MB debug). If larger, investigate via `./gradlew app:dependencies | findstr -i fit` and ensure the FIT artifact is the expected ~250 KB.
- [ ] T097 Cleanup: delete any stub composables left over from foundational route placeholders (T031) once all real screens are wired.
- [ ] T098 [P] Final pass on [README.md](README.md): one short paragraph + the three key links ([spec](specs/001-core-workout-flow/spec.md), [plan](specs/001-core-workout-flow/plan.md), [quickstart](specs/001-core-workout-flow/quickstart.md)) + the one-line build command.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup, T001–T014)**: No dependencies. Must end on a green `assembleDebug` (T014).
- **Phase 2 (Foundational, T015–T032)**: Depends on Phase 1. Must end on a green install + stub-screen launch (T032). **BLOCKS all user stories.**
- **Phase 3 (US1, T033–T055)**: Depends on Phase 2. Independently deliverable as the MVP.
- **Phase 4 (US2, T056–T074)**: Depends on Phase 2; can start in parallel with US1 once Phase 2 is done (no code conflicts), but **must not** be merged before US1 because US1 owns the Selection screen US2 navigates from.
- **Phase 5 (US3, T075–T083)**: Depends on US2 because the HRM client integrates with `WorkoutForegroundService` (T062, T077) and pushes samples into the `RunSessionEngine` (T058).
- **Phase 6 (US4, T084–T091)**: Depends on US2 for the same reason (overlay is owned by the service).
- **Phase 7 (Polish, T092–T098)**: Depends on whichever stories are in scope for the release.

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2 only. Fully independent — no other story needed for delivery.
- **US2 (P1)**: Depends on Phase 2 + US1 (Selection screen) for end-to-end UX, but the engine + service + run-page work can proceed against a stub selection row.
- **US3 (P2)**: Depends on US2 (the service + engine into which HR plugs).
- **US4 (P2)**: Depends on US2 (same).

### Within Each User Story

- Data types & DAOs (Phase 2) before any business logic.
- Pure-logic Kotlin files (FIT decoder, mappers, TSS, engine) before their unit tests are *meaningful*; the test tasks themselves are marked [P] because they live in separate files, but a green test depends on the impl existing.
- Service before UI for US2/US3/US4 (the screens bind to the service).

### Parallel Opportunities

Tasks marked **[P]** within the same phase can be worked in parallel because they live in different files and have no inter-task data dependencies that aren't already satisfied.

Concrete parallel batches:

- **Phase 1 [P] cluster**: T003, T004, T007–T013 can be created in one batch after T001/T002.
- **Phase 2 [P] cluster A** (right after T015): T016, T017, T018, T019, T020 — all pure-Kotlin in disjoint files.
- **Phase 2 [P] cluster B** (after T015): T021 (entity files are one per file → parallelizable internally), T022 (DAOs likewise), T028 (theme), T029 (shared composables).
- **Phase 3 [P] cluster**: T036, T037, T038, T040 — fixtures + three unit tests.
- **Phase 4 [P] cluster**: T056, T057, T059 — engine model types + engine unit test (the test depends on T058 to actually pass, but the *file* can be authored in parallel from the test fixtures' point of view).
- **Phase 5**: linear (most service-bound).
- **Phase 6**: linear.
- **Phase 7 [P] cluster**: T092, T093, T095, T096, T098.

---

## Parallel Example: User Story 1 Foundations

```text
# After Phase 2 + T033 (FIT dep added), these four tasks live in separate files
# and have no inter-dependencies — author them in parallel:

Task T034: Create app/src/main/kotlin/.../workout/fit/FitDecoder.kt
Task T035: Create app/src/main/kotlin/.../workout/fit/DecodedToDomain.kt
Task T039: Create app/src/main/kotlin/.../workout/tss/TssCalculator.kt
Task T036: Copy fixtures to app/src/test/resources/fixtures/

# Then their tests, also parallel:

Task T037: Create app/src/test/kotlin/.../FitDecoderTest.kt
Task T038: Create app/src/test/kotlin/.../DistanceStepDerivationTest.kt
Task T040: Create app/src/test/kotlin/.../TssCalculatorTest.kt
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 (T001–T014).
2. Phase 2 (T015–T032).
3. Phase 3 — US1 (T033–T055).
4. **Stop** at T055. Run manual recipe A + D. You have a working "import + browse + edit + delete + threshold-pace + TSS" app. That alone is useful (the user can see TSS for every shared workout file).

### Incremental delivery

- After MVP (US1): add US2 (T056–T074). **Stop** at T074. Manual recipe C steps C1–C6 + C12–C13. You now have a runnable workout with timeline + beeps + completion summary. This is the headline feature.
- Add US3 (T075–T083). Manual recipe B. HR display lands.
- Add US4 (T084–T091). Manual recipe C steps C7–C11. Mini view lands.
- Polish (T092–T098).

### Single-developer pacing

This is a personal app per Constitution §I; "parallel team strategy" doesn't apply. The MVP scope (Phases 1–3) is the natural first delivery; everything after is genuinely optional polish (US2 + US3 + US4 + Phase 7).

---

## Notes

- **[P]** means *different files, no dependencies on incomplete tasks*. Two tasks editing the same file (e.g., several edits to `AndroidManifest.xml` for permissions / intent filters) are deliberately NOT marked [P] even when they're "conceptually independent" — sequence them.
- Every task has either an exact file path or an exact verification command. No vague tasks.
- Tests (T037, T038, T040, T059) are JVM-only unit tests against pure logic. They are **not** TDD-first; they are authored alongside or after the implementation file they cover. Their purpose is regression safety for the four most algorithmic parts of the codebase.
- Manual-test recipes (Recipes A/B/C/D from [quickstart.md §3](quickstart.md)) are the canonical verification gate per Constitution §V. Failed-recipe = release blocker.
- Commit after each task (or each logical cluster of [P] tasks) using descriptive intent-driven messages.
