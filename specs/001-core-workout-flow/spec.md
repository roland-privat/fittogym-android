# Feature Specification: Core Workout Flow

**Feature Branch**: `001-core-workout-flow`

**Created**: 2026-05-25

**Status**: Draft

**Input**: User description: "when opening a fit file, if it is shared with the app or stored somewhere on the device, the training is imported in the app's storage. On first open (not from app storage) the training details page is opened. There the training has the following information calculated/stored: duration of the training overall, distance of the training overall, date imported, filename becomes name, the TSS (Training Stress Score) which needs to be calculated. Name is editable. When the app is started without being called because of a file being opened, it should go to the training selection page. There a user can select the training they want to perform. Once selected we go into the default training page, where we have: a graphical representation of the workout, start/stop/pause, mini view (always on top of other apps with minimal info), key metrics like time, time till next change, current targets and next targets, overall time left, overall time done, heart rate, lap count for repeats (like 4 of 6); there should be a clear indication where we are on the timeline (graphical representation); the mini view should show current lap time, time left of lap, current target speed, next target speed; there should be a 'connect HR monitor' option in the options"

## Clarifications

### Session 2026-05-25

- Q: How should the app signal step transitions during a running workout? → A: Five short countdown beeps, one per second, in the five seconds leading up to each step transition (no voice, no separate vibration requirement).
- Q: How should target intensity be displayed — pace, speed, or both? → A: User-selectable in Options between pace (min:ss/km) and speed (km/h); the chosen unit applies to both the workout run page and the mini view. Default is pace.
- Q: How should distance-based workout steps be handled during execution? → A: At import, derive an effective duration as `distance / pace_midpoint` (using the step’s own planned pace target) and run the step as time-based of that effective duration. If a distance-based step has no pace target, treat it as “open” (un-runnable, contributes zero to overall metrics).
- Q: Should the workout run page include a manual “Next step” / skip control in v1? → A: Yes — add **Step Forward** (always available while running or paused) **and** **Step Backward**, where Step Backward is only enabled when the current step was reached via a previous Step Forward (i.e., it undoes a manual skip and is not free backward navigation).
- Q: What should the workout-completion screen show? → A: On natural completion only (not on **Stop**), show a short summary card with the workout display name, planned-duration vs actual-elapsed time, average heart rate over the session **if** an HRM was connected for at least half of the workout, and the workout’s planned TSS. A single **Done** affordance (or tap anywhere) returns to the training selection page. Pressing **Stop** before natural completion does **not** show this card and behaves exactly as FR-022 (immediate return to selection page).
- Q: What additional per-step identifiers should the main (large) workout run page show? → A: In addition to the live metrics already in FR-017, the workout run page MUST also continuously display (a) the **step number** in the form `step n of N` (where `N` is the total number of steps in the workout after repeat expansion is *not* applied — i.e., `N` counts each authored step once, not each repeat iteration), (b) the **step name** taken from the FIT workout-step name field (or `—` if the step has no name), and (c) the **intensity class** (warmup / active / rest / cooldown / other). These are explicitly part of the *large* workout run page only; the mini view (FR-030) is unchanged. The repeat-lap counter `n of N` from FR-018 remains separate and is only shown for steps inside a repeat block.

## User Scenarios & Testing

### User Story 1 - Import a workout from a shared/opened FIT file (Priority: P1)

The user has a `.fit` workout file delivered through another app (e.g., WhatsApp, Files, Drive) or stored somewhere on the device. They use Android's share/open flow to hand the file to this app. The app imports the workout into its own private storage, computes summary information from the planned steps, and shows the **training details page** so the user can review and (optionally) rename it before it joins the local workout library.

**Why this priority**: Without this flow there is nothing to run later. It is the entry point for every other user story and the only way data enters the app.

**Independent Test**: From any other Android app, share a `.fit` workout file to this app, or tap a `.fit` file in a file manager and choose this app. The training details page MUST open showing the four computed/derived fields (overall duration, overall distance, date imported, TSS) plus an editable name pre-filled from the source filename. After saving, the workout MUST appear in the training selection list on subsequent app launches, and the source file MUST no longer be required to be present.

