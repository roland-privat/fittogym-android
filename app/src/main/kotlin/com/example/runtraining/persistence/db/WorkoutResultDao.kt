package com.example.runtraining.persistence.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import com.example.runtraining.persistence.db.entities.WorkoutResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutResultDao {

    /** Save a new workout result. */
    @Insert
    suspend fun insert(result: WorkoutResultEntity): Long

    /** Get all results, sorted by completed date (newest first). */
    @Query("SELECT * FROM workout_result ORDER BY completed_at_epoch_ms DESC")
    fun getAllResults(): Flow<List<WorkoutResultEntity>>

    /** Get results for a specific workout, sorted by date (newest first). */
    @Query("SELECT * FROM workout_result WHERE workout_id = :workoutId ORDER BY completed_at_epoch_ms DESC")
    fun getResultsByWorkout(workoutId: Long): Flow<List<WorkoutResultEntity>>

    /** Get a single result by ID. */
    @Query("SELECT * FROM workout_result WHERE id = :resultId")
    suspend fun getResult(resultId: Long): WorkoutResultEntity?

    /** Delete a single result. */
    @Delete
    suspend fun delete(result: WorkoutResultEntity)

    /** Delete multiple results by ID. */
    @Query("DELETE FROM workout_result WHERE id IN (:resultIds)")
    suspend fun deleteMultiple(resultIds: List<Long>)

    /** Delete all results for a workout (called when workout is deleted). */
    @Query("DELETE FROM workout_result WHERE workout_id = :workoutId")
    suspend fun deleteByWorkout(workoutId: Long)

    /** Get results excluding stopped-early sessions (natural completions only). */
    @Query("SELECT * FROM workout_result WHERE was_stopped_early = 0 ORDER BY completed_at_epoch_ms DESC")
    fun getCompletedResults(): Flow<List<WorkoutResultEntity>>

    /** Get count of all results. */
    @Query("SELECT COUNT(*) FROM workout_result")
    fun getResultCount(): Flow<Int>
}
