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
    @After fun cleanup() { preferences.reset(); RecurringReminders.schedule(context) }
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
    @Test fun nativeReminderJobIsPeriodicPersistedAndCanBeCancelled() {
        preferences.setReminders(true)
        RecurringReminders.schedule(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = scheduler.getPendingJob(2301)
        assertNotNull(job)
        assertTrue(job!!.isPeriodic); assertTrue(job.isPersisted)
        assertEquals(6 * 60 * 60 * 1000L, job.intervalMillis)
        assertTrue(job.service.className.endsWith("RecurringReminderService"))
        preferences.setReminders(false); RecurringReminders.schedule(context)
        assertNull(scheduler.getPendingJob(2301))
    }
    @Test fun delayedSchedulerRequestCannotReactivateRemindersAfterTheyAreDisabled() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val started = java.util.concurrent.CountDownLatch(1)
        val proceed = java.util.concurrent.CountDownLatch(1)
        try {
            preferences.setReminders(true)
            val delayed = executor.submit {
                started.countDown()
                check(proceed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                RecurringReminders.schedule(context)
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            preferences.setReminders(false)
            RecurringReminders.schedule(context)
            proceed.countDown()
            delayed.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(2301))
        } finally { proceed.countDown(); executor.shutdownNow() }
    }

}
