package com.example.runtraining.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.runtraining.BuildConfig
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.audio.HandlerBeepScheduler
import com.example.runtraining.audio.TonePlayer
import com.example.runtraining.ble.FakeHrSource
import com.example.runtraining.ble.HrSample
import com.example.runtraining.ble.HrmClient
import com.example.runtraining.overlay.MiniViewController
import com.example.runtraining.util.Log
import com.example.runtraining.workout.engine.RunSessionEngine
import com.example.runtraining.workout.engine.RunState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the long-running workout session. Survives the Activity being
 * backgrounded or the screen being turned off (Spec Edge Case "Phone
 * screen turns off during a workout"). Per research.md §4 the service type
 * is FOREGROUND_SERVICE_TYPE_HEALTH on API 34+.
 */
class WorkoutForegroundService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val service: WorkoutForegroundService get() = this@WorkoutForegroundService
    }

    private val binder = LocalBinder()

    // Lazy because Context is needed for TonePlayer; created on demand.
    private val tonePlayer: TonePlayer by lazy { TonePlayer(this) }
    private val beepScheduler = HandlerBeepScheduler()
    private val miniView: MiniViewController by lazy { MiniViewController(this) }
    private var miniViewEnabled: Boolean = false

    val engine: RunSessionEngine = RunSessionEngine(
        beepScheduler = beepScheduler,
        onPlayBeep = { tonePlayer.beep() },
    )

    /** Exposes HRM connection state to the Options / Run UI. */
    val hrmClient: HrmClient by lazy { (application as RunTrainingApp).container.hrmClient }

    private val fakeHrSource: FakeHrSource by lazy {
        FakeHrSource(
            scope = lifecycleScope,
            uiState = engine.uiState,
            onSample = { engine.onHrSample(it) },
            // Stay silent whenever a real strap is delivering samples, so the
            // debug build doesn't flip-flop between real and synthetic HR.
            isRealHrActive = {
                lastSampleMonotonicMs > 0L &&
                    SystemClock.elapsedRealtime() - lastSampleMonotonicMs < 3_000L
            },
        )
    }

    private var lastSampleMonotonicMs: Long = 0L
    private var hrSampleWatchdogJob: Job? = null

    private val btStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    engine.onHrSample(null)
                    hrmClient.disconnect()
                }
                BluetoothAdapter.STATE_ON -> {
                    tryAutoReconnectHrm()
                }
            }
        }
    }

    private var currentWorkoutId: Long = -1L
    private var foregroundStarted = false

    // True once the engine has entered RUNNING in this foreground session, so
    // we only leave foreground on a real return-to-IDLE (not on priming).
    private var runHasStarted = false

    // Last content posted to the ongoing notification, so we skip redundant
    // nm.notify() calls (the engine emits ~4 Hz; the text changes ~1 Hz).
    private var lastNotifiedTitle: String? = null
    private var lastNotifiedText: String? = null

    override fun onCreate() {
        super.onCreate()
        NotificationBuilder.ensureChannel(this)

        // Refresh display unit setting → engine, so target formatting on the
        // run page matches Options (FR-013a).
        val container = (application as RunTrainingApp).container
        lifecycleScope.launch {
            container.settings.settings.collectLatest { s ->
                engine.setDisplayUnit(s.displayUnit)
            }
        }

        // Pipe BLE HRM samples into the engine (US3). The HrmClient is a
        // process-wide singleton so its connection survives service restarts.
        lifecycleScope.launch {
            hrmClient.samples.collectLatest { sample ->
                if (sample != null) {
                    lastSampleMonotonicMs = SystemClock.elapsedRealtime()
                    engine.onHrSample(sample)
                }
            }
        }

        // Signal-lost watchdog (US3 AS3 / FR-027): if no sample arrives for 3 s,
        // emit null so the UI shows "\u2014 (signal lost)".
        hrSampleWatchdogJob = lifecycleScope.launch {
            while (true) {
                delay(1_000L)
                if (lastSampleMonotonicMs > 0L &&
                    SystemClock.elapsedRealtime() - lastSampleMonotonicMs > 3_000L
                ) {
                    engine.onHrSample(null)
                }
            }
        }

        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED)

        // Debug-only fake HR generator — gives the emulator a live HR stream
        // without a real HRM, so UX (avg HR row, hr card on Run page) can be
        // tested before pairing a strap on a physical device.
        if (BuildConfig.DEBUG) {
            fakeHrSource.start()
        }

        // Drive notification updates from engine state.
        lifecycleScope.launch {
            var alreadyMarkedComplete = false
            engine.uiState.collectLatest { state ->
                if (!foregroundStarted) return@collectLatest
                if (state.state == RunState.COMPLETE) {
                    if (!alreadyMarkedComplete && currentWorkoutId > 0L) {
                        alreadyMarkedComplete = true
                        // Save the result to history regardless of whether stopped early or natural completion
                        val result = engine.getResultForSaving()
                        if (result != null) {
                            val (workoutId, summary) = result
                            container.workoutResultRepository.save(
                                workoutId = workoutId,
                                plannedDurationSec = summary.plannedDurationSec,
                                actualDurationSec = summary.actualElapsedSec,
                                averageHrBpm = summary.averageHrBpm,
                                wasStoppedEarly = summary.wasStoppedEarly,
                                tss = summary.actualTss,
                                workoutDisplayName = summary.workoutDisplayName,
                            )
                        }
                        // Also mark completed on the workout record if finished naturally
                        if (!state.wasStoppedEarly) {
                            container.workoutRepository.markCompleted(currentWorkoutId)
                        }
                    }
                    // Workout finished (naturally or via Stop) — it is no longer
                    // running in the background, so remove the notification and
                    // leave foreground. The service stays alive (bound) so the
                    // summary can still be read; ACTION_RESET stops it later.
                    runHasStarted = false
                    stopForegroundAndSelf()
                    return@collectLatest
                }
                if (state.state == RunState.IDLE) {
                    alreadyMarkedComplete = false
                    // A freshly PRIMED workout is also IDLE (same as one that
                    // finished/reset). Only leave foreground if a run actually
                    // started — otherwise priming would tear the notification
                    // down for the whole session (the "no notification" bug).
                    if (runHasStarted) {
                        runHasStarted = false
                        stopForegroundAndSelf()
                    }
                    return@collectLatest
                }
                if (state.state == RunState.RUNNING) runHasStarted = true
                // The engine emits at ~4 Hz but the visible text only changes
                // ~1 Hz. Posting every emission floods NotificationManager's
                // per-package enqueue rate limiter, which sheds updates and
                // leaves the shade showing a stale step. Only re-post on change.
                val title = state.workoutDisplayName
                val text = NotificationBuilder.contentTextFor(
                    stepIndex = state.currentStepIndex,
                    stepsTotal = state.totalAuthoredSteps,
                    stepRemainingSec = state.stepRemainingSec,
                )
                if (title == lastNotifiedTitle && text == lastNotifiedText) return@collectLatest
                lastNotifiedTitle = title
                lastNotifiedText = text
                val notif = NotificationBuilder.build(
                    context = this@WorkoutForegroundService,
                    contentTitle = title,
                    contentText = text,
                    workoutId = currentWorkoutId,
                )
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationBuilder.NOTIFICATION_ID, notif)
            }
        }

        // Best-effort auto-reconnect to the last paired HRM (US3 / SC-006).
        tryAutoReconnectHrm()
    }

    private fun tryAutoReconnectHrm() {
        val container = (application as RunTrainingApp).container
        lifecycleScope.launch {
            val s = container.settings.settings.first()
            val deviceId = s.lastPairedDeviceId
            if (!deviceId.isNullOrBlank() && hrmClient.hasConnectPermission()) {
                hrmClient.connect(deviceId)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // If the user left the Run page before starting (still IDLE), there is
        // no session to keep alive — stop the service so it doesn't linger.
        if (engine.uiState.value.state == RunState.IDLE) {
            stopForegroundAndSelf()
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PRIME -> {
                val workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, -1L)
                if (workoutId > 0 && workoutId != currentWorkoutId) {
                    currentWorkoutId = workoutId
                    primeWorkout(workoutId)
                }
                // No foreground/notification until the workout actually runs —
                // the notification exists only to keep a running workout alive.
            }
            ACTION_START -> {
                if (!foregroundStarted) startForegroundForCurrentWorkout()
                engine.start()
            }
            ACTION_PAUSE -> engine.pause()
            ACTION_STOP -> {
                engine.stop()
                stopForegroundAndSelf()
            }
            ACTION_STEP_FWD -> engine.stepForward()
            ACTION_STEP_BACK -> engine.stepBackward()
            ACTION_ENABLE_MINI_VIEW -> setMiniViewEnabled(true)
            ACTION_DISABLE_MINI_VIEW -> setMiniViewEnabled(false)
            ACTION_RESET -> {
                // Tear down after the user dismisses the completion screen.
                engine.shutdown()
                stopForegroundAndSelf()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun primeWorkout(workoutId: Long) {
        val container = (application as RunTrainingApp).container
        lifecycleScope.launch {
            val w = container.workoutRepository.get(workoutId) ?: return@launch
            engine.setThresholdPace(container.settings.settings.first().thresholdPaceSecPerKm)
            engine.load(w)
        }
    }

    private fun startForegroundForCurrentWorkout() {
        val notif = NotificationBuilder.build(
            context = this,
            contentTitle = "Workout loaded",
            contentText = "Tap to open",
            workoutId = currentWorkoutId,
        )
        // Force the next engine emission to post, even if it happens to match
        // the previous session's last text.
        lastNotifiedTitle = null
        lastNotifiedText = null
        runHasStarted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationBuilder.NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationBuilder.NOTIFICATION_ID, notif)
        }
        foregroundStarted = true
    }

    private fun stopForegroundAndSelf() {
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        // Always tear down the overlay when the service leaves foreground;
        // FR-031: "automatically dismissed when the workout stops or completes".
        if (miniViewEnabled) setMiniViewEnabled(false)
        // Don't stop the service when paused/idle — UI may still be bound to read state.
    }

    /** Public API — toggle the always-on-top overlay. */
    fun setMiniViewEnabled(enabled: Boolean) {
        miniViewEnabled = enabled
        if (enabled) miniView.attach(engine.uiState)
        else miniView.detach()
    }

    override fun onDestroy() {
        Log.d("WorkoutForegroundService.onDestroy")
        runCatching { unregisterReceiver(btStateReceiver) }
        if (BuildConfig.DEBUG) {
            runCatching { fakeHrSource.stop() }
        }
        hrSampleWatchdogJob?.cancel()
        miniView.shutdown()
        engine.shutdown()
        runCatching { tonePlayer.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PRIME = "com.example.runtraining.action.PRIME"
        const val ACTION_START = "com.example.runtraining.action.START"
        const val ACTION_PAUSE = "com.example.runtraining.action.PAUSE"
        const val ACTION_STOP = "com.example.runtraining.action.STOP"
        const val ACTION_STEP_FWD = "com.example.runtraining.action.STEP_FWD"
        const val ACTION_STEP_BACK = "com.example.runtraining.action.STEP_BACK"
        const val ACTION_ENABLE_MINI_VIEW = "com.example.runtraining.action.ENABLE_MINI_VIEW"
        const val ACTION_DISABLE_MINI_VIEW = "com.example.runtraining.action.DISABLE_MINI_VIEW"
        const val ACTION_RESET = "com.example.runtraining.action.RESET"
        const val EXTRA_WORKOUT_ID = "workoutId"

        fun primeIntent(context: Context, workoutId: Long): Intent =
            Intent(context, WorkoutForegroundService::class.java).apply {
                action = ACTION_PRIME
                putExtra(EXTRA_WORKOUT_ID, workoutId)
            }

        fun simpleIntent(context: Context, action: String): Intent =
            Intent(context, WorkoutForegroundService::class.java).apply { this.action = action }
    }
}
