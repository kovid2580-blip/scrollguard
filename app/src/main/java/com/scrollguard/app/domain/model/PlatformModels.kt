package com.scrollguard.app.domain.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val supportsSelectiveBlocking: Boolean
)

data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean
) {
    val allCoreGranted: Boolean
        get() = accessibilityEnabled && usageAccessGranted && overlayGranted
}

data class StrictModeState(
    val isStrictModeEnabled: Boolean = false,
    val hasPinSet: Boolean = false,
    val focusSessionActive: Boolean = false,
    val focusSessionEndEpochMs: Long = 0L,
    val focusSessionDurationMinutes: Int = 60,
    val dailyTempUnlockLimit: Int = 2,
    val tempUnlocksUsedToday: Int = 0,
    val tempUnlockPackage: String? = null,
    val tempUnlockExpiresEpochMs: Long = 0L,
    val isBlockingPaused: Boolean = false
) {
    val remainingTempUnlocks: Int
        get() = (dailyTempUnlockLimit - tempUnlocksUsedToday).coerceAtLeast(0)

    fun isSettingsLocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        val activeFocus = focusSessionActive && focusSessionEndEpochMs > nowMs
        return hasPinSet && (isStrictModeEnabled || activeFocus)
    }
}