**Acceptance Scenarios**:

1. **Given** the app is not running and a `.fit` file is shared from another app, **When** the user selects this app as the share target, **Then** the app launches directly into the training details page for that file with all summary fields populated and the name field pre-filled (and focused for editing) from the filename without its extension.
2. **Given** the training details page is shown for a freshly imported workout, **When** the user edits the name and confirms, **Then** the new name is persisted as the workout's display name and the original filename is retained internally for reference.
3. **Given** a `.fit` file has been imported, **When** the user later closes and reopens the app cold (not from a share intent), **Then** the workout appears in the training selection list with the (possibly edited) name, the same overall duration, distance, date imported, and TSS as shown on the details page.
4. **Given** the same `.fit` file (by content hash) is shared a second time, **When** import runs, **Then** the app MUST NOT create a duplicate entry; it MUST open the details page for the existing workout and indicate that this workout was already imported.

---

### User Story 2 - Pick and execute a workout with live timeline and controls (Priority: P1)

When the user launches the app normally (no file intent), they land on the **training selection page**. They pick one previously imported workout. The app opens the **workout run page** showing a horizontal graphical representation of the entire workout (bar chart of step intensities, see the reference image in the input), a clear playhead marking the current position, key live metrics (time in current step, time until next step, current target pace, next target pace, overall time elapsed, overall time remaining, lap count `n of N` for steps inside a repeat block), and start / pause / stop controls. The user can run the planned workout to completion.

**Why this priority**: This is the entire reason the app exists. Without it, imports go nowhere.

**Independent Test**: With at least one workout already in app storage, launch the app cold. The training selection page MUST appear. Tap a workout. The workout run page MUST open with the graphical workout chart, playhead at time zero, all key metrics visible, and the controls in an idle state. Tap start: the timeline playhead MUST advance in real time, current-step and overall counters MUST update at least once per second, and step transitions MUST be visually obvious. Tap pause: counters stop and resume on next start. Tap **Step Forward**: the current step ends immediately, the next step begins, and **Step Backward** becomes enabled until either the next natural transition or until Step Backward is used. Tap stop: the workout ends and the user returns to the selection page (no recorded activity is required). The user MUST be able to complete a full workout end-to-end on the device.

**Acceptance Scenarios**:

1. **Given** the app is launched cold (no file intent), **When** the home screen appears, **Then** it is the training selection page listing all workouts in app storage, sorted with most recently imported first, each showing at minimum name, overall duration, and TSS.
2. **Given** the user is on the training selection page and at least one workout exists, **When** the user taps a workout, **Then** the workout run page opens with the timeline chart rendered for the entire workout, a playhead positioned at the start, and all controls and live metrics in an idle/zeroed state.
3. **Given** the workout run page is in idle state, **When** the user taps **start**, **Then** the playhead begins moving in real time along the timeline; "time in current step", "time until next step", "overall time elapsed", and "overall time remaining" update continuously; "current target" shows the active step's target pace range; "next target" shows the next step's target pace range (or "—" if this is the final step).
4. **Given** the workout is running inside a repeat block, **When** a step in that block is active, **Then** a lap counter MUST be visible in the form `n of N` (e.g., `4 of 6`) indicating progress through the repeat. For steps outside a repeat, the lap counter MUST be hidden or show a dash.
5. **Given** the workout is running, **When** the user taps **pause**, **Then** the playhead and all time counters stop advancing while still displaying their current values; **When** the user taps **start** again, **Then** counters resume from where they left off, without skipping.
6. **Given** the workout is running or paused, **When** the user taps **stop**, **Then** the workout ends, the user returns to the training selection page, and any "next time you run this workout" state starts from the beginning.
7. **Given** the workout run page is open with the screen on, **When** no user input occurs for the duration of the workout, **Then** the screen MUST remain on for the full duration and the system status/navigation bars MUST be hidden (immersive fullscreen).
8. **Given** the workout reaches the end of its final step naturally (not stopped), **When** the last step completes, **Then** the app MUST show the workout-completion summary card defined in FR-022a (workout name, planned-duration vs actual-elapsed time, average heart rate if HRM was connected ≥ 50% of the session, planned TSS), and a single **Done** affordance (or tap anywhere) MUST return the user to the training selection page.
9. **Given** the workout is running and a step transition is approaching, **When** the current step has five seconds or less remaining, **Then** the app MUST emit one short audible beep per remaining second (five beeps total: at T-5, T-4, T-3, T-2, T-1 seconds before the next step starts), so the runner gets a hands-free count-in to the next step.
10. **Given** the workout is running on step 3 of N and Step Backward is disabled, **When** the user taps **Step Forward**, **Then** step 3 ends immediately, step 4 starts from its beginning, any pending countdown beeps for step 3 are cancelled, and **Step Backward** becomes enabled; **When** the user then taps **Step Backward**, **Then** the run returns to step 3 at exactly the elapsed time step 3 had when Step Forward was pressed and Step Backward becomes disabled again.
11. **Given** the workout is running and Step Forward has just been used (so Step Backward is enabled), **When** the new current step ends naturally via its timer, **Then** Step Backward MUST become disabled before the next step starts — there is no free backward navigation across natural transitions.
12. **Given** the workout is on its final step, **When** the user inspects the controls, **Then** **Step Forward** MUST be disabled (the only way to end the final step is timer completion or **Stop**).
13. **Given** a workout has 7 authored steps (one of which is a repeat block of 2 steps with repeat count 6) and the runner is currently on the 5th authored step, named `Hard` with intensity class `active`, on the 3rd iteration of the repeat, **When** the user looks at the workout run page, **Then** the screen MUST show all of: `step 5 of 7` (per FR-017/FR-017a, repeat is *not* expanded into `N`), step name `Hard`, intensity badge `active`, **and** the repeat-lap counter `3 of 6` (per FR-018); these are two distinct on-screen indicators and MUST NOT be conflated.

