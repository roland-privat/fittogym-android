package com.example.runtraining.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.nav.Routes
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.settings.AppSettings
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.workout.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetailsUiState(
    val workout: Workout? = null,
    val settings: AppSettings = AppSettings.DEFAULT,
    val isAlreadyImported: Boolean = false,
    val isLoading: Boolean = true,
)

class DetailsViewModel(
    private val workoutId: Long,
    private val repo: WorkoutRepository,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val w = repo.get(workoutId)
            val s = settings.settings.first()
            _state.value = DetailsUiState(workout = w, settings = s, isLoading = false)
        }
    }

    fun markAlreadyImported() {
        _state.value = _state.value.copy(isAlreadyImported = true)
    }

    fun rename(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.rename(workoutId, trimmed)
            _state.value = _state.value.copy(
                workout = _state.value.workout?.copy(displayName = trimmed),
            )
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.delete(workoutId)
            onDeleted()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                val sshExtras: SavedStateHandle = this.createSavedStateHandle()
                val id = sshExtras.get<Long>(Routes.ARG_WORKOUT_ID)
                    ?: error("Details route requires workoutId nav arg")
                DetailsViewModel(id, app.container.workoutRepository, app.container.settings)
            }
        }
    }
}
