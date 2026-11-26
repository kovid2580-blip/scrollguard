package com.scrollguard.app.security

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class PinActionResult {
    data class Success(val recoveryCode: String? = null) : PinActionResult()
    data class Error(val message: String) : PinActionResult()
}

sealed class PinVerificationResult {
    data object Verified : PinVerificationResult()
    data object NotSet : PinVerificationResult()
    data class Invalid(val attemptsRemainingBeforeDelay: Int) : PinVerificationResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerificationResult()
}

@Singleton
class PinManager @Inject constructor(
    private val pinCrypto: PinCrypto,
    private val pinRepository: PinRepository
) {
    val hasPinSet: Flow<Boolean> = pinRepository.hasPinSet

    suspend fun createPin(pin: CharArray, confirmation: CharArray): PinActionResult {
        return try {
            val validationError = validatePinPair(pin, confirmation)
            if (validationError != null) return PinActionResult.Error(validationError)

            val pinRecord = pinCrypto.createHashRecord(pin)
            val recoveryCode = pinCrypto.generateRecoveryCode()
            val recoveryChars = recoveryCode.toCharArray()
            val recoveryRecord = try {
                pinCrypto.createHashRecord(recoveryChars)
            } finally {
                PinCrypto.wipe(recoveryChars)
            }

            pinRepository.savePinRecord(pinRecord, recoveryRecord)
            PinActionResult.Success(recoveryCode = recoveryCode)
        } finally {
            PinCrypto.wipe(pin)
            PinCrypto.wipe(confirmation)
        }
    }

    suspend fun verifyPin(pin: CharArray): PinVerificationResult {
        return try {
            val nowMs = System.currentTimeMillis()
            val lockoutUntil = pinRepository.getLockoutUntilMs()
            if (lockoutUntil > nowMs) {
                val remaining = ((lockoutUntil - nowMs) / 1000L).coerceAtLeast(1L)
                return PinVerificationResult.LockedOut(remaining)
            }

            val record = pinRepository.getPinRecord() ?: return PinVerificationResult.NotSet
            if (pinCrypto.verifyPin(pin, record)) {
                pinRepository.resetFailedAttempts()
                PinVerificationResult.Verified
            } else {
                val failures = pinRepository.getFailedAttempts() + 1
                val delaySeconds = computeDelaySeconds(failures)
                val nextLockout = if (delaySeconds > 0L) nowMs + delaySeconds * 1000L else 0L
                pinRepository.recordFailedAttempt(failures, nextLockout)
                if (delaySeconds > 0L) {
                    PinVerificationResult.LockedOut(delaySeconds)
                } else {
                    PinVerificationResult.Invalid((MAX_ATTEMPTS_BEFORE_DELAY - failures).coerceAtLeast(0))
                }
            }
        } finally {
            PinCrypto.wipe(pin)
        }
    }

    suspend fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
        confirmation: CharArray
    ): PinActionResult {
        return try {
            val record = pinRepository.getPinRecord()
                ?: return PinActionResult.Error("No PIN is currently configured.")

            val nowMs = System.currentTimeMillis()
            val lockoutUntil = pinRepository.getLockoutUntilMs()
            if (lockoutUntil > nowMs) {
                val remaining = ((lockoutUntil - nowMs) / 1000L).coerceAtLeast(1L)
                return PinActionResult.Error("Too many failed attempts. Wait ${remaining}s before retrying.")
            }

            if (!pinCrypto.verifyPin(currentPin, record)) {
                val failures = pinRepository.getFailedAttempts() + 1
                val delaySeconds = computeDelaySeconds(failures)
                pinRepository.recordFailedAttempt(
                    failures,
                    if (delaySeconds > 0L) nowMs + delaySeconds * 1000L else 0L
                )
                return PinActionResult.Error("Current PIN is incorrect.")
            }

            val validationError = validatePinPair(newPin, confirmation)
            if (validationError != null) return PinActionResult.Error(validationError)

            val newRecord = pinCrypto.createHashRecord(newPin)
            pinRepository.savePinRecord(newRecord, recoveryRecord = null)
            PinActionResult.Success()
        } finally {
            PinCrypto.wipe(currentPin)
            PinCrypto.wipe(newPin)
            PinCrypto.wipe(confirmation)
        }
    }

    suspend fun resetPinWithRecoveryCode(
        recoveryCode: CharArray,
        newPin: CharArray,
        confirmation: CharArray
    ): PinActionResult {
        return try {
            val recoveryRecord = pinRepository.getRecoveryRecord()
                ?: return PinActionResult.Error("No recovery key is registered on this device.")

            val normalized = String(recoveryCode).trim().uppercase().toCharArray()
            val verified = try {
                pinCrypto.verifyPin(normalized, recoveryRecord)
            } finally {
                PinCrypto.wipe(normalized)
            }

            if (!verified) {
                return PinActionResult.Error("Invalid recovery key.")
            }

            val validationError = validatePinPair(newPin, confirmation)
            if (validationError != null) return PinActionResult.Error(validationError)

            val newPinRecord = pinCrypto.createHashRecord(newPin)
            val newRecoveryCode = pinCrypto.generateRecoveryCode()
            val newRecoveryChars = newRecoveryCode.toCharArray()
            val newRecoveryRecord = try {
                pinCrypto.createHashRecord(newRecoveryChars)
            } finally {
                PinCrypto.wipe(newRecoveryChars)
            }

            pinRepository.savePinRecord(newPinRecord, newRecoveryRecord)
            PinActionResult.Success(recoveryCode = newRecoveryCode)
        } finally {
            PinCrypto.wipe(recoveryCode)
            PinCrypto.wipe(newPin)
            PinCrypto.wipe(confirmation)
        }
    }

    suspend fun disablePinAndStrictModeWithRecovery(recoveryCode: CharArray): PinActionResult {
        return try {
            val recoveryRecord = pinRepository.getRecoveryRecord()
                ?: return PinActionResult.Error("No recovery key is registered on this device.")

            val normalized = String(recoveryCode).trim().uppercase().toCharArray()
            val verified = try {
                pinCrypto.verifyPin(normalized, recoveryRecord)
            } finally {
                PinCrypto.wipe(normalized)
            }

            if (!verified) {
                return PinActionResult.Error("Invalid recovery key.")
            }

            pinRepository.clearPin()
            PinActionResult.Success()
        } finally {
            PinCrypto.wipe(recoveryCode)
        }
    }

    private fun validatePinPair(pin: CharArray, confirmation: CharArray): String? {
        if (pin.size < MIN_PIN_LENGTH || pin.size > MAX_PIN_LENGTH) {
            return "PIN must be between $MIN_PIN_LENGTH and $MAX_PIN_LENGTH digits."
        }
        if (pin.any { !it.isDigit() }) {
            return "PIN must contain digits only."
        }
        if (!pin.contentEquals(confirmation)) {
            return "PIN and confirmation do not match."
        }
        return null
    }

    private fun computeDelaySeconds(failures: Int): Long {
        return when {
            failures < MAX_ATTEMPTS_BEFORE_DELAY -> 0L
            failures == 3 -> 15L
            failures == 4 -> 30L
            else -> 60L
        }
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MAX_ATTEMPTS_BEFORE_DELAY = 3
    }
}
