package com.example.runtraining.persistence.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_step",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RepeatGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["repeat_group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workout_id", "step_index"]),
        Index(value = ["repeat_group_id", "position_in_repeat"]),
    ],
)
data class WorkoutStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "workout_id")
    val workoutId: Long,
    /** 1-based ordinal in the authored step list (FR-017a). */
    @ColumnInfo(name = "step_index")
    val stepIndex: Int,
    @ColumnInfo(name = "step_name")
    val stepName: String?,
    /** One of "warmup" / "active" / "rest" / "cooldown" / "other". */
    @ColumnInfo(name = "intensity")
    val intensity: String,
    /** "time" | "distance" | "open". */
    @ColumnInfo(name = "source_duration_kind")
    val sourceDurationKind: String,
    @ColumnInfo(name = "source_duration_sec")
    val sourceDurationSec: Int?,
    @ColumnInfo(name = "source_distance_m")
    val sourceDistanceM: Int?,
    /** Null → step is "open" (un-runnable). */
    @ColumnInfo(name = "effective_duration_sec")
    val effectiveDurationSec: Int?,
    /** "pace" | "open". */
    @ColumnInfo(name = "target_kind")
    val targetKind: String,
    @ColumnInfo(name = "target_pace_lower_sec_per_km")
    val targetPaceLowerSecPerKm: Int?,
    @ColumnInfo(name = "target_pace_upper_sec_per_km")
    val targetPaceUpperSecPerKm: Int?,
    @ColumnInfo(name = "zone_label")
    val zoneLabel: String?,
    @ColumnInfo(name = "repeat_group_id")
    val repeatGroupId: Long?,
    @ColumnInfo(name = "position_in_repeat")
    val positionInRepeat: Int?,
)
