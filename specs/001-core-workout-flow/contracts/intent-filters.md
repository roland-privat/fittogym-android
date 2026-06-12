# Contract: Android intent filters (external input surface)

**Date**: 2026-05-25 · **Feature**: [../spec.md](../spec.md)

This is the only externally-visible contract the app exposes — the set of
Android intents and MIME / URI patterns the user (or another app) can use to
hand a workout file to us.

The contract is enforced in `app/src/main/AndroidManifest.xml` against
`MainActivity` and a `FileProvider`. Anything not listed here MUST NOT be
declared in the manifest (per Spec FR-025 "only request permissions /
intents actually used").

## Inbound intents accepted

### A. Share sheet — `ACTION_SEND` from another app

```xml
<intent-filter android:label="@string/share_fit_label">
    <action android:name="android.intent.action.SEND"/>
    <category android:name="android.intent.category.DEFAULT"/>

    <!-- Common MIMEs used by WhatsApp / Drive / file managers for .fit files. -->
    <data android:mimeType="application/vnd.ant.fit"/>
    <data android:mimeType="application/octet-stream"/>
    <data android:mimeType="application/x-binary"/>
</intent-filter>
```

**Why three MIME types**: there is no officially-registered MIME for `.fit`
running-workout files; `application/vnd.ant.fit` is what TrainingPeaks and
Garmin Connect tend to advertise, but WhatsApp and most file managers fall
back to `application/octet-stream`, and a few use `application/x-binary`.
Accepting all three keeps the share sheet from silently hiding us.

**Required runtime check** (FR-002): the app MUST verify the extracted
bytes are a valid FIT file before treating the import as successful.
Accepting `application/octet-stream` means random binaries reach us; the
FIT decoder is the gate, not the MIME type. Spec edge case "Shared file is
not a valid FIT workout" covers this.

### B. Open from a file manager — `ACTION_VIEW` with a content URI

```xml
<intent-filter android:label="@string/open_fit_label">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>

    <!-- content:// URIs from DocumentsProvider / Files app -->
    <data android:scheme="content"/>
    <data android:mimeType="application/vnd.ant.fit"/>
    <data android:mimeType="application/octet-stream"/>
</intent-filter>
```

### C. Open from local storage — `ACTION_VIEW` with a `file://` or `https://` URI by **extension**

```xml
<intent-filter android:label="@string/open_fit_label">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>

    <!-- Extension-based match for older file managers that don't supply a MIME. -->
    <data android:scheme="file"/>
    <data android:scheme="content"/>
    <data android:host="*"/>
    <data android:pathPattern=".*\\.fit"/>
    <data android:pathPattern=".*\\..*\\.fit"/>
    <data android:pathPattern=".*\\..*\\..*\\.fit"/>
    <data android:pathPattern=".*\\..*\\..*\\..*\\.fit"/>
</intent-filter>
```

**Why the funny `pathPattern` ladder**: Android's `pathPattern` doesn't
support `+`/`*` properly across `.` characters, so the common workaround is
to declare one pattern per number-of-dots in the filename. Four entries
covers `foo.fit`, `foo.bar.fit`, `2026-05-27_NoLetUp.fit` (one dash, treated
literally),
`backup.2026-05-27_NoLetUp.fit`, etc. Good enough for practical filenames.

## Outbound intents (we send)

We send exactly two:

| Intent | When | Action |
|---|---|---|
| `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` | First time the user toggles the Mini View on, if `Settings.canDrawOverlays(context) == false`. | Routes the user to the Android system-overlay permission page (FR-029). |
| Self-targeted deep link | The foreground-service ongoing notification's content intent. | `Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_RUN_PAGE)` so tapping the notification returns the user to the workout run screen. |

We never send an `ACTION_SEND` (FR-006 — no data leaves the device).

## Permissions declared

The complete list of `<uses-permission>` and `<uses-feature>` entries the
manifest may declare:

```xml
<!-- Required by use cases above; no others. -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<!-- Optional feature: degrade gracefully if BLE missing. -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false"/>
```

**Explicitly NOT declared** (Spec FR-034, FR-025):

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_*` (we
  rely on the URI permission granted by the share intent + our own
  `FileProvider`, not arbitrary file access)
- `BLUETOOTH_ADVERTISE`
- `WAKE_LOCK` (the foreground service keeps the CPU alive without it on
  API 31+)

## `FileProvider` declaration

Used so that, if a future feature ever needs to surface an imported file to
another app, we can do it without exposing internal paths. Not used by v1's
inbound flow.

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths"/>
</provider>
```

With `res/xml/file_paths.xml`:

```xml
<paths>
    <files-path name="workouts" path="workouts/"/>
</paths>
```

## Acceptance criteria (manual)

The contract is verified by the manual-test recipes in
[../quickstart.md](../quickstart.md) §3 "FIT share / open". Specifically:

1. WhatsApp → share `.fit` → this app appears in the chooser and lands on
   Training Details with all summary fields populated. (FR-007, US1 AS1.)
2. Files app → tap a `.fit` → same as above.
3. Sending the same `.fit` a second time lands on Training Details for the
   *existing* workout with the "already imported" indicator. (FR-008,
   US1 AS4.)
4. Sending a random `.bin` file with `application/octet-stream` from a file
   manager produces a visible error and no library entry. (Spec edge case
   "Shared file is not a valid FIT workout".)
