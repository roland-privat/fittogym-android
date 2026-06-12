package com.example.runtraining.ble

import android.os.SystemClock
import com.example.runtraining.workout.engine.RunState
import com.example.runtraining.workout.engine.RunUiState
import com.example.runtraining.workout.model.Intensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Debug-only fake heart-rate generator. Emits one `HrSample` per second
 * with a plausible BPM curve that reacts to the current step intensity, so
 * the Run page HR card and the Avg HR row in the completion summary can be
 * exercised on the emulator without a real BLE HRM.
 *
 * Gated behind `BuildConfig.DEBUG` at the call site in
 * `WorkoutForegroundService`.
 */
class FakeHrSource(
    private val scope: CoroutineScope,
    private val uiState: StateFlow<RunUiState>,
    private val onSample: (HrSample) -> Unit,
    /**
     * Returns true when a real HRM is currently delivering samples. While this
     * is true the fake source stays silent, so a real strap on a debug build
     * is never polluted by synthetic readings (which would make the displayed
     * HR flip-flop between the real and fake values).
     */
    private val isRealHrActive: () -> Boolean = { false },
) {
    private var job: Job? = null
    private val phaseStartMs = SystemClock.elapsedRealtime()

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                val state = uiState.value
                // Only emit while the engine is actively running. PAUSED still
                // emits (HR doesn't stop when you pause), but IDLE/COMPLETE
                // don't. And never emit while a real HRM is feeding samples.
                if (!isRealHrActive() &&
                    (state.state == RunState.RUNNING || state.state == RunState.PAUSED)
                ) {
                    val target = targetBpm(state.currentIntensity)
                    val tSec = (SystemClock.elapsedRealtime() - phaseStartMs) / 1000.0
                    // Slow drift via a 30-second sine, plus small jitter.
                    val drift = sin(2 * PI * tSec / 30.0) * 4.0
                    val jitter = Random.nextDouble(-1.5, 1.5)
                    val bpm = (target + drift + jitter).toInt().coerceIn(60, 200)
                    onSample(
                        HrSample(
                            bpm = bpm,
                            monotonicTimestampMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                }
                delay(1_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun targetBpm(intensity: Intensity): Double = when (intensity) {
        Intensity.WARMUP -> 120.0
        Intensity.COOLDOWN -> 110.0
        Intensity.REST -> 125.0
        Intensity.ACTIVE -> 160.0
        Intensity.OTHER -> 135.0
    }
}
