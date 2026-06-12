package com.example.runtraining.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.settings.AppSettings
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.workout.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    /** Default per Spec FR-010. */
    LAST_ADDED("Last added"),
    NAME("Name"),
    TSS("TSS"),
    LENGTH("Length"),
    LAST_COMPLETED("Last completed"),
}

data class SelectionUiState(
    val workouts: List<Workout> = emptyList(),
    val settings: AppSettings = AppSettings.DEFAULT,
    val sortOrder: SortOrder = SortOrder.LAST_ADDED,
)

class SelectionViewModel(
    private val repo: WorkoutRepository,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.LAST_ADDED)

    val uiState: StateFlow<SelectionUiState> =
        combine(repo.observeAll(), settings.settings, _sortOrder) { workouts, s, order ->
            SelectionUiState(
                workouts = workouts.sortedWith(order.comparator),
                settings = s,
                sortOrder = order,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SelectionUiState(),
        )

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun delete(workoutId: Long) {
        viewModelScope.launch { repo.delete(workoutId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                SelectionViewModel(app.container.workoutRepository, app.container.settings)
            }
        }
    }
}

/** Sort comparators. Null-safe: workouts with null sort key go LAST. */
private val SortOrder.comparator: Comparator<Workout>
    get() = when (this) {
        SortOrder.LAST_ADDED -> compareByDescending { it.importedAtEpochMs }
        SortOrder.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortOrder.TSS -> compareByDescending<Workout> { it.tss ?: Double.NEGATIVE_INFINITY }
            .thenByDescending { it.importedAtEpochMs }
        SortOrder.LENGTH -> compareByDescending<Workout> { it.plannedDurationSec }
            .thenByDescending { it.importedAtEpochMs }
        SortOrder.LAST_COMPLETED -> compareByDescending<Workout> { it.lastCompletedAtEpochMs ?: Long.MIN_VALUE }
            .thenByDescending { it.importedAtEpochMs }
    }
