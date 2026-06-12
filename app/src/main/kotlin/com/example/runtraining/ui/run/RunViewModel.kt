package com.example.runtraining.ui.run

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.nav.Routes
import com.example.runtraining.service.WorkoutForegroundService
import com.example.runtraining.workout.engine.RunUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Binds to `WorkoutForegroundService` for the lifetime of this ViewModel,
 * surfaces the engine's `uiState`, and translates user actions into service
 * intents (Start / Pause / Stop / Step Forward / Step Backward).
 */
class RunViewModel(
    private val app: Application,
    private val workoutId: Long,
) : AndroidViewModel(app) {

    private val _service = MutableStateFlow<WorkoutForegroundService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? WorkoutForegroundService.LocalBinder ?: return
            _service.value = b.service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    val uiState: StateFlow<RunUiState?> = kotlinx.coroutines.flow.flow {
        // Wait for the service to bind, then re-emit its uiState.
        _service.collect { svc ->
            if (svc == null) {
                emit(null)
            } else {
                svc.engine.uiState.collect { state -> emit(state) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        // Foreground-start the service with this workout primed, then bind.
        val ctx: Context = app.applicationContext
        ctx.startService(WorkoutForegroundService.primeIntent(ctx, workoutId))
        ctx.bindService(
            Intent(ctx, WorkoutForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun onStart() = sendAction(WorkoutForegroundService.ACTION_START)
    fun onPause() = sendAction(WorkoutForegroundService.ACTION_PAUSE)
    fun onStop() = sendAction(WorkoutForegroundService.ACTION_STOP)
    fun onStepForward() = sendAction(WorkoutForegroundService.ACTION_STEP_FWD)
    fun onStepBackward() = sendAction(WorkoutForegroundService.ACTION_STEP_BACK)

    /** US4 — enable the always-on-top mini view (caller must have already granted SYSTEM_ALERT_WINDOW). */
    fun enableMiniView() = sendAction(WorkoutForegroundService.ACTION_ENABLE_MINI_VIEW)

    /** Disable the mini view (e.g., when the user returns to this screen). */
    fun disableMiniView() = sendAction(WorkoutForegroundService.ACTION_DISABLE_MINI_VIEW)

    private fun sendAction(action: String) {
        val ctx: Context = app.applicationContext
        ctx.startService(WorkoutForegroundService.simpleIntent(ctx, action))
    }

    override fun onCleared() {
        runCatching { app.applicationContext.unbindService(connection) }
        super.onCleared()
    }

    /** Helper for the Run screen to fetch the priming workout for the timeline. */
    fun workoutAsync(onLoaded: (com.example.runtraining.workout.model.Workout?) -> Unit) {
        viewModelScope.launch {
            val w = (app as RunTrainingApp).container.workoutRepository.get(workoutId)
            onLoaded(w)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                val handle = this.createSavedStateHandle()
                val id = handle.get<Long>(Routes.ARG_WORKOUT_ID) ?: error("Run route requires workoutId")
                RunViewModel(app, id)
            }
        }
    }
}
