# Privacy Policy — FitToGym

**Last updated:** 2026-06-11

This document describes how the **FitToGym** Android app handles your
data. The short version: it doesn't collect or transmit anything. The app
runs entirely on your device.

## What we collect

**Nothing leaves your device.** The app does not declare the `INTERNET`
permission, which means it is technically incapable of sending any data to
us or any third party.

The following information is stored *locally on your device only* and is
deleted when you uninstall the app:

| Data | Where it's stored | What we do with it |
| --- | --- | --- |
| `.fit` workout files you import | App-private internal storage (`/data/data/com.fittogym.runtraining/files/workouts/`) | Decoded to display the workout. Never read by any other app. |
| Workout metadata (name, planned duration, planned TSS) | Local SQLite database (Room) | Powers the workout library list. |
| Threshold pace, display unit, last paired HRM ID | Local DataStore preferences | Used to compute TSS and reconnect to your HR monitor. |
| Live heart-rate samples while a workout is running | RAM only — never persisted | Shown on the Run page and averaged for the completion summary. Cleared when the workout ends. |

## Permissions

The app requests the following permissions, all of which are necessary for
the local features described and none of which transmit any data
off-device:

- **`POST_NOTIFICATIONS`** — show the workout-in-progress notification.
- **`FOREGROUND_SERVICE`** + **`FOREGROUND_SERVICE_SPECIAL_USE`** — keep
  the workout timer running when the screen turns off. See `FGS-justification`
  below.
- **`SYSTEM_ALERT_WINDOW`** — *optional*. Used only by the floating "mini
  view" overlay while a workout is running, so you can see remaining time
  while you use other apps. If you decline this permission the rest of the
  app keeps working.
- **`BLUETOOTH_SCAN`** (`neverForLocation`) and **`BLUETOOTH_CONNECT`** —
  *optional*. Used only to find and stream from a Bluetooth heart-rate
  monitor. The `neverForLocation` flag declares that we never infer your
  location from BLE scans, and Android prevents us from doing so.

## Foreground Service — `specialUse` subtype

The app declares `foregroundServiceType="specialUse"` with subtype
`personal_workout_timer`. Justification, per Android 14+ requirements:

> The app is a workout timer with audible beeps that the user needs to
> hear during a structured run. The session must continue when the screen
> turns off or another app is in front. None of the more specific FGS
> types (`location`, `health`, `connectedDevice`) match — we don't track
> location, don't read body sensors, and the BLE HRM connection is
> optional and not the reason the service exists.

## Children

This app is not directed at children. No data is collected from anyone.

## Contact

For questions about this policy, file an issue on the source repository.