---

### User Story 3 - Connect a broadcast heart rate monitor and see live HR (Priority: P2)

From an **Options** screen accessible from the training selection page (and/or from the workout run page), the user picks "Connect HR monitor" and pairs with a nearby broadcasting heart rate monitor. Once connected, the live heart rate value is shown among the key metrics on the workout run page during a workout, and survives backgrounding/foregrounding within a workout session.

**Why this priority**: Workouts work without it (time-based pace targets are usable as-is), but the user explicitly asked for HR display and it is a small, isolated addition.

**Independent Test**: With a broadcasting HRM nearby, open Options → "Connect HR monitor". The app MUST scan, list nearby HRMs, allow the user to pick one, confirm connection, and then return to the previous screen. On the workout run page (even with no workout started), the live HR value MUST update at least once per second. If the HRM goes out of range, the HR value MUST show a clear "lost signal" indicator without crashing. The user MUST be able to disconnect or pick a different HRM later from the same Options screen.

**Acceptance Scenarios**:

1. **Given** no HRM has ever been connected, **When** the user opens Options → "Connect HR monitor", **Then** the app prompts for any Bluetooth runtime permissions it does not yet have, then scans and lists nearby broadcast HRMs by display name.
2. **Given** the user selects an HRM from the scan list, **When** pairing succeeds, **Then** the app stores the device identifier for auto-reconnect and shows the device name and "connected" status in Options.
3. **Given** an HRM is connected, **When** the user is on the workout run page (workout running or not), **Then** a live heart rate value (BPM) is displayed among the key metrics and updates at least once per second.
4. **Given** an HRM is connected and a workout is running, **When** the HRM goes out of range or its battery dies, **Then** the HR value indicates "lost signal" and the workout MUST continue running without crashing; **When** the HRM returns, **Then** HR updates resume automatically.
5. **Given** Bluetooth is turned off at the system level, **When** the user opens Options → "Connect HR monitor", **Then** the app MUST surface a clear non-crashing message telling the user to enable Bluetooth, and any workout in progress MUST continue.
6. **Given** the user denies the runtime Bluetooth permission, **When** the user returns to Options, **Then** the HR feature is clearly disabled with an explanation, and all other features (import, selection, execution without HR) MUST continue to work.

---

### User Story 4 - Always-on-top mini view during a workout (Priority: P2)

