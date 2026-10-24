package com.mateuswilliam.financas.data

import android.content.Context
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.notifications.RecurringReminders
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProtectionPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var preferences: CapturePreferences
    @Before fun setup() { preferences = CapturePreferences(context); preferences.reset() }
    @After fun cleanup() { preferences.reset(); RecurringReminders.schedule(context, false) }
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
    @Test fun nativeReminderJobIsPeriodicPersistedAndCanBeCancelled() {
        preferences.setReminders(true)
        RecurringReminders.schedule(context, true)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = scheduler.getPendingJob(2301)
        assertNotNull(job)
        assertTrue(job!!.isPeriodic); assertTrue(job.isPersisted)
        assertEquals(6 * 60 * 60 * 1000L, job.intervalMillis)
        assertTrue(job.service.className.endsWith("RecurringReminderService"))
        preferences.setReminders(false); RecurringReminders.schedule(context, false)
        assertNull(scheduler.getPendingJob(2301))
    }
}
