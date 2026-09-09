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
import com.example.runtraining.persistence.db.entities.WorkoutResultEntity

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutStepEntity::class,
        RepeatGroupEntity::class,
        HrMonitorPairingEntity::class,
        WorkoutResultEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RunTrainingDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao
    abstract fun hrMonitorPairingDao(): HrMonitorPairingDao
    abstract fun workoutResultDao(): WorkoutResultDao

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

        /**
         * v2 → v3: add `workout_result` table for tracking completed/stopped sessions.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE workout_result (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workout_id INTEGER NOT NULL,
                        completed_at_epoch_ms INTEGER NOT NULL,
                        planned_duration_sec INTEGER NOT NULL,
                        actual_duration_sec INTEGER NOT NULL,
                        average_hr_bpm INTEGER,
                        was_stopped_early INTEGER NOT NULL,
                        tss REAL,
                        workout_display_name TEXT NOT NULL,
                        FOREIGN KEY (workout_id) REFERENCES workout(id) ON DELETE CASCADE
                    )
                """)
                // Index names MUST match Room's auto-generated names (index_<table>_<column>),
                // otherwise Room's post-migration schema validation throws at startup on upgrade.
                db.execSQL("CREATE INDEX index_workout_result_workout_id ON workout_result(workout_id)")
                db.execSQL("CREATE INDEX index_workout_result_completed_at_epoch_ms ON workout_result(completed_at_epoch_ms)")
            }
        }

        fun get(context: Context): RunTrainingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RunTrainingDatabase::class.java,
                    "run-training.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
