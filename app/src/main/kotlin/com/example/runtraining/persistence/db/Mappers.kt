package com.example.runtraining.persistence.db

import com.example.runtraining.persistence.db.entities.HrMonitorPairingEntity
import com.example.runtraining.persistence.db.entities.RepeatGroupEntity
import com.example.runtraining.persistence.db.entities.WorkoutEntity
import com.example.runtraining.persistence.db.entities.WorkoutStepEntity
import com.example.runtraining.ble.HrMonitorPairing
import com.example.runtraining.workout.model.Intensity
import com.example.runtraining.workout.model.RepeatGroup
import com.example.runtraining.workout.model.RepeatPosition
import com.example.runtraining.workout.model.SourceDuration
import com.example.runtraining.workout.model.Step
import com.example.runtraining.workout.model.Target
import com.example.runtraining.workout.model.Workout

/**
 * Entity ⇄ Domain mappers with boundary validation. Validation throws
 * IllegalArgumentException on violation; callers (e.g. ImportWorkoutUseCase)
 * translate that to a Rejected import result.
 *
 * See specs/001-core-workout-flow/data-model.md §4.
 */
object Mappers {

    private const val MIN_PACE_SEC_PER_KM = 120  // 2:00/km — implausibly fast for a human → reject
    private const val MAX_PACE_SEC_PER_KM = 1200 // 20:00/km

    // --- Workout -> entities -----------------------------------------------

    /** Returns (workout, steps, repeatGroups). IDs are zeroed for inserts. */
    fun Workout.toEntities(blobRelativePath: String): Triple<WorkoutEntity, List<WorkoutStepEntity>, List<RepeatGroupEntity>> {
        require(displayName.isNotBlank()) { "Workout display name must not be blank." }
        require(plannedDurationSec >= 0) { "Workout duration must be non-negative." }
        require(plannedDistanceM >= 0) { "Workout distance must be non-negative." }
        require(steps.isNotEmpty()) { "Workout must have at least one step." }
        // Step indices contiguous, 1..N
        steps.forEachIndexed { idx, s ->
            require(s.stepIndex == idx + 1) {
                "Steps must be contiguous starting at 1; saw index ${s.stepIndex} at position $idx."
            }
        }
        // Repeat groups iteration count
        repeatGroups.forEach { rg ->
            require(rg.iterationCount in 2..99) {
                "Repeat group iterationCount must be in 2..99; saw ${rg.iterationCount}."
            }
        }

        val workoutEntity = WorkoutEntity(
            contentHash = contentHash,
            originalFilename = originalFilename,
            displayName = displayName,
            importedAtEpochMs = importedAtEpochMs,
            sport = "running",
            plannedDurationSec = plannedDurationSec,
            plannedDistanceM = plannedDistanceM,
            tss = tss,
            blobRelativePath = blobRelativePath,
            lastCompletedEpochMs = lastCompletedAtEpochMs,
        )

        val repeatGroupEntities = repeatGroups.map { rg ->
            RepeatGroupEntity(
                id = rg.id,
                workoutId = id, // overwritten by repository on insert
                orderInWorkout = rg.orderInWorkout,
                iterationCount = rg.iterationCount,
            )
        }

        val stepEntities = steps.map { it.toEntity(workoutId = id) }

        return Triple(workoutEntity, stepEntities, repeatGroupEntities)
    }

    private fun Step.toEntity(workoutId: Long): WorkoutStepEntity {
        validatePace(target)
        val (kind, timeSec, distM) = when (val sd = sourceDuration) {
            is SourceDuration.Time -> Triple("time", sd.seconds, null)
            is SourceDuration.Distance -> Triple("distance", null, sd.meters)
            is SourceDuration.Open -> Triple("open", null, null)
        }
        val (targetKind, pLo, pHi) = when (val t = target) {
            is Target.Pace -> Triple("pace", t.lowerSecPerKm, t.upperSecPerKm)
            is Target.Open -> Triple("open", null, null)
        }
        return WorkoutStepEntity(
            id = id,
            workoutId = workoutId,
            stepIndex = stepIndex,
            stepName = name,
            intensity = intensity.name.lowercase(),
            sourceDurationKind = kind,
            sourceDurationSec = timeSec,
            sourceDistanceM = distM,
            effectiveDurationSec = effectiveDurationSec,
            targetKind = targetKind,
            targetPaceLowerSecPerKm = pLo,
            targetPaceUpperSecPerKm = pHi,
            zoneLabel = zoneLabel,
            repeatGroupId = inRepeat?.repeatGroupId,
            positionInRepeat = inRepeat?.positionInRepeat,
        )
    }

