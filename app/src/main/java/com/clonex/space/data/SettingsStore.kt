package com.clonex.space.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clonex_settings"
)

/**
 * CloneX settings, stored with Jetpack DataStore (Preferences).
 * Small key/value settings only — the clone library itself is Room.
 *
 * v2.0: the app is ALREADY PRO — every feature is unlocked from the
 * start, max 10 clones per app by default. There is NO VIP flag, NO
 * VIP badge, NO upsell and NO gating of any kind.
 *  - Secret Zone PIN: a 4-digit PIN gating the private-clone section.
 *    This is a privacy screen, NOT encryption — the PIN is stored as a
 *    plain string in the app-private DataStore and only keeps casual
 *    eyes out of the Secret Zone.
 */
class SettingsStore(private val context: Context) {

    companion object {
        private val MAX_CLONES = intPreferencesKey("max_clones")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val SECRET_PIN = stringPreferencesKey("secret_zone_pin")

        const val DEFAULT_MAX_CLONES = 10
        const val MAX_CLONES_CAP = 10
    }

    val maxClones: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[MAX_CLONES] ?: DEFAULT_MAX_CLONES
    }

    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[NOTIFICATIONS] ?: true
    }

    /** Empty string = no PIN set yet. */
    val secretPin: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SECRET_PIN] ?: ""
    }

    suspend fun setMaxClones(value: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[MAX_CLONES] = value.coerceIn(1, MAX_CLONES_CAP)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATIONS] = enabled
        }
    }

    suspend fun setSecretPin(pin: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SECRET_PIN] = pin
        }
    }
}
