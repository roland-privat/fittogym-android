package com.example.runtraining.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app-settings",
)

/**
 * Single source of truth for `AppSettings`. Wraps DataStore Preferences.
 * See specs/001-core-workout-flow/data-model.md §2.
 */
class AppSettingsRepository(context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.appSettingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            thresholdPaceSecPerKm = prefs[Keys.THRESHOLD_PACE],
            displayUnit = prefs[Keys.DISPLAY_UNIT]
                ?.let { runCatching { DisplayUnit.valueOf(it) }.getOrDefault(DisplayUnit.PACE) }
                ?: DisplayUnit.PACE,
            lastPairedDeviceId = prefs[Keys.LAST_PAIRED_DEVICE_ID],
        )
    }

    suspend fun setThresholdPace(secPerKm: Int?) {
        store.edit { prefs ->
            if (secPerKm == null) prefs.remove(Keys.THRESHOLD_PACE)
            else prefs[Keys.THRESHOLD_PACE] = secPerKm
        }
    }

    suspend fun setDisplayUnit(unit: DisplayUnit) {
        store.edit { prefs -> prefs[Keys.DISPLAY_UNIT] = unit.name }
    }

    suspend fun setLastPairedDeviceId(deviceId: String?) {
        store.edit { prefs ->
            if (deviceId == null) prefs.remove(Keys.LAST_PAIRED_DEVICE_ID)
            else prefs[Keys.LAST_PAIRED_DEVICE_ID] = deviceId
        }
    }

    private object Keys {
        val THRESHOLD_PACE = intPreferencesKey("threshold_pace_sec_per_km")
        val DISPLAY_UNIT = stringPreferencesKey("display_unit")
        val LAST_PAIRED_DEVICE_ID = stringPreferencesKey("last_paired_device_id")
    }
}
