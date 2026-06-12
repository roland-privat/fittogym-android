package com.example.runtraining.persistence.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (CHECK id = 1) holding the last-paired HRM. We don't enforce
 * the CHECK constraint at the SQLite level for v1; the DAO upsert pattern
 * guarantees there's at most one row.
 */
@Entity(tableName = "hr_monitor_pairing")
data class HrMonitorPairingEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "last_known_name")
    val lastKnownName: String,
    @ColumnInfo(name = "last_connected_epoch_ms")
    val lastConnectedEpochMs: Long,
)
