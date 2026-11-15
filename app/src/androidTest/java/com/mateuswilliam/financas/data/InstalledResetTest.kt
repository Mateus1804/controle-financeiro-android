package com.mateuswilliam.financas.data

import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class InstalledResetTest {
    @Test fun authorizedResetDiscardsOldDatabaseAndKeepsAppearance() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("intentionalReset") == "true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApplication
        app.maintenanceReady.await()
        val dao=app.database.financeDao()
        assertEquals(1,app.database.openHelper.writableDatabase.version)
        assertFalse(app.getDatabasePath("financas.db").exists())
        assertTrue(app.getDatabasePath("rendly-v1.db").exists())
        assertTrue(dao.allRecords().isEmpty())
        assertTrue(dao.allForecastRules().isEmpty())
        assertTrue(dao.allAccounts().isEmpty())
        assertEquals(Appearance.DARK,app.settings.snapshot().appearance)
        assertTrue(app.settings.snapshot().hideAmounts)
        assertFalse(app.settings.snapshot().enabled)
    }
}
