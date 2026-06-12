package com.example.runtraining.persistence.files

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Content-addressed store for raw `.fit` blobs in app-private internal storage
 * at `context.filesDir/workouts/<sha>.fit`. Idempotent on save (same content
 * → same path → no duplicate write).
 *
 * See specs/001-core-workout-flow/data-model.md §1.
 */
class WorkoutBlobStore(context: Context) {

    private val rootDir: File = File(context.filesDir, "workouts")

    /** SHA-256 hex digest of `bytes`. */
    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            if (v < 0x10) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    /** Saves bytes; returns the sha. Idempotent if the same content was saved before. */
    fun save(bytes: ByteArray): String {
        val hash = sha256(bytes)
        val target = fileFor(hash)
        if (!target.exists()) {
            if (!rootDir.exists()) rootDir.mkdirs()
            target.writeBytes(bytes)
        }
        return hash
    }

    fun load(sha: String): ByteArray? {
        val f = fileFor(sha)
        return if (f.exists()) f.readBytes() else null
    }

    fun delete(sha: String) {
        fileFor(sha).delete()
    }

    /** Relative path stored in the DB row for traceability. */
    fun relativePath(sha: String): String = "workouts/$sha.fit"

    private fun fileFor(sha: String): File = File(rootDir, "$sha.fit")
}
