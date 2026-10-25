package com.mateuswilliam.financas.data

import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.BuildConfig
import com.mateuswilliam.financas.FinanceApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InstalledUpdateTest {
    @Test fun previousVersionHistoryAndPreferencesSurviveInstallationInPlace() = runBlocking<Unit> {
        val installed = InstrumentationRegistry.getArguments().getString("installedUpgrade") == "true"
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApplication
        // A execução dedicada usa a base semeada no APK anterior; a matriz comum exercita a mesma base isoladamente.
        val name = "compatibility-update-isolated"
        val database = if (installed) app.database else CompatibilityFixtures.database(app, name)
        try {
            val expected = CompatibilityFixtures.snapshot()
            val dao = database.financeDao()
            assertTrue(BuildConfig.VERSION_CODE >= 2)
            assertEquals(expected.records.toSet(), dao.allRecords().toSet())
            assertEquals(expected.drafts.toSet(), dao.allDrafts().toSet())
            assertEquals(expected.accounts.toSet(), dao.allAccounts().toSet())
            assertEquals(expected.budgets.toSet(), dao.allBudgets().toSet())
            assertEquals(expected.recurring.toSet(), dao.allRecurring().toSet())
            assertEquals(expected.occurrences.toSet(), dao.allOccurrences().toSet())
            assertEquals(4, database.openHelper.writableDatabase.version)
            if (installed) {
                assertEquals(Appearance.DARK, app.settings.snapshot().appearance)
                assertTrue(app.settings.snapshot().hideAmounts)
                assertTrue(app.settings.snapshot().introSeen)
                assertFalse(app.settings.snapshot().enabled)
            }
        } finally { if (!installed) { database.close(); app.deleteDatabase(name) } }
    }
}
