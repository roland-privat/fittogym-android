package com.example.runtraining.settings

/** How target intensities are rendered to the user. Toggle is in Options (FR-013a). */
enum class DisplayUnit { PACE, SPEED }

/**
 * App-level user settings persisted in DataStore Preferences.
 * See specs/001-core-workout-flow/data-model.md §2.
 */
data class AppSettings(
    /** Null → unset; TSS renders as "—" everywhere. */
    val thresholdPaceSecPerKm: Int?,
    /** Default PACE per FR-013a. */
    val displayUnit: DisplayUnit,
    /** Null → no HRM has ever been paired. */
    val lastPairedDeviceId: String?,
) {
    companion object {
        val DEFAULT = AppSettings(
            thresholdPaceSecPerKm = null,
            displayUnit = DisplayUnit.PACE,
            lastPairedDeviceId = null,
        )
    }
}
