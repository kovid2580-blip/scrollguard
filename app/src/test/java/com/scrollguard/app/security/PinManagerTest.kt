package com.scrollguard.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.StrictModeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinManagerTest {

    private lateinit var pinCrypto: PinCrypto
    private lateinit var pinRepository: PinRepository
    private lateinit var pinManager: PinManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        pinCrypto = PinCrypto()
        val appPrefs = ScrollGuardPreferences(context)
        pinRepository = PinRepository(context, pinCrypto, appPrefs)
        pinManager = PinManager(pinCrypto, pinRepository)
    }

    @Test
    fun `pbkdf2 hashing uses unique salt and constant-time verification`() {
        val rec1 = pinCrypto.createHashRecord("4829".toCharArray())
        val rec2 = pinCrypto.createHashRecord("4829".toCharArray())

        assertNotEquals(rec1.saltBase64, rec2.saltBase64)
        assertNotEquals(rec1.hashBase64, rec2.hashBase64)
        assertTrue(pinCrypto.verifyPin("4829".toCharArray(), rec1))
        assertFalse(pinCrypto.verifyPin("4820".toCharArray(), rec1))
    }

    @Test
    fun `scenario 9 - create, verify, lockout after failed attempts, change, and recovery`() = runTest {
        val mismatch = pinManager.createPin("1234".toCharArray(), "1235".toCharArray())
        assertTrue(mismatch is PinActionResult.Error)

        val created = pinManager.createPin("2468".toCharArray(), "2468".toCharArray())
        assertTrue(created is PinActionResult.Success)
        val recoveryCode = (created as PinActionResult.Success).recoveryCode
        assertNotNull(recoveryCode)

        assertEquals(PinVerificationResult.Verified, pinManager.verifyPin("2468".toCharArray()))

        // 3 consecutive failures trigger lockout delay
        assertTrue(pinManager.verifyPin("0000".toCharArray()) is PinVerificationResult.Invalid)
        assertTrue(pinManager.verifyPin("0000".toCharArray()) is PinVerificationResult.Invalid)
        val thirdFailure = pinManager.verifyPin("0000".toCharArray())
        assertTrue(thirdFailure is PinVerificationResult.LockedOut)

        // Reset via recovery key works even if PIN was forgotten
        val recovered = pinManager.resetPinWithRecoveryCode(
            recoveryCode = recoveryCode!!.toCharArray(),
            newPin = "1357".toCharArray(),
            confirmation = "1357".toCharArray()
        )
        assertTrue(recovered is PinActionResult.Success)
        assertEquals(PinVerificationResult.Verified, pinManager.verifyPin("1357".toCharArray()))
    }

    @Test
    fun `scenario 10 - strict mode locks settings when pin is configured`() {
        val unlockedNormal = StrictModeState(
            isStrictModeEnabled = false,
            hasPinSet = true,
            focusSessionActive = false
        )
        assertFalse(unlockedNormal.isSettingsLocked(nowMs = 1_000L))

        val strictEnabled = StrictModeState(
            isStrictModeEnabled = true,
            hasPinSet = true,
            focusSessionActive = false
        )
        assertTrue(strictEnabled.isSettingsLocked(nowMs = 1_000L))

        val activeFocus = StrictModeState(
            isStrictModeEnabled = false,
            hasPinSet = true,
            focusSessionActive = true,
            focusSessionEndEpochMs = 5_000L
        )
        assertTrue(activeFocus.isSettingsLocked(nowMs = 1_000L))
        assertFalse(activeFocus.isSettingsLocked(nowMs = 6_000L))
    }
}
