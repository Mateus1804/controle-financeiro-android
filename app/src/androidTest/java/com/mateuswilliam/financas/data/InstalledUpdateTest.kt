package com.mateuswilliam.financas.data

import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InstalledUpdateTest {
    @Test fun previousVersionHistoryAndPreferencesSurviveInstallationInPlace() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val installed = args.getString("installedUpgrade") == "true"
        val legacyExport = args.getString("legacyExport") == "true"
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApplication
        val installedVersion = PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        val name = "compatibility-update-isolated"
        val database = if (installed || legacyExport) app.database else CompatibilityFixtures.database(app, name)
        suspend fun assertTables() {
            val expected = CompatibilityFixtures.snapshot()
            val dao = database.financeDao()
            assertEquals(expected.records.toSet(), dao.allRecords().toSet())
            assertEquals(expected.drafts.toSet(), dao.allDrafts().toSet())
            assertEquals(expected.accounts.toSet(), dao.allAccounts().toSet())
            assertEquals(expected.budgets.toSet(), dao.allBudgets().toSet())
            assertEquals(expected.recurring.toSet(), dao.allRecurring().toSet())
            assertEquals(expected.occurrences.toSet(), dao.allOccurrences().toSet())
        }
        val exportedBackup = File(app.getExternalFilesDir(null), "compatibility/previous-version.cfbackup")
        try {
            assertTables()
            if (legacyExport) {
                assertEquals(1L, installedVersion)
                assertEquals(3, database.openHelper.writableDatabase.version)
                exportedBackup.parentFile!!.mkdirs()
                // O código carregado do APK anterior produz o arquivo; não há reimplementação do exportador no teste.
                exportedBackup.writeBytes(app.backups.create("Atualização fictícia 2026!".toCharArray()))
                assertTrue(exportedBackup.length() > BackupCrypto.HEADER_BYTES)
                return@runBlocking
            }
            assertTrue(installedVersion >= 2)
            assertEquals(4, database.openHelper.writableDatabase.version)
            if (installed) {
                assertEquals(Appearance.DARK, app.settings.snapshot().appearance)
                assertTrue(app.settings.snapshot().hideAmounts)
                assertTrue(app.settings.snapshot().introSeen)
                assertFalse(app.settings.snapshot().enabled)
                assertTrue("O APK anterior precisa ter exportado o backup.", exportedBackup.isFile)
                app.repository.save(Record("registro-antes-da-restauracao", 100, Movement.EXPENSE, PaymentMethod.CASH,
                    Category.OTHER, "Carteira", "Registro fictício posterior à atualização", 1), null)
                val preview = app.backups.stage(exportedBackup.inputStream(), "Atualização fictícia 2026!".toCharArray())
                assertTrue(app.repository.exportRecords().any { it.id == "registro-antes-da-restauracao" })
                assertEquals(3, preview.records); assertEquals(1, preview.pending)
                app.backups.restore(preview)
                assertTables()
                assertEquals(Appearance.DARK, app.settings.snapshot().appearance)
                assertTrue(app.settings.snapshot().hideAmounts)
                assertFalse(app.settings.snapshot().enabled); assertFalse(app.settings.snapshot().remindersEnabled)
            }
        } finally { if (!installed && !legacyExport) { database.close(); app.deleteDatabase(name) } }
    }
}
