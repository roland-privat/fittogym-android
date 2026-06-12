package com.example.runtraining.workout.fit

import com.example.runtraining.util.Log
import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.Intensity as FitIntensity
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.Sport
import com.garmin.fit.WktStepDuration
import com.garmin.fit.WktStepTarget
import com.garmin.fit.WorkoutMesg
import com.garmin.fit.WorkoutMesgListener
import com.garmin.fit.WorkoutStepMesg
import com.garmin.fit.WorkoutStepMesgListener
import com.garmin.fit.File as FitFileType
import java.io.ByteArrayInputStream

/**
 * Decoded workout in a Garmin-SDK-free shape. Step indices here are the FIT
 * message_index (0-based) — repeat markers ARE emitted in `decodedSteps` so
 * the domain mapper can pair them with their referenced step range. Mapping
 * to the canonical 1-based authored step list happens in DecodedToDomain.
 */
data class DecodedWorkout(
    val name: String?,
    val sport: String,                 // always "running" on Result.Ok
    val steps: List<DecodedStep>,
    val repeats: List<DecodedRepeat>,
)

data class DecodedStep(
    /** 0-based FIT message_index. */
    val originalIndex: Int,
    val name: String?,
    /** One of "warmup" / "active" / "rest" / "cooldown" / "other". */
    val intensity: String,
    /** "time" | "distance" | "open". Repeat markers are NOT here — they're in `repeats`. */
    val durationKind: String,
    val durationSec: Int?,
    val distanceM: Int?,
    /** "pace" | "open". */
    val targetKind: String,
    val targetPaceLowerSecPerKm: Int?,
    val targetPaceUpperSecPerKm: Int?,
    val zoneLabel: String?,
)

data class DecodedRepeat(
    /** Inclusive, FIT message_index (0-based) of the first step in the loop. */
    val fromOriginalIndex: Int,
    /** Inclusive, FIT message_index (0-based) of the last step in the loop. */
    val toOriginalIndex: Int,
    val iterationCount: Int,
)

sealed class FitDecodeResult {
    data class Ok(val workout: DecodedWorkout) : FitDecodeResult()
    data class Rejected(val reason: RejectReason, val detail: String? = null) : FitDecodeResult()
}

enum class RejectReason {
    NOT_A_FIT_FILE,
    NOT_A_WORKOUT_FILE,
    NOT_RUNNING,
    NO_STEPS,
    MALFORMED,
}

/**
 * Wraps the Garmin FIT Java SDK. We expose only the surface this app needs.
 * See specs/001-core-workout-flow/research.md §2.
 */
object FitDecoder {

    fun decode(bytes: ByteArray): FitDecodeResult {
        if (bytes.isEmpty()) return FitDecodeResult.Rejected(RejectReason.MALFORMED, "empty file")

        val decoder = Decode()
        val input = ByteArrayInputStream(bytes)

        // Pre-check FIT header validity before running the listeners — avoids
        // ambiguous downstream errors on random binaries.
        if (!decoder.checkFileIntegrity(ByteArrayInputStream(bytes))) {
            return FitDecodeResult.Rejected(RejectReason.NOT_A_FIT_FILE, "FIT integrity check failed")
        }

        val broadcaster = MesgBroadcaster(decoder)

        var fileType: FitFileType? = null
        var workoutName: String? = null
        var workoutSport: Sport? = null
        val rawSteps = mutableListOf<WorkoutStepMesg>()

        broadcaster.addListener(FileIdMesgListener { m: FileIdMesg ->
            fileType = m.type
        })
        broadcaster.addListener(WorkoutMesgListener { m: WorkoutMesg ->
            workoutName = m.wktName
            workoutSport = m.sport
        })
        broadcaster.addListener(WorkoutStepMesgListener { m: WorkoutStepMesg ->
            rawSteps += copyOf(m)
        })

        try {
            broadcaster.run(input)
        } catch (t: Throwable) {
            Log.w("FIT decode threw: ${t.message}", t)
            return FitDecodeResult.Rejected(RejectReason.MALFORMED, t.message)
        }

        if (fileType != FitFileType.WORKOUT) {
            return FitDecodeResult.Rejected(RejectReason.NOT_A_WORKOUT_FILE, "type=$fileType")
        }
        if (workoutSport != Sport.RUNNING) {
            return FitDecodeResult.Rejected(RejectReason.NOT_RUNNING, "sport=$workoutSport")
        }

        // Sort by message_index so we walk steps in author order.
        rawSteps.sortBy { (it.messageIndex ?: 0).toInt() }

        val emittedSteps = mutableListOf<DecodedStep>()
        val repeats = mutableListOf<DecodedRepeat>()

        rawSteps.forEach { mesg ->
            val msgIdx = (mesg.messageIndex ?: 0).toInt()
            val durType = mesg.durationType
            if (durType == WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT ||
                durType == WktStepDuration.REPEAT_UNTIL_TIME ||
                durType == WktStepDuration.REPEAT_UNTIL_DISTANCE ||
                durType == WktStepDuration.REPEAT_UNTIL_CALORIES ||
                durType == WktStepDuration.REPEAT_UNTIL_HR_LESS_THAN ||
                durType == WktStepDuration.REPEAT_UNTIL_HR_GREATER_THAN ||
                durType == WktStepDuration.REPEAT_UNTIL_POWER_LESS_THAN ||
                durType == WktStepDuration.REPEAT_UNTIL_POWER_GREATER_THAN
            ) {
                // Repeat-loop marker. duration_value is the step index to loop
                // BACK to (0-based). For REPEAT_UNTIL_STEPS_CMPLT, target_value
                // is the number of iterations (typed as repeat_steps). For
                // other repeat-until variants, only the count via target_value
                // matters for our v1 (we ignore HR/power exit conditions).
                val from = (mesg.durationStep ?: 0L).toInt()
                val to = msgIdx - 1
                val count = (mesg.targetValue ?: 1L).toInt().coerceAtLeast(2)
                if (to >= from) {
                    repeats += DecodedRepeat(
                        fromOriginalIndex = from,
                        toOriginalIndex = to,
                        iterationCount = count,
                    )
                }
                return@forEach
            }

            emittedSteps += decodeRegularStep(mesg, msgIdx)
        }

        if (emittedSteps.isEmpty()) {
            return FitDecodeResult.Rejected(RejectReason.NO_STEPS, "0 emitted steps")
        }

        return FitDecodeResult.Ok(
            DecodedWorkout(
                name = workoutName,
                sport = "running",
                steps = emittedSteps,
                repeats = repeats,
            ),
        )
    }

