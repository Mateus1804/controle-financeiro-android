package com.mateuswilliam.financas.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.core.Category
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanningMigrationTest {
    @Test fun versionTwoAddsPlanningWithoutRewritingAccountsOrHistory() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-planning-test"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (n in 0 until indices.length()) execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            version = 2
            execSQL("INSERT INTO records VALUES ('legado-v2', NULL, 4290, 'EXPENSE', 'PIX', 'FOOD', '  Banco antigo  ', 'Legado fictício', 1792800000000, '')")
            execSQL("INSERT INTO accounts VALUES ('inter pessoal', 'Inter pessoal', 1792800000000)")
            close()
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_2_3).build()
        try {
            val dao = database.financeDao()
            assertEquals(4290L, dao.allRecords().single().amountCents)
            assertEquals("  Banco antigo  ", dao.allRecords().single().bank)
            assertEquals("Inter pessoal", dao.allAccounts().single().label)
            assertTrue(dao.allRecurring().isEmpty()); assertTrue(dao.allOccurrences().isEmpty())
            dao.upsertBudget(BudgetEntity("2026-10", Category.FOOD, 10000))
            assertEquals(10000L, dao.allBudgets().single().limitCents)
            assertEquals(3, database.openHelper.writableDatabase.version)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
