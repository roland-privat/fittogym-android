package com.example.runtraining.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.runtraining.RunTrainingApp
import com.example.runtraining.ble.HrmClient
import com.example.runtraining.persistence.WorkoutRepository
import com.example.runtraining.settings.AppSettings
import com.example.runtraining.settings.AppSettingsRepository
import com.example.runtraining.workout.tss.TssCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OptionsViewModel(
    private val settings: AppSettingsRepository,
    private val repo: WorkoutRepository,
    private val hrmClient: HrmClient,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings.DEFAULT,
    )

    val hrmConnectionState: StateFlow<HrmClient.ConnectionState> = hrmClient.state
    val liveHrSample: StateFlow<com.example.runtraining.ble.HrSample?> = hrmClient.samples

    private val _scanResults = MutableStateFlow<List<HrmClient.DiscoveredDevice>>(emptyList())
    val scanResults: StateFlow<List<HrmClient.DiscoveredDevice>> = _scanResults.asStateFlow()

    private var scanJob: Job? = null

    /** Parse a user-typed "m:ss" string → sec/km. Returns null if not valid. */
    fun parseThresholdPaceText(raw: String): Int? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        val match = Regex("""^(\d+):([0-5]\d)$""").matchEntire(cleaned) ?: return null
        val minutes = match.groupValues[1].toInt()
        val seconds = match.groupValues[2].toInt()
        return minutes * 60 + seconds
    }

    /** Render an Int sec/km back to the "m:ss" mask used in the text field. */
    fun formatThresholdPaceText(secPerKm: Int?): String {
        if (secPerKm == null) return ""
        val m = secPerKm / 60
        val s = secPerKm % 60
        return "%d:%02d".format(m, s)
    }

    fun saveThresholdPace(secPerKm: Int?) {
        viewModelScope.launch {
            settings.setThresholdPace(secPerKm)
            // Recompute TSS for the whole library (T053).
            repo.recomputeAllTss { workout -> TssCalculator.compute(workout, secPerKm) }
        }
    }

    fun clearThresholdPace() {
        saveThresholdPace(null)
    }

    fun setDisplayUnit(unit: com.example.runtraining.settings.DisplayUnit) {
        viewModelScope.launch { settings.setDisplayUnit(unit) }
    }

    /** Begin a 15-second BLE scan, surfacing each discovery via [scanResults]. */
    fun startScan() {
        scanJob?.cancel()
        _scanResults.value = emptyList()
        scanJob = viewModelScope.launch {
            hrmClient.scan().collect { device ->
                _scanResults.value = (_scanResults.value + device)
                    .distinctBy { it.deviceId }
                    .sortedByDescending { it.rssi }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun connect(device: HrmClient.DiscoveredDevice) {
        stopScan()
        viewModelScope.launch {
            settings.setLastPairedDeviceId(device.deviceId)
            hrmClient.connect(device.deviceId)
        }
    }

    fun forget() {
        viewModelScope.launch {
            hrmClient.disconnect()
            settings.setLastPairedDeviceId(null)
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RunTrainingApp
                OptionsViewModel(
                    app.container.settings,
                    app.container.workoutRepository,
                    app.container.hrmClient,
                )
            }
        }
    }
}
