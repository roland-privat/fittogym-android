package com.example.runtraining.persistence

import androidx.room.withTransaction
import com.example.runtraining.persistence.db.Mappers.toDomain
import com.example.runtraining.persistence.db.Mappers.toEntities
import com.example.runtraining.persistence.db.RunTrainingDatabase
import com.example.runtraining.persistence.db.WorkoutWithChildren
import com.example.runtraining.persistence.files.WorkoutBlobStore
import com.example.runtraining.workout.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * Orchestrates Room + blob store for the Workout aggregate. Sole entry point
 * for UI / use-cases to read or mutate workout data.
 */
class WorkoutRepository(
    private val db: RunTrainingDatabase,
    private val blobs: WorkoutBlobStore,
) {

    private val dao = db.workoutDao()

    sealed interface UpsertResult {
        data class NewImport(val workoutId: Long) : UpsertResult
        data class AlreadyImported(val workoutId: Long) : UpsertResult
    }

    /**
     * Insert a fresh workout, deduping by content hash. If a workout with
     * the same hash already exists, returns AlreadyImported without
     * mutating. Otherwise saves the blob and inserts workout + steps +
     * repeat groups in one DB transaction.
     */
    suspend fun upsert(workout: Workout, rawBytes: ByteArray): UpsertResult {
        val existing = dao.findByHash(workout.contentHash)
        if (existing != null) return UpsertResult.AlreadyImported(existing.id)

        val hash = blobs.save(rawBytes)
        check(hash == workout.contentHash) {
            "Workout.contentHash (${workout.contentHash}) disagrees with computed blob hash ($hash)."
        }

        val (workoutEntity, stepEntities, repeatEntities) = workout.toEntities(
            blobRelativePath = blobs.relativePath(hash),
        )

        return db.withTransaction {
            val newWorkoutId = dao.insertWorkout(workoutEntity)
            // Resolve repeat-group surrogate ids: store fresh inserts, build a
            // map from the original domain id (which was 0 for new groups) +
            // orderInWorkout → new entity id.
            val groupOriginalIds = workout.repeatGroups.map { it.id }
            val groupsForInsert = repeatEntities.map { it.copy(workoutId = newWorkoutId, id = 0) }
            val newGroupIds = dao.insertRepeatGroups(groupsForInsert)
            val groupIdRemap: Map<Long, Long> = groupOriginalIds
                .zip(newGroupIds)
                .toMap()

            val stepsForInsert = stepEntities.map { se ->
                se.copy(
                    workoutId = newWorkoutId,
                    id = 0,
                    repeatGroupId = se.repeatGroupId?.let { old -> groupIdRemap[old] },
                )
            }
            dao.insertSteps(stepsForInsert)
            UpsertResult.NewImport(newWorkoutId)
        }
    }

    /** Observe library list, sorted most-recent-imported first (FR-010). */
    fun observeAll(): Flow<List<Workout>> =
        dao.observeAll().transform { entities ->
            val workouts = entities.map { e ->
                (dao.loadWithChildren(e.id)
                    ?: WorkoutWithChildren(workout = e, steps = emptyList(), repeatGroups = emptyList())
                ).toDomain()
            }
            emit(workouts)
        }

    suspend fun get(id: Long): Workout? {
        return dao.loadWithChildren(id)?.toDomain()
    }

    suspend fun findByHash(hash: String): Long? = dao.findByHash(hash)?.id

    suspend fun rename(id: Long, newName: String) {
        require(newName.isNotBlank()) { "Display name must not be blank." }
        dao.updateDisplayName(id, newName)
    }

    suspend fun updateTss(id: Long, tss: Double?) {
        dao.updateTss(id, tss)
    }

    /** Stamp the workout as completed at wall-clock `epochMs`. Persists across launches. */
    suspend fun markCompleted(id: Long, epochMs: Long = System.currentTimeMillis()) {
        dao.updateLastCompleted(id, epochMs)
    }

    suspend fun delete(id: Long) {
        val existing = dao.findById(id) ?: return
        dao.deleteById(id) // cascades to step / repeat_group via FK
        // Best-effort blob cleanup
        existing.blobRelativePath.substringAfterLast('/').substringBeforeLast('.').let { sha ->
            if (sha.isNotEmpty()) blobs.delete(sha)
        }
    }

    /** Recompute TSS for every workout when threshold pace changes (T053). */
    suspend fun recomputeAllTss(compute: (Workout) -> Double?) {
        val entities = dao.getAll()
        for (e in entities) {
            val full = dao.loadWithChildren(e.id)?.toDomain() ?: continue
            val newTss = compute(full)
            if (newTss != e.tss) {
                dao.updateTss(e.id, newTss)
            }
        }
    }
}
