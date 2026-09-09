<#
.SYNOPSIS
    Record the FOREGROUND_SERVICE_SPECIAL_USE demonstration video for the
    Google Play permission declaration ("Foreground service permissions" >
    Special use > Other > "Provide a video link").

.DESCRIPTION
    Captures a real screen recording of FitToGym's WorkoutForegroundService in
    action, using the Android platform tool `adb screenrecord` (no extra
    installs). The recording proves the special-use foreground service keeps a
    running-workout session alive with an ongoing notification and a floating
    mini-view while the app is backgrounded.

    Follow the on-screen SHOT LIST while recording, then upload the resulting
    MP4 to YouTube (Unlisted) or Google Drive and paste that link into the
    Play Console "Video link" field.

    NOTE: adb screenrecord captures VIDEO ONLY (no audio) and records a black
    frame while the screen is physically off. This demo therefore proves
    "keeps running in the background" by (a) the persistent notification that
    updates its step/remaining time and (b) the floating mini-view overlaid on
    a different app - both visible on screen. To also capture the step-change
    beeps or a real screen-off segment, record with scrcpy instead
    (scrcpy --record demo.mp4  captures device audio on Android 11+).

.PARAMETER Install
    Build and install the debug APK before recording (.\gradlew installDebug).

.PARAMETER BootEmulator
    Boot the named AVD first if no device is connected.

.PARAMETER Avd
    AVD name to boot when -BootEmulator is set. Default: Pixel_7_API_34.

.PARAMETER Duration
    Hard time limit (seconds) passed to screenrecord. Default 180 (adb max on
    most devices). Recording also stops early when you press Enter.

.PARAMETER Output
    Output MP4 path. Default: play-assets\fgs-demo.mp4 next to this script.

.EXAMPLE
    .\play-assets\record_fgs_video.ps1 -Install

.EXAMPLE
    .\play-assets\record_fgs_video.ps1 -BootEmulator -Avd Pixel_7_API_34
#>
[CmdletBinding()]
param(
    [switch]$Install,
    [switch]$BootEmulator,
    [string]$Avd = 'Pixel_7_API_34',
    [int]$Duration = 180,
    [string]$Output
)

$ErrorActionPreference = 'Stop'
$AppId       = 'com.fittogym.runtraining'
$LaunchClass = 'com.example.runtraining.MainActivity'
$DeviceFile  = '/sdcard/fgs-demo.mp4'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = Split-Path -Parent $scriptDir
if (-not $Output) { $Output = Join-Path $scriptDir 'fgs-demo.mp4' }

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $default) { return $default }
    throw "adb not found. Add platform-tools to PATH or install the Android SDK."
}

function Get-ConnectedDevice($adb) {
    $lines = & $adb devices | Select-Object -Skip 1
    foreach ($l in $lines) {
        if ($l -match '^\s*(\S+)\s+device\s*$') { return $Matches[1] }
    }
    return $null
}

$adb = Resolve-Adb
Write-Host "adb: $adb" -ForegroundColor DarkGray

# --- Ensure a device is available ------------------------------------------
$device = Get-ConnectedDevice $adb
if (-not $device -and $BootEmulator) {
    $emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
    if (-not (Test-Path $emulator)) { throw "emulator.exe not found at $emulator" }
    Write-Host "Booting AVD '$Avd'..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $Avd, '-no-snapshot-load') | Out-Null
    & $adb wait-for-device
    Write-Host "Waiting for boot to complete..." -ForegroundColor Cyan
    do {
        Start-Sleep -Seconds 2
        $booted = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
    } while ($booted -ne '1')
    $device = Get-ConnectedDevice $adb
}

if (-not $device) {
    throw "No device/emulator connected. Plug in a phone (USB debugging on) or re-run with -BootEmulator."
}
Write-Host "device: $device" -ForegroundColor Green

# --- Optional build + install ----------------------------------------------
if ($Install) {
    Write-Host "Building + installing debug APK..." -ForegroundColor Cyan
    Push-Location $repoRoot
    try { & .\gradlew.bat installDebug }
    finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "gradlew installDebug failed ($LASTEXITCODE)." }
}

# --- Launch the app --------------------------------------------------------
Write-Host "Launching $AppId ..." -ForegroundColor Cyan
& $adb shell am start -n "$AppId/$LaunchClass" | Out-Null
Start-Sleep -Seconds 2

# --- Shot list -------------------------------------------------------------
$shots = @"

============================  SHOT LIST  ============================
 Perform these steps on the device WHILE recording. This demonstrates
 the special-use foreground service keeping the workout alive in the
 background - which is exactly what the Play declaration must show.

  1. Open a workout from the library, tap into the RUN page.
       -> The ongoing 'workout' notification appears (service is now
          in the foreground).
  2. Press START. Let the timer run through at least one STEP CHANGE
       so the current step / remaining time visibly updates.
  3. Press HOME (or open another app, e.g. Clock/Chrome).
  4. Swipe down the notification shade: show the FitToGym workout
       notification still counting down while the app is backgrounded.
  5. Re-open FitToGym briefly, enable the FLOATING MINI VIEW, press
       Home again: show the mini-view overlay counting down ON TOP of
       the other app. (Strongest proof the session stays alive.)
  6. Return to FitToGym; let the workout reach the COMPLETION summary
       (or press Stop) to show a clean end state.

 Keep it to ~30-60 s. Press ENTER here the moment you are done to stop.
====================================================================

"@
Write-Host $shots -ForegroundColor White

# --- Start recording -------------------------------------------------------
& $adb shell rm -f $DeviceFile 2>$null | Out-Null
Write-Host "Recording... (hard limit ${Duration}s)" -ForegroundColor Yellow

$recordJob = Start-Job -ScriptBlock {
    param($adbPath, $devFile, $limit)
    & $adbPath shell screenrecord --bit-rate 8000000 --time-limit $limit $devFile
} -ArgumentList $adb, $DeviceFile, $Duration

try {
    Read-Host "Press ENTER to STOP recording"
} finally {
    # SIGINT lets screenrecord finalise the MP4 header cleanly.
    & $adb shell pkill -INT screenrecord 2>$null | Out-Null
}

Write-Host "Finalising recording on device..." -ForegroundColor Cyan
Wait-Job $recordJob -Timeout 15 | Out-Null
Receive-Job $recordJob -ErrorAction SilentlyContinue | Out-Null
Remove-Job $recordJob -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2  # let the on-device file flush to disk

# --- Pull + clean up -------------------------------------------------------
if (Test-Path $Output) { Remove-Item $Output -Force }
& $adb pull $DeviceFile $Output | Out-Null
& $adb shell rm -f $DeviceFile 2>$null | Out-Null

if (Test-Path $Output) {
    $sizeKb = [math]::Round((Get-Item $Output).Length / 1KB)
    Write-Host ""
    Write-Host "Saved: $Output ($sizeKb KB)" -ForegroundColor Green
    Write-Host "Next: upload to YouTube (Unlisted) or Google Drive, then paste" -ForegroundColor Green
    Write-Host "      the link into Play Console > Foreground service permissions." -ForegroundColor Green
} else {
    throw "Recording was not pulled. Check that the demo ran and the device file existed."
}
