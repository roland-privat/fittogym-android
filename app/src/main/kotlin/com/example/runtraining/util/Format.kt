package com.example.runtraining.util

import java.util.Locale

/** Pure-logic formatters used across the app. No Android dependencies → unit-testable. */
object Format {

    /** Seconds → "m:ss" (or "h:mm:ss" when ≥ 1 hour). Never negative. */
    fun formatDuration(totalSec: Int): String {
        val s = maxOf(0, totalSec)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Pace in sec/km → "m:ss/km". */
    fun formatPace(secPerKm: Int): String {
        val s = maxOf(0, secPerKm)
        val minutes = s / 60
        val seconds = s % 60
        return String.format(Locale.US, "%d:%02d/km", minutes, seconds)
    }

    /** Pace range "lo–hi /km" with the *faster* (smaller sec/km) bound first per spec. */
    fun formatPaceRange(lowerSecPerKm: Int, upperSecPerKm: Int): String {
        val lo = minOf(lowerSecPerKm, upperSecPerKm)
        val hi = maxOf(lowerSecPerKm, upperSecPerKm)
        return "${formatPaceNoUnit(lo)}\u2013${formatPaceNoUnit(hi)} /km"
    }

    private fun formatPaceNoUnit(secPerKm: Int): String {
        val s = maxOf(0, secPerKm)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** Pace sec/km → speed km/h. 0 / negative returns 0.0. */
    fun paceSecPerKmToKmh(secPerKm: Int): Double {
        if (secPerKm <= 0) return 0.0
        return 3600.0 / secPerKm.toDouble()
    }

    /** Speed in km/h → "X.X km/h". */
    fun formatSpeed(kmh: Double): String = String.format(Locale.US, "%.1f km/h", kmh)

    /** Speed range from pace bounds. The *faster* pace (smaller sec/km) gives the higher km/h. */
    fun formatSpeedRangeFromPace(lowerSecPerKm: Int, upperSecPerKm: Int): String {
        val hiKmh = paceSecPerKmToKmh(minOf(lowerSecPerKm, upperSecPerKm))
        val loKmh = paceSecPerKmToKmh(maxOf(lowerSecPerKm, upperSecPerKm))
        return String.format(Locale.US, "%.1f\u2013%.1f km/h", loKmh, hiKmh)
    }

    /** Short relative time like "5 min ago", "3 d ago", or a date for older. Null → "—". */
    fun formatRelativeAgo(eventEpochMs: Long?, nowEpochMs: Long = System.currentTimeMillis()): String {
        if (eventEpochMs == null) return "\u2014"
        val deltaSec = ((nowEpochMs - eventEpochMs) / 1000L).coerceAtLeast(0L)
        return when {
            deltaSec < 45L -> "just now"
            deltaSec < 60L * 60L -> "${(deltaSec / 60L).coerceAtLeast(1L)} min ago"
            deltaSec < 60L * 60L * 24L -> "${deltaSec / 3600L} h ago"
            deltaSec < 60L * 60L * 24L * 7L -> "${deltaSec / 86400L} d ago"
            else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(java.util.Date(eventEpochMs))
        }
    }
}