While a workout is running, the user can enable a **mini view** — a small floating overlay that stays visible on top of other apps. It shows the current step's elapsed time ("current lap time"), the time remaining in the current step ("time left of lap"), the current target speed/pace, and the next target speed/pace. The user can switch to another app (e.g., a music app) and still see those four values.

**Why this priority**: Adds polish and lets the user use the phone for music or messages mid-workout, but the workout is fully usable without it.

**Independent Test**: With a workout running, tap the "Mini view" toggle in the workout run page. The app MUST request the Android system-overlay permission if not already granted. Once granted and the toggle is on, switch to a different app (e.g., the home screen or any music app). A small floating window MUST remain visible on top showing the four required values, all updating live as the workout progresses. Tapping the mini view (or a close handle on it) MUST bring the app back to the foreground and/or dismiss the overlay. Stopping the workout MUST also dismiss the overlay.

**Acceptance Scenarios**:

1. **Given** the user is on the workout run page and a workout is running, **When** the user taps "Mini view" for the first time, **Then** the app explains why the system-overlay permission is needed and routes the user to the Android settings page to grant it; on return, the overlay appears.
2. **Given** the overlay permission is granted and the mini view is enabled, **When** the user switches to another app, **Then** the floating mini view remains visible and continuously displays: current lap (step) elapsed time, current lap (step) time remaining, current target pace, next target pace.
3. **Given** the mini view is visible, **When** a step transition occurs, **Then** the four displayed values update accordingly within one second of the transition.
4. **Given** the mini view is visible, **When** the user taps it, **Then** this app returns to the foreground on the workout run page.
5. **Given** the mini view is enabled, **When** the user stops or completes the workout, **Then** the mini view is automatically dismissed.
6. **Given** the user denies the system-overlay permission, **When** they return to the workout run page, **Then** the mini view toggle is clearly disabled with an explanation, and the rest of the workout flow continues to work normally.

---

### Edge Cases

- **Shared file is not a valid FIT workout** (random binary, truncated, wrong sport type, recorded activity instead of planned workout): import MUST fail with a clear visible error and the file MUST NOT enter app storage. The app MUST NOT crash.
- **FIT workout has open-duration or distance-based steps**: overall duration, overall distance, and TSS are computed only from steps where the planned target makes those computations meaningful. Distance-based steps with a usable pace target are converted to an effective time-based duration at import per FR-004a and contribute normally. Steps that ultimately contribute "unknown" (no usable pace, or open with no other target) MUST be shown as such on the details page rather than silently treated as zero.
- **Threshold pace required for TSS is not set**: TSS on the details page and selection list MUST show "—" instead of a misleading numeric value, and the rest of the import flow MUST proceed normally.
- **App killed by Android while a workout is running**: when relaunched, the user lands on the training selection page (no automatic resume in v1). The previously running workout simply ended.
- **Phone rotated, multi-window, or split-screen** during a workout: the workout MUST continue running and the timeline + counters MUST continue updating. Visual layout adjustments are acceptable; data loss is not.
- **Phone screen turns off** (e.g., user manually) during a workout: counters MUST continue running; when the screen is unlocked, the workout run page MUST still reflect correct elapsed/remaining values.
- **User shares a workout file** while a workout is already running: the in-progress workout MUST be preserved. The details page for the new import MAY open on top, and dismissing it MUST return the user to the running workout.
- **HRM connected but workout not started**: the HR value still shows on the workout run page (idle state).
- **Mini view overlay collides with the system keyboard** in another app: the overlay MAY be repositioned but MUST NOT block keyboard input from the underlying app.
- **Workout with zero steps** or with only "open" steps: the workout is still importable but cannot be run; the run button MUST be disabled with a tooltip explanation.

## Requirements

### Functional Requirements

**Import & storage**

