package com.example.runtraining.persistence.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.runtraining.persistence.db.entities.HrMonitorPairingEntity
import com.example.runtraining.persistence.db.entities.RepeatGroupEntity
import com.example.runtraining.persistence.db.entities.WorkoutEntity
import com.example.runtraining.persistence.db.entities.WorkoutStepEntity

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutStepEntity::class,
        RepeatGroupEntity::class,
        HrMonitorPairingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RunTrainingDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao
    abstract fun hrMonitorPairingDao(): HrMonitorPairingDao

    companion object {
        @Volatile private var instance: RunTrainingDatabase? = null

        /**
         * v1 → v2: add `last_completed_epoch_ms` column to `workout`.
         * Data-preserving (per the user-memory rule on EF/Room migrations).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout ADD COLUMN last_completed_epoch_ms INTEGER")
            }
        }

        fun get(context: Context): RunTrainingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RunTrainingDatabase::class.java,
                    "run-training.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
