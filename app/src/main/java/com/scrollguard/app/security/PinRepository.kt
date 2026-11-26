package com.scrollguard.app.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrollguard_pin_store")

@Singleton
class PinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinCrypto: PinCrypto,
    private val appPreferences: ScrollGuardPreferences
) {
    private val store = context.pinDataStore

    private object Keys {
        val SEALED_PIN_RECORD = stringPreferencesKey("sealed_pin_record")
        val SEALED_RECOVERY_RECORD = stringPreferencesKey("sealed_recovery_record")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("lockout_until_ms")
    }

    val hasPinSet: Flow<Boolean> = store.data.map { prefs ->
        !prefs[Keys.SEALED_PIN_RECORD].isNullOrBlank()
    }

    suspend fun isPinConfigured(): Boolean = hasPinSet.first()

    suspend fun savePinRecord(pinRecord: PinHashRecord, recoveryRecord: PinHashRecord?) {
        val serializedPin = "${pinRecord.iterations}|${pinRecord.saltBase64}|${pinRecord.hashBase64}"
        val sealedPin = pinCrypto.sealWithKeystore(serializedPin)
        val sealedRecovery = recoveryRecord?.let {
            pinCrypto.sealWithKeystore("${it.iterations}|${it.saltBase64}|${it.hashBase64}")
        }
        store.edit { prefs ->
            prefs[Keys.SEALED_PIN_RECORD] = sealedPin
            if (sealedRecovery != null) {
                prefs[Keys.SEALED_RECOVERY_RECORD] = sealedRecovery
            }
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
        appPreferences.setPinPresent(true)
    }

    suspend fun getPinRecord(): PinHashRecord? {
        val sealed = store.data.first()[Keys.SEALED_PIN_RECORD] ?: return null
        return parseRecord(pinCrypto.unsealWithKeystore(sealed))
    }

    suspend fun getRecoveryRecord(): PinHashRecord? {
        val sealed = store.data.first()[Keys.SEALED_RECOVERY_RECORD] ?: return null
        return parseRecord(pinCrypto.unsealWithKeystore(sealed))
    }

    suspend fun getFailedAttempts(): Int = store.data.first()[Keys.FAILED_ATTEMPTS] ?: 0

    suspend fun getLockoutUntilMs(): Long = store.data.first()[Keys.LOCKOUT_UNTIL_MS] ?: 0L

    suspend fun recordFailedAttempt(attempts: Int, lockoutUntilMs: Long) {
        store.edit {
            it[Keys.FAILED_ATTEMPTS] = attempts
            it[Keys.LOCKOUT_UNTIL_MS] = lockoutUntilMs
        }
    }

    suspend fun resetFailedAttempts() {
        store.edit {
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun clearPin() {
        store.edit { it.clear() }
        appPreferences.setPinPresent(false)
        appPreferences.setStrictModeEnabled(false)
    }

    private fun parseRecord(raw: String?): PinHashRecord? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|", limit = 3)
        if (parts.size != 3) return null
        val iterations = parts[0].toIntOrNull() ?: return null
        return PinHashRecord(
            saltBase64 = parts[1],
            hashBase64 = parts[2],
            iterations = iterations
        )
    }
}