- **FR-001**: The app MUST register itself as a receiver for Android `ACTION_SEND` and `ACTION_VIEW` intents carrying `.fit` files (and the corresponding MIME types where browsers/file pickers use them).
- **FR-002**: When the app is invoked via a file intent with a `.fit` payload, it MUST copy the file's bytes into app-private storage before any further processing and MUST NOT rely on the source `Uri` after the initial read.
- **FR-003**: The app MUST detect duplicate imports by content hash and MUST NOT create duplicate library entries; opening a duplicate MUST resolve to the existing workout's details page with a clear "already imported" indication.
- **FR-004**: For each imported workout the app MUST persist, at a minimum: a stable internal ID, the original filename, the user-editable display name (initially derived from the filename without extension), the import timestamp, the computed overall planned duration, the computed overall planned distance, and the computed TSS (or an explicit "unknown" marker when not computable).
- **FR-004a**: For each **distance-based step** (a step whose source FIT message defines its length by distance rather than duration), the app MUST, at import time, derive an **effective duration** from `distance / pace_midpoint` using the step’s own planned target pace, persist that effective duration alongside the original distance value, and treat the step as time-based of that effective duration for all run-page behavior (playhead, counters, transitions, beeps). If a distance-based step has no usable pace target, its effective duration MUST be marked **“open”**: the step contributes zero to overall planned duration / distance / TSS and the workout is treated like any other workout containing an “open” step per the edge case below.
- **FR-005**: The training details page MUST allow the user to edit the display name, and the new value MUST persist immediately on confirmation; cancelling MUST leave the name unchanged.
- **FR-006**: The app MUST function entirely without network access. No imported workout, computed metric, or HR sample is permitted to leave the device.

**App entry routing**

- **FR-007**: When launched from a `.fit` file intent for a workout not yet in app storage, the app MUST open directly on the training details page for the freshly imported workout.
- **FR-008**: When launched from a `.fit` file intent for a workout that already exists in app storage (duplicate by hash), the app MUST open the training details page of the existing workout.
- **FR-009**: When launched without a file intent (cold start from the launcher), the app MUST open the training selection page.

**Training selection page**

- **FR-010**: The training selection page MUST list every workout currently in app storage, sorted with the most recently imported first.
- **FR-011**: Each row MUST display at minimum the display name, the overall planned duration, and the TSS (or "—" if unknown).
- **FR-012**: Tapping a row MUST open the workout run page for that workout, **except** when the workout is not runnable (zero authored steps, or all steps effectively “open” per FR-004a) — in that case the tap MUST surface a visible non-crashing explanation that the workout has no runnable steps and MUST NOT navigate to the workout run page (per the Edge Case “Workout with zero steps or with only ‘open’ steps”). Long-press or a row affordance SHOULD allow opening the training details page (read/edit) and deleting the workout from app storage.
- **FR-013**: The training selection page MUST provide visible entry points to an **Options** screen that contains at minimum the "Connect HR monitor" action (FR-024), the threshold-pace setting used for TSS computation (referenced in Assumptions), and the **target-intensity display unit** toggle (pace vs speed, FR-013a).
- **FR-013a**: The Options screen MUST expose a single user-selectable target-intensity display unit with two values: **pace** (formatted as `min:ss/km`, default) and **speed** (formatted as `km/h`). The selected unit MUST apply to every place a target is shown to the user — current and next target on the workout run page, current and next target on the mini view, and target column on the training details page. Underlying workout storage MUST remain in pace (seconds per km) regardless of display setting; the unit conversion is presentation-only.

**Workout run page — graphical timeline**

- **FR-014**: The workout run page MUST render a horizontal graphical representation of the entire workout where each step is a rectangle whose **width** is proportional to that step's planned duration and whose **height/style** clearly communicates that step's intensity class (e.g., warmup / active / rest / cooldown) and target pace zone. The visual style matches the reference image included in the original feature input — a left-to-right strip of solid blue rectangles of varying heights (height ≈ step intensity), each rectangle topped by a thin slightly-darker stripe (the target zone band), with a short cluster of narrow tall stripes near the left end indicating warm-up strides / short bursts before the main set. Steps in a repeat block are visually grouped (see FR-016).
- **FR-015**: A clearly visible playhead MUST mark the current position along the timeline, and MUST advance smoothly (at least once per second) while the workout is running.
- **FR-016**: Step boundaries MUST be visually distinct on the timeline. Steps that belong to a repeat block SHOULD be visually grouped to make the repeat structure recognizable.

