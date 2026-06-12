package com.example.runtraining.workout.import

import android.content.ContentResolver
import android.net.Uri
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.persistence.files.WorkoutBlobStore
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.util.Log
import com.example.runtraining.workout.fit.DecodedToDomain.toDomain
import com.example.runtraining.workout.fit.FitDecodeResult
import com.example.runtraining.workout.fit.FitDecoder
import com.example.runtraining.workout.fit.RejectReason
import com.example.runtraining.workout.tss.TssCalculator
import kotlinx.coroutines.flow.first

/**
 * Reads a `.fit` file from a URI, deduplicates by content hash, decodes,
 * computes TSS (if threshold pace is set), and persists.
 *
 * Sealed result lets the UI route correctly per Spec FR-007 / FR-008 / and
 * also surface a non-crashing error for malformed inputs per FR-033.
 */
class ImportWorkoutUseCase(
    private val resolver: ContentResolver,
    private val blobStore: WorkoutBlobStore,
    private val repository: WorkoutRepository,
    private val settings: AppSettingsRepository,
) {

    sealed interface Result {
        data class NewImport(val workoutId: Long) : Result
        data class AlreadyImported(val workoutId: Long) : Result
        data class Rejected(val reason: RejectReason, val detail: String?) : Result
    }

    suspend operator fun invoke(uri: Uri): Result {
        // 1) Read bytes from the source URI.
        val bytes: ByteArray = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Rejected(RejectReason.MALFORMED, "could not open input stream")
        } catch (t: Throwable) {
            Log.w("Import: failed to read URI $uri", t)
            return Result.Rejected(RejectReason.MALFORMED, t.message)
        }
        if (bytes.isEmpty()) {
            return Result.Rejected(RejectReason.MALFORMED, "empty payload")
        }

        // 2) Hash + dedupe.
        val hash = blobStore.sha256(bytes)
        val existingId = repository.findByHash(hash)
        if (existingId != null) {
            Log.d("Import: dedup hit for $hash → existing id=$existingId")
            return Result.AlreadyImported(existingId)
        }

        // 3) Decode.
        val decoded = when (val r = FitDecoder.decode(bytes)) {
            is FitDecodeResult.Ok -> r.workout
            is FitDecodeResult.Rejected -> {
                Log.w("Import: FIT rejected ${r.reason} ${r.detail}")
                return Result.Rejected(r.reason, r.detail)
            }
        }

        // 4) Filename: prefer DocumentsContract DISPLAY_NAME, fall back to URI lastPathSegment.
        val originalFilename = resolveFilename(uri) ?: "workout.fit"

        // 5) Map to domain (effective durations, totals, default name).
        val baseWorkout = decoded.toDomain(
            originalFilename = originalFilename,
            contentHash = hash,
            importedAtEpochMs = System.currentTimeMillis(),
        )

        // 6) Compute TSS from current threshold pace (may be null → "—").
        val threshold = settings.settings.first().thresholdPaceSecPerKm
        val tss = TssCalculator.compute(baseWorkout, threshold)
        val withTss = baseWorkout.copy(tss = tss)

        // 7) Persist (Mapper validation may throw → translate to Rejected).
        return try {
            when (val r = repository.upsert(withTss, bytes)) {
                is WorkoutRepository.UpsertResult.NewImport -> Result.NewImport(r.workoutId)
                is WorkoutRepository.UpsertResult.AlreadyImported -> Result.AlreadyImported(r.workoutId)
            }
        } catch (t: IllegalArgumentException) {
            Log.w("Import: persist validation failed", t)
            Result.Rejected(RejectReason.MALFORMED, t.message)
        }
    }

    private fun resolveFilename(uri: Uri): String? {
        val cursor = runCatching {
            resolver.query(uri, null, null, null, null)
        }.getOrNull() ?: return uri.lastPathSegment?.substringAfterLast('/')
        cursor.use { c ->
            val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && c.moveToFirst()) {
                val n = c.getString(nameIdx)
                if (!n.isNullOrBlank()) return n
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