    private fun validatePace(target: Target) {
        if (target is Target.Pace) {
            require(target.lowerSecPerKm in MIN_PACE_SEC_PER_KM..MAX_PACE_SEC_PER_KM) {
                "Pace bound ${target.lowerSecPerKm} sec/km out of plausible range [$MIN_PACE_SEC_PER_KM, $MAX_PACE_SEC_PER_KM]."
            }
            require(target.upperSecPerKm in MIN_PACE_SEC_PER_KM..MAX_PACE_SEC_PER_KM) {
                "Pace bound ${target.upperSecPerKm} sec/km out of plausible range [$MIN_PACE_SEC_PER_KM, $MAX_PACE_SEC_PER_KM]."
            }
            require(target.lowerSecPerKm <= target.upperSecPerKm) {
                "Pace lower bound (${target.lowerSecPerKm}) must be ≤ upper (${target.upperSecPerKm})."
            }
        }
    }

    // --- Entities -> Workout -----------------------------------------------

    fun WorkoutWithChildren.toDomain(): Workout {
        val repeatsByEntityId = repeatGroups.associate { rg ->
            rg.id to RepeatGroup(
                id = rg.id,
                orderInWorkout = rg.orderInWorkout,
                iterationCount = rg.iterationCount,
            )
        }
        return Workout(
            id = workout.id,
            contentHash = workout.contentHash,
            originalFilename = workout.originalFilename,
            displayName = workout.displayName,
            importedAtEpochMs = workout.importedAtEpochMs,
            plannedDurationSec = workout.plannedDurationSec,
            plannedDistanceM = workout.plannedDistanceM,
            tss = workout.tss,
            lastCompletedAtEpochMs = workout.lastCompletedEpochMs,
            steps = steps.map { it.toDomain() },
            repeatGroups = repeatsByEntityId.values.sortedBy { it.orderInWorkout },
        )
    }

    private fun WorkoutStepEntity.toDomain(): Step {
        val intensityEnum = runCatching { Intensity.valueOf(intensity.uppercase()) }
            .getOrDefault(Intensity.OTHER)
        val source: SourceDuration = when (sourceDurationKind) {
            "time" -> SourceDuration.Time(sourceDurationSec ?: 0)
            "distance" -> SourceDuration.Distance(sourceDistanceM ?: 0)
            else -> SourceDuration.Open
        }
        val tgt: Target = if (targetKind == "pace" &&
            targetPaceLowerSecPerKm != null && targetPaceUpperSecPerKm != null
        ) {
            Target.Pace(targetPaceLowerSecPerKm, targetPaceUpperSecPerKm)
        } else {
            Target.Open
        }
        val rp = if (repeatGroupId != null && positionInRepeat != null) {
            RepeatPosition(repeatGroupId, positionInRepeat)
        } else null
        return Step(
            id = id,
            stepIndex = stepIndex,
            name = stepName,
            intensity = intensityEnum,
            sourceDuration = source,
            effectiveDurationSec = effectiveDurationSec,
            target = tgt,
            zoneLabel = zoneLabel,
            inRepeat = rp,
        )
    }

    // --- HrMonitorPairing --------------------------------------------------

    fun HrMonitorPairingEntity.toDomain(): HrMonitorPairing =
        HrMonitorPairing(deviceId, lastKnownName, lastConnectedEpochMs)

    fun HrMonitorPairing.toEntity(): HrMonitorPairingEntity =
        HrMonitorPairingEntity(
            id = 1,
            deviceId = deviceId,
            lastKnownName = lastKnownName,
            lastConnectedEpochMs = lastConnectedEpochMs,
        )
}