**Workout run page — live key metrics**

- **FR-017**: The workout run page MUST continuously display, while a workout is running or paused, all of the following metrics: time elapsed in the current step, time remaining in the current step ("time till next change"), current target pace range, next target pace range (or "—" if final step), overall time elapsed, overall time remaining, live heart rate (or "—" when no HRM is connected or signal is lost), the **step number** as `step n of N` (per FR-017a), the **step name** (per FR-017a), and the **intensity class** of the current step (warmup / active / rest / cooldown / other).
- **FR-017a**: The step number `n` MUST refer to the current step’s ordinal position in the authored step list (1-based). The total `N` MUST be the count of authored steps in the workout, **without** expanding repeat blocks (each authored step is counted once, regardless of repeat count). This makes `step n of N` an unambiguous “position in the workout definition” indicator and keeps it distinct from the repeat-lap counter in FR-018, which counts iterations *within* a repeat block. The step name MUST be taken verbatim from the FIT workout-step name field; if that field is empty or absent, the workout run page MUST render the step name as `—`. The intensity class MUST be rendered using a clear visual treatment (e.g., a badge with the class label and/or a color matching the timeline rectangle’s style from FR-014) so that the runtime intensity class and the timeline’s color coding agree at a glance.
- **FR-018**: When the currently active step is part of a repeat block, the workout run page MUST display a lap counter in the form `n of N` (e.g., `4 of 6`). For steps not in a repeat block, the lap counter MUST be hidden or rendered as a dash.

**Workout run page — controls**

- **FR-019**: The workout run page MUST provide **Start**, **Pause**, **Stop**, **Step Forward**, and **Step Backward** controls whose enabled/disabled states match the current run state (idle, running, paused, complete) per FR-019a and FR-019b.
- **FR-019a**: **Step Forward** MUST be available whenever the workout is **running** or **paused** and the current step is not the final step. Pressing it MUST end the current step immediately and start the next step from its beginning. Time accounting MUST follow normal step-transition logic (overall time elapsed and remaining recompute to reflect the early transition; the playhead jumps to the next step’s start; the countdown beeps for the skipped step are cancelled). If the current step is the final step, Step Forward MUST be disabled; reaching the end this way is equivalent to natural completion (Acceptance Scenario 8) and triggers the “workout complete” state.
- **FR-019b**: **Step Backward** MUST be enabled **only** when the **most recent** step transition was caused by Step Forward (i.e., it is an explicit undo of a manual skip). It MUST NOT be available as free backward navigation. Pressing it MUST return the user to the previously skipped step at the position they left it (the time-elapsed value the skipped step had at the moment Step Forward was pressed). Any natural (timer-driven) step transition, any Step Backward press, or any Stop press MUST clear the “undoable” flag and disable Step Backward until a new Step Forward occurs.
- **FR-020**: While running, all displayed time counters MUST update at least once per second.
- **FR-021**: Pause MUST freeze all time counters and the playhead; subsequent Start MUST resume them from their paused values with no skipped time.
- **FR-022**: Stop MUST end the workout immediately and return the user to the training selection page; no recorded activity output is required in this version. The workout-completion summary card (FR-022a) is **not** shown on **Stop** — it is shown only on natural completion.
- **FR-022a**: On natural completion of the final step (i.e., the last step’s timer reaches zero, **not** when the user presses Stop), the workout run page MUST present a workout-completion summary card showing all of the following: (a) the workout display name, (b) planned overall duration vs the actual elapsed session time (paused time excluded), (c) average heart rate over the session **if and only if** an HRM was connected and producing samples for at least 50% of the elapsed session time — otherwise omit the HR row, and (d) the workout’s already-computed planned TSS (or `—` if TSS is unknown per FR-004). The card MUST be dismissible by tapping a single **Done** affordance or anywhere on the card, after which the user returns to the training selection page. No new persistent data is created.
- **FR-023**: While the workout run page is visible **and** a workout is running, the app MUST keep the screen on and present an immersive fullscreen layout (status bar and navigation bar hidden). On other screens (selection page, details page, options), normal screen behavior MUST apply.

