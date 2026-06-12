# Play Store listing — preparation checklist

Tracks every artefact, declaration, and policy answer needed to ship the
**FitToGym** app to the Google Play Store. Use this as the source of
truth when filling out the Play Console.

> **Application ID:** `com.fittogym.runtraining` (permanent — cannot change
> after first publish). The internal code namespace remains
> `com.example.runtraining`, which is fine: Google only sees the application ID.

## Build artefact

- [ ] Generate a release keystore (one-time):

  ```pwsh
  keytool -genkeypair -v `
    -keystore release.jks `
    -keyalg RSA -keysize 4096 -validity 10000 `
    -alias fittogym `
    -dname "CN=FitToGym, OU=Personal, O=Personal, L=Unknown, S=Unknown, C=DE"
  ```

  Store `release.jks` **outside** the repo (e.g. `~/.android/fittogym-release.jks`).

- [ ] Create `keystore.properties` at the repo root (gitignored):

  ```properties
  storeFile=C:\\Users\\rnitsch\\.android\\fittogym-release.jks
  storePassword=...
  keyAlias=fittogym
  keyPassword=...
  ```

- [ ] Build a signed Android App Bundle:

  ```pwsh
  .\gradlew.bat bundleRelease
  ```

  Output: `app/build/outputs/bundle/release/app-release.aab`.

- [ ] Bump `app.versionCode` (and `app.versionName` if user-visible) in
  `gradle.properties` before every Play upload — Play enforces strictly
  increasing version codes per track.

## Store listing copy

| Field | Value |
| --- | --- |
| **App name** | FitToGym |
| **Short description (80 chars)** | Local workout player for .fit files. Beeps, BLE HRM, mini view. Offline. |
| **Full description** | (write 4000-char paid-keyword-free copy explaining: import FIT via share or open, see structured intervals, run with audible beeps, optional BLE HR monitor, floating mini view, all-offline / no account / no tracking) |
| **Category** | Health & Fitness |
| **Content rating** | Everyone — IARC questionnaire: no violence, no ads, no UGC sharing, no purchases |
| **Target audience** | 18+ (fitness apps default to adults; trim if appropriate) |
| **Contains ads** | No |
| **In-app purchases** | No |
| **Default language** | English (United Kingdom) |

## Privacy & Data safety

- [ ] **Privacy policy URL** — hosted via GitHub Pages from the `docs/`
      folder. After pushing this repo to GitHub and enabling Pages
      (Settings → Pages → Source: **GitHub Actions**), the policy is served at
      `https://<user>.github.io/<repo>/privacy-policy.html` by the
      `.github/workflows/pages.yml` workflow. Paste that URL into Play.
- [ ] **Data safety form** — declare:
      - Data collected: **None**.
      - Data shared: **None**.
      - Encryption in transit: **N/A — no network use**.
      - Data deletion: **uninstall removes everything**.
- [ ] **Account deletion** — N/A: no account, no off-device data, no
      server-side deletion endpoint required.

## Permission declarations (Play Console → App content)

Each restricted permission needs a justification. Reuse the text below.

### `SYSTEM_ALERT_WINDOW`

> Required for the optional **Mini view** floating overlay that shows the
> current workout step, remaining time, and HR while the user is in another
> app (e.g. music). The overlay is only displayed while a workout is
> actively running and is automatically dismissed when the workout ends or
> the user opens the FitToGym app. The user must explicitly grant
> permission via system Settings; declining the permission has no effect on
> the rest of the app.

### `FOREGROUND_SERVICE_SPECIAL_USE` with subtype `personal_workout_timer`

> The app is a workout timer with audible beeps and a floating mini view.
> The foreground service must continue running when the screen turns off
> or another app is in the foreground so the user can complete a
> structured run with accurate timing and audible step transitions. None
> of the more specific FGS types match: we don't track location, don't
> read body sensors directly, and the BLE HRM is optional and not the
> reason the service exists.

### `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`

> Used only to discover and stream from a Bluetooth Low Energy heart-rate
> monitor (Heart Rate Service, UUID 0x180D). `BLUETOOTH_SCAN` is declared
> with `neverForLocation` — we do not derive location from BLE scans. The
> HRM feature is optional; declining the runtime permission disables it
> without affecting the rest of the app.

## Graphic assets

- [ ] **App icon** — already in `app/src/main/res/drawable/ic_launcher_foreground.xml`
      and `mipmap-anydpi-v26/ic_launcher.xml`. Adaptive + monochrome.
      Required final PNG (512×512) for Play Console: export the foreground
      composited onto the blue background.
- [ ] **Feature graphic** (1024 × 500) — export with the timeline bars on
      brand-blue background, with the text "FitToGym".
- [ ] **Phone screenshots** (min 2, max 8, 1080 × 1920 or larger) — capture:
      1. Workout library (Last added sort)
      2. Workout details (intervals visible)
      3. Run page in portrait
      4. Mini view overlay over another app
      5. Completion summary card

## Release tracks

- [ ] **Internal testing** first — add yourself as a tester.
- [ ] After 1-2 install cycles, promote to **Closed testing (alpha)**.
- [ ] After ~14 days closed-testing minimum (Play's policy for new
      developer accounts), promote to **Production**.

## Pre-launch checks Google will run

- **Crashes / ANRs** — verify the release variant boots and reaches the
  Selection screen on each of the 5 emulator-tested devices Play uses.
- **Login** — N/A (no login).
- **Performance** — ensure cold start < 5 s.
- **Accessibility** — TalkBack at least announces buttons; verify the Run
  page is not pure-icons-only (it isn't — labels are present).

## Post-launch monitoring

- [ ] Subscribe to Play Console crash alerts.
- [ ] Decide whether to add a crash-reporting library (Firebase Crashlytics)
      *after* a few real installs. This would require adding `INTERNET`
      permission and updating the privacy policy.
