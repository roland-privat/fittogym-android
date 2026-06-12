package com.example.runtraining.audio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * BeepScheduler implementation backed by the main-thread Handler. Fires the
 * `onBeep` callback at monotonic-time targets computed from
 * `SystemClock.elapsedRealtime()`. Self-cancelling on `scheduleStepEndBeeps`
 * to keep step-transition semantics tidy.
 */
class HandlerBeepScheduler : BeepScheduler {

    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Runnable>()

    override fun scheduleStepEndBeeps(
        stepStartMonotonicMs: Long,
        effectiveDurationSec: Int?,
        onBeep: () -> Unit,
    ) {
        cancelAll()
        if (effectiveDurationSec == null || effectiveDurationSec <= 0) return

        val stepEndMs = stepStartMonotonicMs + effectiveDurationSec * 1000L
        val firstBeepIdx = if (effectiveDurationSec >= 5) 5 else effectiveDurationSec
        for (k in firstBeepIdx downTo 1) {
            val targetMs = stepEndMs - k * 1000L
            val delayMs = targetMs - SystemClock.elapsedRealtime()
            val safeDelay = if (delayMs < 0) 0L else delayMs
            val r = Runnable { onBeep() }
            handler.postDelayed(r, safeDelay)
            pending += r
        }
    }

    override fun cancelAll() {
        pending.forEach { handler.removeCallbacks(it) }
        pending.clear()
    }
}
