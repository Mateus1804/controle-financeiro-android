package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class HistoryCompatibilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun assertTables(expected: FinanceSnapshot, actual: FinanceSnapshot) {
        assertEquals(expected.records.toSet(), actual.records.toSet())
        assertEquals(expected.drafts.toSet(), actual.drafts.toSet())
        assertEquals(expected.accounts.toSet(), actual.accounts.toSet())
        assertEquals(expected.budgets.toSet(), actual.budgets.toSet())
        assertEquals(expected.recurring.toSet(), actual.recurring.toSet())
        assertEquals(expected.occurrences.toSet(), actual.occurrences.toSet())
    }

    @Test fun schemaThreeAddsQueryIndicesWithoutRewritingAnyFinancialTable() = runBlocking<Unit> {
        val name = "compatibility-schema-three"
        val database = CompatibilityFixtures.database(context, name)
        try {
            assertTables(CompatibilityFixtures.snapshot(), readSnapshot(database.financeDao(), 1, CompatibilityFixtures.snapshot().display))
            val sqlite = database.openHelper.writableDatabase
            assertEquals(4, sqlite.version)
            fun plan(query: String) = sqlite.query("EXPLAIN QUERY PLAN $query").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString(" ")
            }
            val historyPlan = plan("SELECT * FROM records ORDER BY occurredAt DESC, id ASC")
            assertTrue(historyPlan, historyPlan.contains("index_records_occurredAt_id"))
            assertFalse(historyPlan, historyPlan.contains("TEMP B-TREE"))
            val pendingPlan = plan("SELECT * FROM drafts WHERE status = 'PENDING' ORDER BY capturedAt DESC, id ASC")
            assertTrue(pendingPlan, pendingPlan.contains("index_drafts_status_capturedAt_id"))
            assertFalse(pendingPlan, pendingPlan.contains("TEMP B-TREE"))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun frozenVersionOneBackupRestoresAllTablesIntoTheCurrentSchema() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val preferences = CapturePreferences(context).also { it.reset() }
        val repository = FinanceRepository(database, preferences)
        val store = BackupStore(context, repository)
        try {
            repository.save(Record("preservado-ate-confirmar", 100, Movement.EXPENSE, PaymentMethod.CASH, Category.OTHER, "Carteira", "Fictício", 1), null)
            val envelope = Base64.getMimeDecoder().decode(CompatibilityFixtures.bytes("backup-v1.b64"))
            val preview = store.stage(ByteArrayInputStream(envelope), "Compatibilidade fictícia 2026!".toCharArray())
            assertEquals("preservado-ate-confirmar", repository.exportRecords().single().id)
            assertEquals(3, preview.records); assertEquals(1, preview.pending)
            store.restore(preview)
            val expected = CompatibilityFixtures.snapshot()
            val actual = repository.snapshot()
            assertTables(expected, actual)
            assertEquals(expected.display, actual.display)
            assertFalse(preferences.snapshot().enabled); assertFalse(preferences.snapshot().remindersEnabled)
            assertEquals(4, database.openHelper.writableDatabase.version)
            // O novo exportador continua legível pelo formato de dados 1, independentemente do esquema Room.
            assertTables(actual, BackupSnapshot.decode(BackupSnapshot.encode(actual)))
        } finally { store.removeAbandonedStages(); preferences.reset(); database.close() }
    }

    @Test fun growingHistoryKeepsStableOrderManualCandidatesAndNormalizedAccountChoices() = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val preferences = CapturePreferences(context).also { it.reset() }
        try {
            val dao = database.financeDao()
            database.withTransaction {
                dao.insertAccount(AccountEntity("inter pessoal", "Inter pessoal", 1))
                repeat(5000) { i ->
                    dao.insertRecord(RecordEntity("history-%05d".format(java.util.Locale.ROOT, i), null, 100 + i.toLong(), Movement.EXPENSE,
                        PaymentMethod.PIX, Category.OTHER, if (i % 2 == 0) "  INTER pessoal " else "Inter trabalho", "Fictício $i", 1000 + (i / 2).toLong(), if (i % 3 == 0) "Carteira" else ""))
                }
            }
            val repository = FinanceRepository(database, preferences)
            val ledger = repository.ledger.first()
            assertEquals(5000, ledger.records.size); assertEquals(ledger.records, ledger.manualRecords)
            assertEquals(listOf("history-04998", "history-04999", "history-04996", "history-04997"), ledger.records.take(4).map { it.id })
            assertEquals(3, dao.usedAccounts().first().size)
            assertEquals(setOf("Inter pessoal", "Inter trabalho", "Carteira"), repository.accounts.first().map { it.label }.toSet())
            assertEquals(5000, repository.exportRecords().size)
        } finally { preferences.reset(); database.close() }
    }
}