    private fun decodeRegularStep(m: WorkoutStepMesg, msgIdx: Int): DecodedStep {
        val (kind, sec, distM) = when (m.durationType) {
            WktStepDuration.TIME -> {
                // duration_time scale is seconds (typed getter handles the FIT scale of 1000).
                val s = m.durationTime?.toInt() ?: 0
                Triple("time", s, null)
            }
            WktStepDuration.DISTANCE -> {
                val d = m.durationDistance?.toInt() ?: 0
                Triple("distance", null, d)
            }
            WktStepDuration.OPEN, null -> Triple("open", null, null)
            else -> Triple("open", null, null)
        }

        // Pace targets in FIT workouts are stored as speeds (m/s). PACE target
        // type is mostly a UI hint; SPEED encodes the actual range. Convert.
        val (targetKind, pLo, pHi) = when (m.targetType) {
            WktStepTarget.SPEED -> {
                val sLowMps = m.customTargetSpeedLow?.toDouble()  // m/s
                val sHighMps = m.customTargetSpeedHigh?.toDouble() // m/s
                if (sLowMps != null && sHighMps != null && sLowMps > 0 && sHighMps > 0) {
                    // Faster = higher speed = smaller sec/km. Map to (lo,hi) by
                    // ensuring lower = faster (smaller sec/km).
                    val secPerKmAtSLow = (1000.0 / sLowMps).toInt()    // slower bound
                    val secPerKmAtSHigh = (1000.0 / sHighMps).toInt()  // faster bound
                    val lower = minOf(secPerKmAtSLow, secPerKmAtSHigh)
                    val upper = maxOf(secPerKmAtSLow, secPerKmAtSHigh)
                    Triple("pace", lower, upper)
                } else {
                    Triple("open", null, null)
                }
            }
            else -> Triple("open", null, null)
        }

        val intensity = when (m.intensity) {
            FitIntensity.WARMUP -> "warmup"
            FitIntensity.ACTIVE -> "active"
            FitIntensity.REST, FitIntensity.RECOVERY -> "rest"
            FitIntensity.COOLDOWN -> "cooldown"
            else -> "other"
        }

        return DecodedStep(
            originalIndex = msgIdx,
            name = m.wktStepName?.takeIf { it.isNotBlank() },
            intensity = intensity,
            durationKind = kind,
            durationSec = sec,
            distanceM = distM,
            targetKind = targetKind,
            targetPaceLowerSecPerKm = pLo,
            targetPaceUpperSecPerKm = pHi,
            zoneLabel = null,
        )
    }

    /** Garmin's WorkoutStepMesg is mutable & re-used by the broadcaster. */
    private fun copyOf(m: WorkoutStepMesg): WorkoutStepMesg = WorkoutStepMesg(m)
}
