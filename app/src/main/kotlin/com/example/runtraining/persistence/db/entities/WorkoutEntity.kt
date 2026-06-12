package com.example.runtraining.persistence.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Top-level workout record. One row per imported `.fit` file (deduped by SHA-256 content hash).
 * See specs/001-core-workout-flow/data-model.md §1.
 */
@Entity(
    tableName = "workout",
    indices = [Index(value = ["content_hash"], unique = true), Index(value = ["imported_at_epoch_ms"])],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @androidx.room.ColumnInfo(name = "content_hash")
    val contentHash: String,
    @androidx.room.ColumnInfo(name = "original_filename")
    val originalFilename: String,
    @androidx.room.ColumnInfo(name = "display_name")
    val displayName: String,
    @androidx.room.ColumnInfo(name = "imported_at_epoch_ms")
    val importedAtEpochMs: Long,
    @androidx.room.ColumnInfo(name = "sport")
    val sport: String,
    @androidx.room.ColumnInfo(name = "planned_duration_sec")
    val plannedDurationSec: Int,
    @androidx.room.ColumnInfo(name = "planned_distance_m")
    val plannedDistanceM: Int,
    @androidx.room.ColumnInfo(name = "tss")
    val tss: Double?,
    @androidx.room.ColumnInfo(name = "blob_relative_path")
    val blobRelativePath: String,
    /** Wall-clock epoch ms when the user last completed this workout naturally. Null = never completed. */
    @androidx.room.ColumnInfo(name = "last_completed_epoch_ms")
    val lastCompletedEpochMs: Long? = null,
)