**Workout run page — audio cues**

- **FR-023a**: While a workout is running (not paused, not idle, not complete), the app MUST emit exactly five short countdown beeps timed at one-per-second in the final five seconds of every step, so the last beep coincides (within ±100 ms) with the transition to the next step. No beeps are emitted when the workout is paused, when the current step has "open" duration, or after the final step ends.
- **FR-023b**: The countdown beeps MUST cooperate with other audio (e.g., a music app the user is playing) by requesting transient audio focus for the duration of each beep and releasing it immediately; the beeps MUST NOT stop or permanently duck the user's music. No voice/TTS output and no separate vibration is required by this version.
- **FR-023c**: The beeps MUST play even when the screen is off, when the app is backgrounded (e.g., mini view in use, music app foreground), and when the device is in silent ringer mode — i.e., they use the *media* audio stream so the workout's audible cues are tied to media volume, not ringer mute.

**Heart rate monitor**

- **FR-024**: The Options screen MUST offer a "Connect HR monitor" action that scans for nearby broadcast heart rate monitors, lets the user pick one, and stores the chosen device for auto-reconnect on subsequent app launches.
- **FR-025**: The app MUST request only the Bluetooth runtime permissions strictly needed to scan for and connect to a broadcast heart rate monitor, and MUST NOT request location permissions unless the chosen device target genuinely requires it.
- **FR-026**: A connected HRM's live heart rate value MUST be exposed to the workout run page (live metric in FR-017) and MUST tolerate transient signal loss and Bluetooth-off events without crashing.
- **FR-027**: The user MUST be able to disconnect or replace the connected HRM from the same Options screen.

**Mini view (always-on-top overlay)**

- **FR-028**: The workout run page MUST provide a toggle that enables/disables a mini view overlay while a workout is running.
- **FR-029**: When the mini view is enabled, the app MUST request the Android system-overlay capability if not already granted, and MUST clearly explain why it is needed before routing the user to the corresponding system settings.
- **FR-030**: The mini view MUST display, at minimum, the current step elapsed time, the current step time remaining, the current target pace, and the next target pace, and MUST update those values within one second of any change.
- **FR-031**: The mini view MUST remain visible on top of other apps when the user backgrounds this app, and MUST be automatically dismissed when the workout stops or completes.
- **FR-032**: Tapping the mini view MUST bring this app's workout run page back to the foreground.

**Resilience & graceful degradation**

- **FR-033**: Every external-touchpoint feature (HRM, FIT parsing, overlay permission, file intent) MUST present a visible non-crashing state on its predictable failure modes (permission denied, Bluetooth off, no HRM, malformed FIT, oversized FIT, denied overlay permission, source `Uri` revoked).
- **FR-034**: The app MUST never declare or use the `INTERNET` permission.

### Key Entities

