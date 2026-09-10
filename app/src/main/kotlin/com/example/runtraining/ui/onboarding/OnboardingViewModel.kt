package com.example.runtraining.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.settings.DisplayUnit
import com.example.runtraining.workout.tss.TssCalculator
import kotlinx.coroutines.launch

/**
 * Backs the first-run tour: persists the user's basic settings and marks
 * onboarding complete. Demo workouts are already seeded by the container, so
 * setting a threshold pace here recomputes their TSS.
 */
class OnboardingViewModel(
    private val settings: AppSettingsRepository,
    private val repo: WorkoutRepository,
) : ViewModel() {

    /** Parse a user-typed "m:ss" pace → sec/km, or null if blank/invalid. */
    fun parsePace(raw: String): Int? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        val m = Regex("""^(\d+):([0-5]\d)$""").matchEntire(cleaned) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    fun finish(thresholdPaceSecPerKm: Int?, unit: DisplayUnit, onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setThresholdPace(thresholdPaceSecPerKm)
            settings.setDisplayUnit(unit)
            if (thresholdPaceSecPerKm != null) {
                repo.recomputeAllTss { workout -> TssCalculator.compute(workout, thresholdPaceSecPerKm) }
            }
            settings.setOnboardingComplete(true)
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                OnboardingViewModel(app.container.settings, app.container.workoutRepository)
            }
        }
    }
}
