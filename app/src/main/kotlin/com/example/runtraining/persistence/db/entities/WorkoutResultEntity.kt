package com.example.runtraining.persistence.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per completed/stopped workout session.
 * Tracks actual performance metrics vs. planned targets.
 */
@Entity(
    tableName = "workout_result",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["workout_id"]),
        Index(value = ["completed_at_epoch_ms"], unique = false),
    ]
)
data class WorkoutResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @androidx.room.ColumnInfo(name = "workout_id")
    val workoutId: Long,

    /** Wall-clock epoch ms when this session was completed/stopped. */
    @androidx.room.ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long,

    /** Planned duration from the workout, in seconds. */
    @androidx.room.ColumnInfo(name = "planned_duration_sec")
    val plannedDurationSec: Int,

    /** Actual elapsed time, in seconds. */
    @androidx.room.ColumnInfo(name = "actual_duration_sec")
    val actualDurationSec: Int,

    /** Average heart rate during the session, in BPM. Null if HRM was not connected. */
    @androidx.room.ColumnInfo(name = "average_hr_bpm")
    val averageHrBpm: Int?,

    /** True if the user stopped early; false if completed naturally. */
    @androidx.room.ColumnInfo(name = "was_stopped_early")
    val wasStoppedEarly: Boolean,

    /** TSS from the workout. Copied at result time (for display, even if workout is deleted). */
    @androidx.room.ColumnInfo(name = "tss")
    val tss: Double?,

    /** Workout display name at the time of completion (for display, even if workout is deleted). */
    @androidx.room.ColumnInfo(name = "workout_display_name")
    val workoutDisplayName: String,
)