- **Workout**: A planned running workout imported from a `.fit` file. Attributes: stable internal ID, content hash, original filename, user-editable display name, import timestamp, overall planned duration, overall planned distance, computed TSS (or "unknown"), ordered list of steps.
- **Step**: One segment of a workout. Attributes: 1-based ordinal index within the authored step list, optional name taken from the FIT workout-step name field, intensity class (warmup / active / rest / cooldown / other), duration (or "open"), distance target (or "open"), target pace range (lower–upper, or "open"), optional zone label, and (for steps inside a repeat) the repeat group it belongs to plus its position within that group.
- **Repeat Group**: A bracket around two or more contiguous steps with a repeat count `N`. Drives the `n of N` lap counter on the workout run page.
- **Run Session** (transient, in-memory only): The live state of a workout in progress — current step index, current repeat iteration, time elapsed in current step, time elapsed overall, run state (idle/running/paused/complete), and current heart rate sample. Not persisted across app process death in this version.
- **HR Monitor Pairing**: Persisted pairing record: device identifier, last-known display name, last-connected timestamp.
- **App Settings**: User-editable settings stored in app-private storage, including at minimum: (a) the **threshold pace** (in sec/km) used as the basis for TSS computation, and (b) the **target-intensity display unit** (`pace` | `speed`, default `pace`) per FR-013a. Used by the import flow (TSS) and by every UI surface that renders a target intensity.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A workout shared from another Android app reaches the training details page in under 5 seconds end-to-end on the developer's USB-connected phone.
- **SC-002**: 100% of `.fit` files that have been successfully imported are visible in the training selection page after a cold restart of the app.
- **SC-003**: Tapping a workout in the selection page opens the workout run page with the timeline fully rendered in under 1 second.
- **SC-004**: During a running workout, the playhead and all time counters update at least once per second with drift of no more than 1 second per 30 minutes of elapsed wall-clock time.
- **SC-005**: When the workout run page is active and a workout is running, the device screen does not auto-lock for at least 60 minutes of continuous workout time.
- **SC-006**: With a broadcast HRM in range, the live heart rate value updates at least once per second and recovers automatically within 10 seconds of the HRM coming back into range after a signal loss.
- **SC-007**: The mini view remains visible on top of at least three different commonly used apps (e.g., a music app, a messaging app, the home screen) without being clipped or dismissed.
- **SC-008**: Importing the same `.fit` file a second time creates zero duplicate library entries.
- **SC-009**: Sharing any of the malformed FIT inputs documented in the manual-test recipes (truncated file, wrong sport, recorded activity, empty steps list) produces a visible error and zero crashes.
- **SC-010**: The total user-facing interaction time to "open the app cold → pick a workout → press Start" is at most 5 taps and under 10 seconds for a user who has at least one workout in storage.

## Assumptions

Project-wide defaults from the constitution that you can rely on without restating:

- Single user (the developer) on a USB-connected Android phone.
- No network, telemetry, accounts, or Play Store distribution.
- Single Android Gradle module (`app/`), Kotlin + Jetpack Compose.
- Manual test recipes are the canonical verification gate for HRM, FIT share/open intents, and fullscreen behavior.

Feature-specific assumptions (reasonable defaults adopted because the input did not specify):

- **Workout sport**: Only running workouts are in scope for this version. Other FIT sports (cycling, swimming, multisport) are rejected at import with a visible error.
- **Pace, not power**: Targets are pace ranges (min/max sec per km). Power-based targets are out of scope for this version even if present in the FIT file. Display of those targets to the user is user-selectable between pace (`min:ss/km`, default) and speed (`km/h`) per FR-013a; the underlying stored representation remains pace.
- **Threshold pace source**: TSS computation relies on a single app-level "threshold pace" stored in App Settings. If the user has not set it, TSS is shown as "—" and the rest of the import flow proceeds normally. (No threshold value is shipped as a default — leaving it unset is a valid state.)
- **TSS definition**: A running-style Training Stress Score computed deterministically from each step's planned duration and the midpoint of its target pace range relative to the user's threshold pace. Open-duration steps (and distance-based steps with no usable pace target, per FR-004a) contribute zero; distance-based steps that *do* have a usable pace target contribute via their effective duration (`distance / pace_midpoint`) just like any other time-based step. The exact formula is an implementation concern and is not part of this spec, but the result MUST be stable for the same input.
- **Distance computation**: For time-based steps with a pace target, planned distance contribution = step duration × midpoint of pace range. Distance-based steps contribute their distance directly **and** their effective duration (= distance / pace midpoint) per FR-004a so the rest of the engine treats them as time-based. Open steps and distance-based steps with no usable pace contribute zero. The overall workout distance is the sum.
- **Mini view trigger**: The mini view is opt-in via a toggle on the workout run page; it does not appear automatically when the user switches apps.
- **Activity recording**: The app does not record an output activity file (no `.fit` write-back, no GPS track, no recorded HR stream). The workout run page is purely a guided execution view in this version.
- **HRM protocol**: Only Bluetooth LE broadcast/standard Heart Rate Service is in scope. ANT+ is out of scope for this version.
- **Storage location**: All imported workouts and app settings live in app-private storage; they are removed if the user clears app data from Android settings, and that is acceptable.
- **No workout resume after process death**: If the app is killed while a workout is running, on next launch the user lands on the training selection page; no auto-resume is required.
- **Workout history**: Not in scope for this version. The training selection page lists available (importable/runnable) workouts only.
