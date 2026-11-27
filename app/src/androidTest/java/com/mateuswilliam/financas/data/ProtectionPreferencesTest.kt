package com.mateuswilliam.financas.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProtectionPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var preferences: CapturePreferences
    @Before fun setup() { preferences = CapturePreferences(context); preferences.reset() }
    @After fun cleanup() { preferences.reset() }
    @Test fun protectionAndReminderChoicesPersistBetweenPreferenceInstances() {
        assertFalse(preferences.snapshot().lockEnabled); assertFalse(preferences.snapshot().secureScreen)
        preferences.setLockEnabled(true); preferences.setReminders(true)
        val saved = CapturePreferences(context).snapshot()
        assertTrue(saved.lockEnabled); assertTrue(saved.secureScreen); assertTrue(saved.remindersEnabled)
        preferences.setSecureScreen(false)
        assertTrue(CapturePreferences(context).snapshot().lockEnabled)
        assertFalse(CapturePreferences(context).snapshot().secureScreen)
    }
    @Test fun restoringDisplayPausesCaptureWithoutTransferringConsentOrRemovingProtection() {
        preferences.acceptDisclosure(); preferences.selectPackages(setOf("fixture.local")); preferences.setLockEnabled(true); preferences.setReminders(true)
        preferences.restoreDisplay(BackupDisplay(Appearance.DARK, true, "Conta fictícia"))
        val saved = CapturePreferences(context).snapshot()
        assertTrue(saved.consented); assertEquals(setOf("fixture.local"), saved.packages)
        assertTrue(saved.lockEnabled); assertTrue(saved.secureScreen)
        assertFalse(saved.enabled); assertFalse(saved.remindersEnabled)
        assertEquals(Appearance.DARK, saved.appearance); assertTrue(saved.hideAmounts)
    }
    @Test fun clearingPreferencesResetsSecurityAndReminderChoices() {
        preferences.setLockEnabled(true); preferences.setReminders(true)
        preferences.reset()
        val saved = CapturePreferences(context).snapshot()
        assertFalse(saved.lockEnabled); assertFalse(saved.secureScreen); assertFalse(saved.remindersEnabled)
    }
    @Test fun failedPersistenceDoesNotClaimLockActivationOrRemoveAnExistingLock() {
        val isolated = context.getSharedPreferences("failing-protection-test", Context.MODE_PRIVATE)
        try {
            for (enabled in listOf(false, true)) {
                check(isolated.edit().clear().putBoolean("lock_enabled", enabled).putBoolean("secure_screen", enabled).commit())
                val failing = CapturePreferences(FailingPreferencesContext(context, isolated))
                assertTrue(runCatching { failing.setLockEnabled(!enabled) }.exceptionOrNull() is IllegalStateException)
                assertEquals(enabled, failing.snapshot().lockEnabled)
                assertEquals(enabled, failing.snapshot().secureScreen)
            }
        } finally { isolated.edit().clear().commit() }
    }
}
