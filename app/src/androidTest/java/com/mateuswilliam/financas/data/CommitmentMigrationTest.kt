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
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (n in 0 until indices.length()) db.execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
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
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 4
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_4_5).build()
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
            assertEquals(5, database.openHelper.writableDatabase.version)
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
