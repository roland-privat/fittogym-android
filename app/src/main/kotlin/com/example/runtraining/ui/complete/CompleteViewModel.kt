package com.example.runtraining.ui.complete

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.nav.Routes
import com.example.runtraining.service.WorkoutForegroundService
import com.example.runtraining.workout.engine.RunSessionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Snapshots the in-memory completion summary from `WorkoutForegroundService` if
 * still bound, otherwise falls back to the persisted Workout's planned values.
 * Reset (via Done) sends ACTION_STOP to the service so the engine returns to
 * IDLE and the foreground notification clears.
 */
class CompleteViewModel(
    private val app: Application,
    private val workoutId: Long,
    private val stoppedEarlyArg: Boolean,
) : AndroidViewModel(app) {

    private val _summary = MutableStateFlow<RunSessionEngine.CompletionSummary?>(null)
    val summary: StateFlow<RunSessionEngine.CompletionSummary?> = _summary.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? WorkoutForegroundService.LocalBinder)?.service ?: return
            val live = svc.engine.buildCompletionSummary()
            if (live != null) {
                _summary.value = live
            } else if (_summary.value == null) {
                seedFromRepository()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { /* no-op */ }
    }

    init {
        // Try to read the engine's summary first; fallback comes from the
        // repository if the service has already detached.
        app.applicationContext.bindService(
            Intent(app.applicationContext, WorkoutForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun seedFromRepository() {
        viewModelScope.launch {
            val w = (app as RunTrainingApp).container.workoutRepository.get(workoutId)
            if (w != null && _summary.value == null) {
                _summary.value = RunSessionEngine.CompletionSummary(
                    workoutDisplayName = w.displayName,
                    plannedDurationSec = w.plannedDurationSec,
                    actualElapsedSec = w.plannedDurationSec,
                    averageHrBpm = null,
                    plannedTss = w.tss,
                    wasStoppedEarly = stoppedEarlyArg,
                )
            }
        }
    }

    /** Returning to selection: tell the service to leave foreground + idle the engine. */
    fun dismiss() {
        val ctx = app.applicationContext
        ctx.startService(
            WorkoutForegroundService.simpleIntent(ctx, WorkoutForegroundService.ACTION_RESET),
        )
    }

    override fun onCleared() {
        runCatching { app.applicationContext.unbindService(connection) }
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                val handle = this.createSavedStateHandle()
                val id = handle.get<Long>(Routes.ARG_WORKOUT_ID) ?: error("Complete route requires workoutId")
                val stoppedEarly = handle.get<Boolean>(Routes.ARG_STOPPED_EARLY) ?: false
                CompleteViewModel(app, id, stoppedEarly)
            }
        }
    }
}
