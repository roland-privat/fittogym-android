package com.example.runtraining

import android.app.Application
import android.content.Context
import com.example.runtraining.ble.HrmClient
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.persistence.WorkoutResultRepository
import com.example.runtraining.persistence.db.RunTrainingDatabase
import com.example.runtraining.persistence.files.WorkoutBlobStore
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.workout.import.ImportWorkoutUseCase

/**
 * Process-wide container of singletons. No DI framework — per Constitution
 * §III, manual wiring is enough for a personal app.
 */
class AppContainer(context: Context) {

    private val appCtx = context.applicationContext

    val database: RunTrainingDatabase by lazy { RunTrainingDatabase.get(appCtx) }
    val blobStore: WorkoutBlobStore by lazy { WorkoutBlobStore(appCtx) }
    val settings: AppSettingsRepository by lazy { AppSettingsRepository(appCtx) }
    val workoutRepository: WorkoutRepository by lazy { WorkoutRepository(database, blobStore) }
    val workoutResultRepository: WorkoutResultRepository by lazy { WorkoutResultRepository(database.workoutResultDao()) }
    val hrmClient: HrmClient by lazy { HrmClient(appCtx) }

    fun importUseCase(): ImportWorkoutUseCase = ImportWorkoutUseCase(
        resolver = appCtx.contentResolver,
        blobStore = blobStore,
        repository = workoutRepository,
        settings = settings,
    )

    /**
     * On first run, import the bundled demo `.fit` workouts from assets so the
     * library isn't empty. Idempotent: gated by a flag and dedup-safe.
     */
    suspend fun seedDemoWorkoutsIfNeeded() {
        if (settings.isDemosSeeded()) return
        val importer = importUseCase()
        val dir = "demo-workouts"
        val names = runCatching { appCtx.assets.list(dir) }.getOrNull()
            ?.filter { it.endsWith(".fit") }?.sorted().orEmpty()
        for (name in names) {
            val bytes = runCatching { appCtx.assets.open("$dir/$name").use { it.readBytes() } }.getOrNull() ?: continue
            runCatching { importer.importBytes(bytes, name) }
        }
        settings.markDemosSeeded()
    }
}

class RunTrainingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience extension for Composables and ViewModels needing the container. */
val Context.appContainer: AppContainer
    get() = (applicationContext as RunTrainingApp).container
