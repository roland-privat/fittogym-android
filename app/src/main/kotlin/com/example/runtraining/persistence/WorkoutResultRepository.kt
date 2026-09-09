package com.example.runtraining.persistence

import com.example.runtraining.persistence.db.WorkoutResultDao
import com.example.runtraining.persistence.db.entities.WorkoutResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * High-level API for managing workout results (completed/stopped sessions).
 */
class WorkoutResultRepository(private val dao: WorkoutResultDao) {

    /**
     * Save a completed or stopped workout session.
     * 
     * @param workoutId The ID of the workout that was run.
     * @param plannedDurationSec The planned duration from the workout.
     * @param actualDurationSec The actual elapsed time.
     * @param averageHrBpm Average heart rate in BPM (null if HRM not connected).
     * @param wasStoppedEarly True if user stopped early; false if completed naturally.
     * @param tss TSS from the workout.
     * @param workoutDisplayName The workout's display name at result time.
     * @return The ID of the inserted result.
     */
    suspend fun save(
        workoutId: Long,
        plannedDurationSec: Int,
        actualDurationSec: Int,
        averageHrBpm: Int?,
        wasStoppedEarly: Boolean,
        tss: Double?,
        workoutDisplayName: String,
    ): Long {
        val result = WorkoutResultEntity(
            workoutId = workoutId,
            completedAtEpochMs = System.currentTimeMillis(),
            plannedDurationSec = plannedDurationSec,
            actualDurationSec = actualDurationSec,
            averageHrBpm = averageHrBpm,
            wasStoppedEarly = wasStoppedEarly,
            tss = tss,
            workoutDisplayName = workoutDisplayName,
        )
        return dao.insert(result)
    }

    /** Get all results, sorted by date (newest first). */
    fun getAllResults(): Flow<List<WorkoutResultEntity>> = dao.getAllResults()

    /** Get results for a specific workout. */
    fun getResultsByWorkout(workoutId: Long): Flow<List<WorkoutResultEntity>> =
        dao.getResultsByWorkout(workoutId)

    /** Get a single result by ID. */
    suspend fun getResult(resultId: Long): WorkoutResultEntity? = dao.getResult(resultId)

    /** Delete a single result. */
    suspend fun delete(resultId: Long) {
        val result = dao.getResult(resultId) ?: return
        dao.delete(result)
    }

    /** Delete multiple results. */
    suspend fun deleteMultiple(resultIds: List<Long>) {
        if (resultIds.isNotEmpty()) {
            dao.deleteMultiple(resultIds)
        }
    }

    /** Get only completed results (excluding stopped-early). */
    fun getCompletedResults(): Flow<List<WorkoutResultEntity>> = dao.getCompletedResults()

    /** Get total count of results. */
    fun getResultCount(): Flow<Int> = dao.getResultCount()
}
