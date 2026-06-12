package com.example.runtraining.persistence.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.runtraining.persistence.db.entities.HrMonitorPairingEntity

@Dao
interface HrMonitorPairingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pairing: HrMonitorPairingEntity)

    @Query("SELECT * FROM hr_monitor_pairing WHERE id = 1")
    suspend fun get(): HrMonitorPairingEntity?

    @Query("DELETE FROM hr_monitor_pairing WHERE id = 1")
    suspend fun clear()
}
