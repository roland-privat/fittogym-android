package com.example.runtraining.ble

/** Single live HR sample from a broadcast BLE HRM. monotonicTimestampMs is from SystemClock.elapsedRealtime(). */
data class HrSample(
    val bpm: Int,
    val monotonicTimestampMs: Long,
)

/** Persisted pairing record for auto-reconnect on next launch. */
data class HrMonitorPairing(
    val deviceId: String,
    val lastKnownName: String,
    val lastConnectedEpochMs: Long,
)
