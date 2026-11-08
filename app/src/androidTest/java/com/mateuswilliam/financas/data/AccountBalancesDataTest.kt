package com.mateuswilliam.financas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AccountBalancesDataTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val start get() = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun referencesArchiveRefundAndJsonRoundTripPreserveHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val settings = CapturePreferences(context)
        try {
            val repo = FinanceRepository(db, settings)
            repo.configureAccountBalance("A", -1000, start, "Saldo inicial")
            repo.configureAccountBalance("A", 2000, start, "Conferência")
            val original = Record("income", 500, Movement.INCOME, PaymentMethod.PIX, Category.SALARY, "A", "Salário", start + 100)
            repo.save(original, null)
            repo.save(original.copy(id = "refund", movement = Movement.REFUND, originalRecordId = original.id, amountCents = 100), null)
            assertEquals(Movement.INCOME, repo.exportRecords().first { it.id == "refund" }.reversalMovement)
            repo.setAccountArchived("a", true)
            repo.addAccount("A")
            assertTrue(repo.accounts.first().isEmpty())
            assertEquals(2400L, repo.observeAccountBalances(start + 200).first().single().balanceCents)
            val snapshot = repo.snapshot()
            val restored = BackupSnapshot.decode(BackupSnapshot.encode(snapshot))
            assertEquals(snapshot, restored)
            assertEquals(2, restored.accountBalanceEvents.size)
            val json = JSONObject(BackupSnapshot.encode(snapshot).toString(Charsets.UTF_8))
            json.put("version", 5)
            assertTrue(runCatching { BackupSnapshot.decode(json.toString().toByteArray()) }.isFailure)
            repo.clear()
            assertTrue(db.financeDao().allAccountBalanceEvents().isEmpty())
            repo.restore(restored)
            assertEquals(snapshot.accountBalanceEvents, repo.snapshot().accountBalanceEvents)
            repo.save(original.copy(movement = Movement.EXPENSE, category = Category.FOOD), null, editing = true)
            assertEquals(Movement.EXPENSE, repo.exportRecords().first { it.id == "refund" }.reversalMovement)
            repo.deleteRecord(original.id)
            val retained = repo.exportRecords().single()
            assertEquals(Category.FOOD, retained.category)
            assertEquals(Movement.EXPENSE, retained.reversalMovement)
            assertEquals(original.id, retained.originalRecordId)
            assertEquals(2100L, repo.observeAccountBalances(start + 200).first().single().balanceCents)
            val afterDeletion = repo.snapshot()
            assertEquals(afterDeletion, BackupSnapshot.decode(BackupSnapshot.encode(afterDeletion)))
        } finally { db.close() }
    }

    @Test fun transfersSaveEditAndDeleteAtomicallyWithoutChangingConsolidatedFundsOrResult() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db, CapturePreferences(context))
            repo.configureAccountBalance("A", 1000, start, "Saldo inicial")
            repo.configureAccountBalance("B", 500, start, "Saldo inicial")
            val income = Record("income-at-reference", 100, Movement.INCOME, PaymentMethod.PIX,
                Category.SALARY, "A", "Receita na data da referência", start)
            repo.save(income.copy(id = "already-in-reference", amountCents = 999,
                movement = Movement.EXPENSE, category = Category.FOOD, occurredAt = start - 1), null)
            repo.save(income, null)
            suspend fun assertFunds(a: Long, b: Long) {
                val balances = repo.observeAccountBalances(start + 100).first().associate { it.account.key to it.balanceCents }
                assertEquals(mapOf("a" to a, "b" to b), balances)
                assertEquals(1600L, balances.values.sumOf { checkNotNull(it) })
                assertEquals(100L, Ledger.month(repo.exportRecords(), java.time.YearMonth.of(2026, 10),
                    ZoneId.systemDefault()).result)
            }
            // Earlier activity is already represented by the baseline; activity at the cutoff is included.
            assertFunds(1100, 500)
            val transfer = Record("transfer", 200, Movement.TRANSFER, PaymentMethod.BANK_TRANSFER,
                Category.OTHER, "A", "Entre contas próprias", start + 1, "B")
            assertTrue(repo.save(transfer, null))
            assertFunds(900, 700)
            val edited = transfer.copy(bank = "B", destination = "A", amountCents = 300)
            assertTrue(repo.save(edited, null, editing = true))
            assertFunds(1400, 200)
            val beforeRejected = repo.exportRecords()
            assertTrue(runCatching { repo.save(edited.copy(destination = " B "), null, editing = true) }.isFailure)
            assertTrue(runCatching { repo.save(edited.copy(destination = "Não cadastrada"), null, editing = true) }.isFailure)
            assertTrue(runCatching { repo.save(transfer.copy(id = "invalid-new", destination = "Não cadastrada"), null) }.isFailure)
            assertEquals(beforeRejected, repo.exportRecords())
            assertEquals(setOf("a", "b"), db.financeDao().allAccounts().map { it.nameKey }.toSet())
            assertEquals(2, db.financeDao().allAccountBalanceEvents().size)
            assertFunds(1400, 200)
            repo.deleteRecord(transfer.id)
            assertEquals(2, repo.exportRecords().size)
            assertFunds(1100, 500)
        } finally { db.close() }
    }

    @Test fun versionEightMigrationPreservesRecordsAndLeavesOldBalancesUnknown() = runBlocking {
        val name = "migration-account-balances"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mateuswilliam.financas.data.FinanceDatabase/8.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO accounts VALUES ('a', 'A', 1)")
            db.execSQL("INSERT INTO records VALUES ('legacy', NULL, 100, 'REFUND', 'PIX', 'FOOD', 'A', 'Legado', 1792800000000, '')")
            db.version = 8
        }
        val db = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_8_9).build()
        try {
            val dao = db.financeDao()
            assertFalse(dao.allAccounts().single().archived)
            assertEquals(Movement.EXPENSE, dao.allRecords().single().reversalMovement)
            assertNull(dao.allRecords().single().originalRecordId)
            assertTrue(dao.allAccountBalanceEvents().isEmpty())
            assertNull(FinanceRepository(db, CapturePreferences(context)).observeAccountBalances().first().single().balanceCents)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
