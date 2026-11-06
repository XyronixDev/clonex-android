package com.clonex.space.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clonex_settings"
)

/**
 * CloneX settings, stored with Jetpack DataStore (Preferences).
 * Small key/value settings only — the clone library itself is Room.
 */
class SettingsStore(private val context: Context) {

    companion object {
        private val MAX_CLONES = intPreferencesKey("max_clones")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")

        const val DEFAULT_MAX_CLONES = 5
    }

    val maxClones: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[MAX_CLONES] ?: DEFAULT_MAX_CLONES
    }

    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[NOTIFICATIONS] ?: true
    }

    suspend fun setMaxClones(value: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[MAX_CLONES] = value.coerceIn(1, 10)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATIONS] = enabled
        }
    }
}
