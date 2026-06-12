package com.example.runtraining.persistence.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.runtraining.persistence.db.entities.RepeatGroupEntity
import com.example.runtraining.persistence.db.entities.WorkoutEntity
import com.example.runtraining.persistence.db.entities.WorkoutStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * Joined query result for a workout plus its children. Mapped to the domain
 * `Workout` via `Mappers.toDomain`.
 */
data class WorkoutWithChildren(
    val workout: WorkoutEntity,
    val steps: List<WorkoutStepEntity>,
    val repeatGroups: List<RepeatGroupEntity>,
)

@Dao
abstract class WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRepeatGroups(groups: List<RepeatGroupEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSteps(steps: List<WorkoutStepEntity>): List<Long>

    @Query("SELECT * FROM workout WHERE id = :id")
    abstract suspend fun findById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE content_hash = :hash LIMIT 1")
    abstract suspend fun findByHash(hash: String): WorkoutEntity?

    @Query("SELECT * FROM workout_step WHERE workout_id = :workoutId ORDER BY step_index ASC")
    abstract suspend fun stepsFor(workoutId: Long): List<WorkoutStepEntity>

    @Query("SELECT * FROM repeat_group WHERE workout_id = :workoutId ORDER BY order_in_workout ASC")
    abstract suspend fun repeatGroupsFor(workoutId: Long): List<RepeatGroupEntity>

    @Transaction
    open suspend fun loadWithChildren(id: Long): WorkoutWithChildren? {
        val w = findById(id) ?: return null
        return WorkoutWithChildren(
            workout = w,
            steps = stepsFor(id),
            repeatGroups = repeatGroupsFor(id),
        )
    }

    @Query("SELECT * FROM workout ORDER BY imported_at_epoch_ms DESC")
    abstract fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout ORDER BY imported_at_epoch_ms DESC")
    abstract suspend fun getAll(): List<WorkoutEntity>

    @Query("UPDATE workout SET display_name = :newName WHERE id = :id")
    abstract suspend fun updateDisplayName(id: Long, newName: String)

    @Query("UPDATE workout SET tss = :tss WHERE id = :id")
    abstract suspend fun updateTss(id: Long, tss: Double?)

    @Query("UPDATE workout SET last_completed_epoch_ms = :epochMs WHERE id = :id")
    abstract suspend fun updateLastCompleted(id: Long, epochMs: Long)

    @Query("DELETE FROM workout WHERE id = :id")
    abstract suspend fun deleteById(id: Long)
}
