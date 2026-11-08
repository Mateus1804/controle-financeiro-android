package com.mateuswilliam.financas.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CommitmentMigrationTest {
    @Test fun schemaFourPreservesEveryLegacyTableAndDoesNotAutoClassifyRecurrences() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-commitments-test"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val snapshot = JSONObject(CompatibilityFixtures.bytes("snapshot-v1.json").toString(Charsets.UTF_8))
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            createTables(db, schema)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                val fields = entity.getJSONArray("fields")
                val columns = List(fields.length()) { fields.getJSONObject(it).getString("columnName") }
                val rows = snapshot.getJSONArray(table)
                for (n in 0 until rows.length()) {
                    val row = rows.getJSONObject(n)
                    val values = columns.map { column -> when (val value = row.get(column)) {
                        JSONObject.NULL -> null; is Boolean -> if (value) 1 else 0; else -> value
                    } }.toTypedArray()
                    db.execSQL("INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})", values)
                }
            }
            finishFixture(db, schema, 4)
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_4_5, FinanceDatabase.MIGRATION_5_6, FinanceDatabase.MIGRATION_6_7).build()
        val preferences = CapturePreferences(context)
        try {
            val dao = database.financeDao(); val expected = CompatibilityFixtures.snapshot()
            assertEquals(expected.records.toSet(), dao.allRecords().toSet())
            assertEquals(expected.drafts.toSet(), dao.allDrafts().toSet())
            assertEquals(expected.accounts.toSet(), dao.allAccounts().toSet())
            assertEquals(expected.budgets.toSet(), dao.allBudgets().toSet())
            assertEquals(expected.recurring.toSet(), dao.allRecurring().toSet())
            assertEquals(expected.occurrences.toSet(), dao.allOccurrences().toSet())
            assertTrue(dao.allMonthlyPlans().isEmpty()); assertTrue(dao.allPurchases().isEmpty())
            assertTrue(dao.allFixedRecurring().isEmpty()); assertTrue(dao.allCommitments().isEmpty())
            val repo = FinanceRepository(database, preferences)
            repo.saveMonthlyPlan(MonthlyIncomePlan(YearMonth.of(2026, 11), 300000))
            assertEquals(300000L, repo.commitmentReport(YearMonth.of(2026, 11)).incomeCents)
            assertEquals(0L, repo.commitmentReport(YearMonth.of(2026, 11)).committedCents)
            assertEquals(7, database.openHelper.writableDatabase.version)
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun schemaFivePreservesAnExistingPurchaseAndItsConfirmedInstallment() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-purchase-management-test"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/5.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            createTables(db, schema)
            db.execSQL("INSERT INTO purchases (id,title,bank,category,method,totalCents,installmentCount,paidInstallments,nextDueDate,dayOfMonth,sourceDraftId) VALUES ('compra-v5','Compra fictícia','Carteira','OTHER','CARD',6800,2,0,'2026-11-10',10,NULL)")
            db.execSQL("INSERT INTO records (id,sourceDraftId,amountCents,movement,method,category,bank,description,occurredAt,destination) VALUES ('pago-v5',NULL,3500,'EXPENSE','CARD','OTHER','Carteira','Pagamento fictício',1792800000000,'')")
            db.execSQL("INSERT INTO commitments (id,label,amountCents,dueDate,purchaseId,installmentNumber,recurringId,recordId) VALUES ('installment:compra-v5:1','Compra fictícia · 1/2',3400,'2026-11-10','compra-v5',1,NULL,'pago-v5')")
            db.execSQL("INSERT INTO commitments (id,label,amountCents,dueDate,purchaseId,installmentNumber,recurringId,recordId) VALUES ('installment:compra-v5:2','Compra fictícia · 2/2',3400,'2026-12-10','compra-v5',2,NULL,NULL)")
            finishFixture(db, schema, 5)
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_5_6, FinanceDatabase.MIGRATION_6_7).build()
        try {
            val dao = database.financeDao()
            val purchase = dao.allPurchases().single()
            assertEquals("compra-v5", purchase.id)
            assertEquals(6800L, purchase.totalCents)
            assertEquals(0L, purchase.revision)
            assertFalse(purchase.openCancelled)
            assertEquals(listOf("pago-v5", null), dao.allCommitments().map { it.recordId })
            assertTrue(dao.allCommitments().none { it.adjusted })
            assertEquals(3500L, dao.allRecords().single().amountCents)
            val repo = FinanceRepository(database, CapturePreferences(context))
            assertEquals(3500L, repo.commitmentReport(YearMonth.of(2026, 11)).committedCents)
            assertEquals(3400L, repo.commitmentReport(YearMonth.of(2026, 12)).committedCents)
            BackupSnapshot.validate(repo.snapshot())
            assertEquals(7, database.openHelper.writableDatabase.version)
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun schemaSixKeepsExistingForecastsAndPriorCancellation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-forecast-removal-test"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/6.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            createTables(db, schema)
            db.execSQL("INSERT INTO recurring (id,title,amountCents,movement,method,category,bank,nextDate,dayOfMonth,active,lastRemindedDate) VALUES ('r','Conta fictícia',4000,'EXPENSE','PIX','HOME','Carteira','2026-11-10',10,1,'')")
            db.execSQL("INSERT INTO fixed_recurring (recurringId,fromMonth) VALUES ('r','2026-11')")
            db.execSQL("INSERT INTO purchases (id,title,bank,category,method,totalCents,installmentCount,paidInstallments,nextDueDate,dayOfMonth,sourceDraftId,revision,openCancelled) VALUES ('p','Compra fictícia','Carteira','OTHER','CARD',6800,2,0,'2026-11-12',12,NULL,1,1)")
            db.execSQL("INSERT INTO commitments (id,label,amountCents,dueDate,purchaseId,installmentNumber,recurringId,recordId,adjusted) VALUES ('installment:p:1','Compra fictícia · 1/2',3400,'2026-11-12','p',1,NULL,NULL,0), ('installment:p:2','Compra fictícia · 2/2',3400,'2026-12-12','p',2,NULL,NULL,0)")
            finishFixture(db, schema, 6)
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_6_7).build()
        try {
            val dao = database.financeDao()
            assertFalse(dao.allRecurring().single().archived)
            assertTrue(dao.allRecurring().single().active)
            assertTrue(dao.allPurchases().single().openCancelled)
            assertTrue(dao.allCommitments().none { it.excluded })
            val repo = FinanceRepository(database, CapturePreferences(context))
            assertEquals(4000L, repo.commitmentReport(YearMonth.of(2026, 11)).committedCents)
            assertNull(repo.purchaseManagement("installment:p:1"))
            BackupSnapshot.validate(repo.snapshot())
            assertEquals(7, database.openHelper.writableDatabase.version)
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    private fun createTables(db: SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        repeat(entities.length()) { i ->
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            repeat(indices.length()) { n ->
                db.execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }

    private fun finishFixture(db: SQLiteDatabase, schema: JSONObject, version: Int) {
        val setup = schema.getJSONArray("setupQueries")
        repeat(setup.length()) { db.execSQL(setup.getString(it)) }
        db.version = version
    }
}
