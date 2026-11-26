package com.scrollguard.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrollguard.app.domain.model.StrictModeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

val Context.scrollGuardDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrollguard_prefs")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Singleton
class ScrollGuardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.scrollGuardDataStore

    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val CONSENT_ACCEPTED = booleanPreferencesKey("consent_accepted")
        val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
        val STRICT_MODE_ENABLED = booleanPreferencesKey("strict_mode_enabled")
        val FOCUS_SESSION_ACTIVE = booleanPreferencesKey("focus_session_active")
        val FOCUS_SESSION_END_MS = longPreferencesKey("focus_session_end_ms")
        val FOCUS_SESSION_DURATION_MIN = intPreferencesKey("focus_session_duration_min")
        val DAILY_TEMP_UNLOCK_LIMIT = intPreferencesKey("daily_temp_unlock_limit")
        val TEMP_UNLOCKS_USED_TODAY = intPreferencesKey("temp_unlocks_used_today")
        val TEMP_UNLOCK_DATE = stringPreferencesKey("temp_unlock_date")
        val TEMP_UNLOCK_PACKAGE = stringPreferencesKey("temp_unlock_package")
        val TEMP_UNLOCK_EXPIRES_MS = longPreferencesKey("temp_unlock_expires_ms")
        val BLOCKING_PAUSED = booleanPreferencesKey("blocking_paused")
        val PIN_HASH_PRESENT = booleanPreferencesKey("pin_hash_present")
        val ENTRY_POINT_SUPPRESSION_ENABLED = booleanPreferencesKey("entry_point_suppression_enabled")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val consentAccepted: Flow<Boolean> = dataStore.data.map { it[Keys.CONSENT_ACCEPTED] ?: false }
    val diagnosticsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DIAGNOSTICS_ENABLED] ?: false }
    val entryPointSuppressionEnabled: Flow<Boolean> = dataStore.data.map {
        it[Keys.ENTRY_POINT_SUPPRESSION_ENABLED] ?: true
    }

    val strictModeState: Flow<StrictModeState> = dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val storedDate = prefs[Keys.TEMP_UNLOCK_DATE] ?: today
        val usedToday = if (storedDate == today) prefs[Keys.TEMP_UNLOCKS_USED_TODAY] ?: 0 else 0
        val nowMs = System.currentTimeMillis()
        val focusEndMs = prefs[Keys.FOCUS_SESSION_END_MS] ?: 0L
        val focusActive = (prefs[Keys.FOCUS_SESSION_ACTIVE] ?: false) && focusEndMs > nowMs
        val unlockExpiresMs = prefs[Keys.TEMP_UNLOCK_EXPIRES_MS] ?: 0L
        val unlockPkg = if (unlockExpiresMs > nowMs) prefs[Keys.TEMP_UNLOCK_PACKAGE] else null

        StrictModeState(
            isStrictModeEnabled = prefs[Keys.STRICT_MODE_ENABLED] ?: false,
            hasPinSet = prefs[Keys.PIN_HASH_PRESENT] ?: false,
            focusSessionActive = focusActive,
            focusSessionEndEpochMs = if (focusActive) focusEndMs else 0L,
            focusSessionDurationMinutes = prefs[Keys.FOCUS_SESSION_DURATION_MIN] ?: 60,
            dailyTempUnlockLimit = prefs[Keys.DAILY_TEMP_UNLOCK_LIMIT] ?: 2,
            tempUnlocksUsedToday = usedToday,
            tempUnlockPackage = unlockPkg,
            tempUnlockExpiresEpochMs = if (unlockPkg != null) unlockExpiresMs else 0L,
            isBlockingPaused = prefs[Keys.BLOCKING_PAUSED] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean, consentAccepted: Boolean) {
        dataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = completed
            it[Keys.CONSENT_ACCEPTED] = consentAccepted
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DIAGNOSTICS_ENABLED] = enabled }
    }

    suspend fun setEntryPointSuppressionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENTRY_POINT_SUPPRESSION_ENABLED] = enabled }
    }

    suspend fun setStrictModeEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.STRICT_MODE_ENABLED] = enabled
            if (enabled) {
                it[Keys.BLOCKING_PAUSED] = false
            }
        }
    }

    suspend fun setFocusSessionDurationMinutes(minutes: Int) {
        dataStore.edit { it[Keys.FOCUS_SESSION_DURATION_MIN] = minutes.coerceIn(5, 480) }
    }

    suspend fun startFocusSession(durationMinutes: Int, endEpochMs: Long) {
        dataStore.edit {
            it[Keys.FOCUS_SESSION_ACTIVE] = true
            it[Keys.FOCUS_SESSION_DURATION_MIN] = durationMinutes
            it[Keys.FOCUS_SESSION_END_MS] = endEpochMs
            it[Keys.BLOCKING_PAUSED] = false
        }
    }

    suspend fun endFocusSession() {
        dataStore.edit {
            it[Keys.FOCUS_SESSION_ACTIVE] = false
            it[Keys.FOCUS_SESSION_END_MS] = 0L
        }
    }

    suspend fun setBlockingPaused(paused: Boolean) {
        dataStore.edit { it[Keys.BLOCKING_PAUSED] = paused }
    }

    suspend fun grantTemporaryUnlock(packageName: String, expiresEpochMs: Long) {
        val today = LocalDate.now().toString()
        dataStore.edit { prefs ->
            val currentCount = if (prefs[Keys.TEMP_UNLOCK_DATE] == today) {
                prefs[Keys.TEMP_UNLOCKS_USED_TODAY] ?: 0
            } else {
                0
            }
            prefs[Keys.TEMP_UNLOCK_DATE] = today
            prefs[Keys.TEMP_UNLOCKS_USED_TODAY] = currentCount + 1
            prefs[Keys.TEMP_UNLOCK_PACKAGE] = packageName
            prefs[Keys.TEMP_UNLOCK_EXPIRES_MS] = expiresEpochMs
        }
    }

    suspend fun clearTemporaryUnlock() {
        dataStore.edit {
            it.remove(Keys.TEMP_UNLOCK_PACKAGE)
            it[Keys.TEMP_UNLOCK_EXPIRES_MS] = 0L
        }
    }

    suspend fun setDailyTempUnlockLimit(limit: Int) {
        dataStore.edit { it[Keys.DAILY_TEMP_UNLOCK_LIMIT] = limit.coerceIn(0, 10) }
    }

    suspend fun setPinPresent(present: Boolean) {
        dataStore.edit { it[Keys.PIN_HASH_PRESENT] = present }
    }
}
