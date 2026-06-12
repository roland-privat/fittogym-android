# Quickstart: Build, Deploy & Manually Verify the Run-Training App

**Date**: 2026-05-25 · **Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

This is the canonical operator manual for v1: how to get the app from a
clean clone onto a USB-connected Android phone using only **VS Code** (no
Android Studio), and how to manually verify the three Hardware-Integration
flows mandated by Constitution §V.

## 1. One-time host setup

### 1.1 Install JDK 17

Windows (PowerShell, recommended Microsoft Build of OpenJDK):

```powershell
winget install --id Microsoft.OpenJDK.17
```

Confirm:

```powershell
java -version
# openjdk version "17.0.x" ...
$env:JAVA_HOME = (Get-Command java).Source | Split-Path | Split-Path
```

### 1.2 Install the Android SDK command-line tools (no Studio)

1. Download "Command line tools only" from
   `https://developer.android.com/studio#command-line-tools-only`
   *(provided by Google as part of the standard Android SDK distribution)*.
2. Extract to `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\` so the
   final layout is `…\Sdk\cmdline-tools\latest\bin\sdkmanager.bat`.
3. Set environment variables and add `platform-tools` + `cmdline-tools` to
   `PATH`:

   ```powershell
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
   ```

   Persist these via *System Properties → Environment Variables* once you
   have them right.

4. Install required SDK packages:

   ```powershell
   sdkmanager --licenses        # accept all
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```

### 1.3 Install VS Code + recommended extensions

The workspace recommends (none are strictly required for `./gradlew installDebug`
to work, but they make editing pleasant):

- `fwcd.kotlin` — Kotlin Language
- `vscjava.vscode-gradle` — Gradle for Java
- `redhat.vscode-xml` — XML support for the manifest

Open the workspace folder in VS Code. The integrated terminal is the
canonical build surface.

### 1.4 Put the phone in USB-debugging mode

1. On the phone: *Settings → About phone → tap "Build number" 7 times* to
   unlock Developer options.
2. *Settings → System → Developer options → USB debugging → On*.
3. Plug the phone in over USB.
4. From the workspace terminal:

   ```powershell
   adb devices
   # List of devices attached
   # <serial>    device
   ```

   Approve the "Allow USB debugging from this computer?" dialog on the
   phone the first time.

## 2. The first build

From the workspace root, in the VS Code integrated terminal:

```powershell
.\gradlew.bat installDebug
```

Expected outcome:

- Gradle downloads its wrapper-pinned distribution into `~/.gradle`.
- AGP downloads its own pinned versions of the Android build tools.
- Kotlin / KSP / Room / Compose compilers run.
- An APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
- It is `adb install`-ed to the connected device. (`adb devices` must show
  one `device` line — not `unauthorized`, not `offline`.)

To launch the app after install:

```powershell
adb shell am start -n com.example.runtraining/.MainActivity
```

To watch logs while the app runs:

```powershell
adb logcat -v color RunTraining:V *:S
```

(`RunTraining` is the shared `Log.TAG_*` constant defined in
`com.example.runtraining.util.Log`.)

To uninstall (e.g., if you want a clean storage state):

```powershell
adb uninstall com.example.runtraining
```

## 3. Manual test recipes (Constitution §V — mandatory before each release)

Each recipe lists **precondition → steps → expected**. If any expected
condition fails, the run is a regression and must be fixed before merging
the change.

### Recipe A — FIT share & open

**Precondition**: app installed; at least one valid `.fit` workout file
exists somewhere on the device (e.g., synced from `TrainingPeaksData/` via
`adb push`). For repeatability, push the bundled sample once:

```powershell
adb push TrainingPeaksData\2026-05-27_NoLetUp.fit /sdcard/Download/
```

**Steps & expected outcomes**:

| # | Step | Expected |
|---|---|---|
| A1 | From the system Files app, navigate to `Download/`, tap `2026-05-27_NoLetUp.fit`, choose **Open with → Run Training App**. | App launches into **Training Details**. Name field shows `2026-05-27_NoLetUp` and is focused. Overall duration, distance, date imported, and TSS are populated (TSS = `—` if threshold pace is unset). |
| A2 | Edit the name to `No Let Up — easy run`, tap **Save**. | Returns to Training Details with the new name; pressing back returns to selection page where the row's display name is updated. |
| A3 | Cold-restart the app (`adb shell am force-stop com.example.runtraining` then relaunch). | Lands on Training Selection. The renamed workout is in the list with the same duration / TSS as in step A1. |
| A4 | From WhatsApp on the phone, share the same `.fit` file to this app. | Lands on Training Details for the **existing** workout (not a new entry). A subtle "Already imported" banner is visible. |
| A5 | From the Files app, share or open `/sdcard/Download/garbage.bin` (a random binary you create) using this app. | Visible error message ("This isn't a valid running workout file"). No new entry created. Tapping back returns to the selection page intact. |

### Recipe B — Connect & use a Bluetooth-LE heart-rate monitor

**Precondition**: app installed; a broadcast BLE HRM is nearby and turned
on (e.g., a Polar H10, Wahoo TICKR, Garmin HRM-Dual). Bluetooth is on on
the phone. Bluetooth runtime permissions have NOT yet been granted to the
app.

| # | Step | Expected |
|---|---|---|
| B1 | Cold-launch the app. From Training Selection, open the overflow → **Options** → **Connect HR monitor**. | The system Bluetooth runtime-permission dialog appears (BLUETOOTH_SCAN + BLUETOOTH_CONNECT). |
| B2 | Tap **Allow**. | Scan starts; nearby HRMs appear in a list within ~5 s, each showing display name + signal strength. |
| B3 | Tap your HRM. | Status flips to **Connected** within ~3 s. The Options screen shows the device name and "Connected". |
| B4 | Press back to selection, tap a workout, observe Workout Run page even before pressing Start. | HR value shows a live BPM, updating at least once per second. |
| B5 | Press **Start**. Move the HRM out of range (e.g., walk to another room) for 30 s, then return. | While out of range, the BPM cell shows `— (signal lost)`; the workout continues running (timer + beeps unaffected). When the HRM returns, BPM resumes within 10 s. |
| B6 | Toggle Bluetooth **off** at the system level during the workout. | The workout continues running. BPM cell shows `— (Bluetooth off)`. No crash. |
| B7 | Toggle Bluetooth back on, then press **Stop**. | Returns to selection. Reopen Options → HRM is still listed; tap **Disconnect** → status "Not connected". |
| B8 | (Permission denial path.) Long-press the app icon → **App info → Permissions → Nearby devices → Deny**. Re-open Options → **Connect HR monitor**. | A clear non-crashing message explains the feature is disabled and points to system settings. All other features (import, run a workout without HR) still work. |

### Recipe C — Immersive fullscreen, keep-screen-on, beeps, and mini view

**Precondition**: app installed; the device's media volume is **not** muted
(beeps play through the media stream); at least one workout is in storage;
an HRM may or may not be connected.

| # | Step | Expected |
|---|---|---|
| C1 | Launch app → Selection → tap a workout → Workout Run page. | The status bar and navigation bar are hidden (immersive). Brightness time-out is disabled (screen stays on). |
| C2 | Press **Start**. Wait until the first step has less than 5 seconds remaining (use a short warmup workout; bundled `2026-05-21_FitnessTes.fit` has 30-second steps). | Exactly 5 short beeps fire at 1-second intervals; the 5th coincides (within ±100 ms) with the visible step transition. |
| C3 | Press **Pause**. | All counters freeze. No further beeps. |
| C4 | Press **Start**. | Counters resume from where they stopped. Beeps reschedule correctly if > 5 s remain in the current step. |
| C5 | Tap **Step Forward**. | Current step ends immediately; next step starts at t=0. Pending beeps for the skipped step do not fire. **Step Backward** becomes enabled. |
| C6 | Tap **Step Backward**. | Returns to the previously skipped step at the elapsed time it had at the moment of the skip. **Step Backward** becomes disabled. |
| C7 | While a workout is running, tap **Mini View**. First time only: the app shows a rationale page, then opens the Android system-overlay permission page. Toggle the permission on, then return. | The mini view appears as a floating window. It shows: current step elapsed, current step remaining, current target, next target (all using the current pace/speed display setting). |
| C8 | Press the system Home key. | The mini view remains visible on top of the launcher. Counters keep advancing. Beeps still fire correctly. |
| C9 | Open a music app and start music playback. Wait for the next 5-beep countdown. | Music continues; the beeps play *through* the music (transient duck) without stopping it. |
| C10 | Open a messaging app and start typing in a text field. | The soft keyboard does not get blocked by the mini view (it repositions if needed). |
| C11 | Tap the mini view. | This app returns to the foreground, on the Workout Run page; the mini view is dismissed. |
| C12 | Let the workout run all the way to natural completion. | The completion summary card appears: workout name, planned vs actual time, average HR if HRM was connected ≥ 50% of the session (otherwise omitted), planned TSS. Tap anywhere → returns to selection. |
| C13 | (Stop-mid-workout path.) Start another workout. Press **Stop** mid-step. | Returns to selection immediately. **No** completion card is shown (per FR-022). |

### Recipe D — TSS and threshold pace

| # | Step | Expected |
|---|---|---|
| D1 | Fresh install, no threshold pace set. Import any `.fit`. | Training Details shows TSS = `—`. Selection list also shows `—`. |
| D2 | Options → Threshold pace → enter `4:00` → Save. | Returns to Options showing `Threshold pace: 4:00/km`. |
| D3 | Re-open Training Details for the imported workout. | TSS shows a numeric value > 0 within ~1 s; the value is stable across re-opens and across app cold-restarts. |
| D4 | Options → Threshold pace → clear → Save. | Returns to "Unset". Training Details TSS goes back to `—`. |

## 4. Regression policy

- Before merging any change that could plausibly affect the three flows
  (audio, BLE, overlay, immersive, FIT parsing), the developer MUST run
  Recipes A, B, and C on the USB-connected device. This is the canonical
  verification gate per Constitution §V.
- A failed recipe step is a release blocker, not a "fix later" item.
- The bundled `TrainingPeaksData/2026-05-21_FitnessTes.fit` and
  `2026-05-27_NoLetUp.fit` are the reference workouts; if FIT parsing
  changes, they must still import and run identically.

## 5. Local URLs to hit / commands to rerun after a change

There is no local URL — this is a phone-resident app. The smallest "test
this change" loop is:

```powershell
# in workspace root, VS Code integrated terminal
.\gradlew.bat installDebug                                  # build + install
adb shell am start -n com.example.runtraining/.MainActivity  # launch
adb logcat -v color RunTraining:V *:S                       # watch logs in a separate terminal
```

After a code change in `app/`, just rerun `installDebug`; Gradle's
incremental build keeps subsequent rebuilds fast (~5–20 s for a small
change).
