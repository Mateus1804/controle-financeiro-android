package com.mateuswilliam.financas.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForecastMigrationTest {
    @Test fun versionNineAddsForecastWithoutChangingLegacyRowsAndReopens()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="forecast-migration-nine"
        context.deleteDatabase(name)
        val schema=instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/9.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).apply {
            val entities=schema.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { n -> execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            val setup=schema.getJSONArray("setupQueries");repeat(setup.length()) { execSQL(setup.getString(it)) }
            execSQL("INSERT INTO records VALUES ('legacy',NULL,12345,'EXPENSE','PIX','FOOD','  Historic bank  ','History',1793491200000,'',NULL,'EXPENSE')")
            execSQL("INSERT INTO monthly_plans VALUES ('2026-11',987654,8000)")
            version=9;close()
        }
        fun open()=Room.databaseBuilder(context,FinanceDatabase::class.java,name).addMigrations(FinanceDatabase.MIGRATION_9_10).build()
        try {
            open().let { db -> try {
                val dao=db.financeDao();assertEquals(10,db.openHelper.writableDatabase.version)
                assertEquals("  Historic bank  ",dao.allRecords().single().bank)
                assertTrue(dao.allForecastRules().isEmpty())
                ForecastRepository(db).initialize()
                assertEquals("record:legacy",dao.allForecastRules().single().legacySource)
                assertEquals(987654L,dao.allMonthlyPlans().single().incomeCents)
            } finally { db.close() } }
            open().let { db -> try {
                ForecastRepository(db).initialize()
                assertEquals(1,db.financeDao().allForecastRules().size)
                assertEquals(1,db.financeDao().allRecords().size)
            } finally { db.close() } }
        } finally { context.deleteDatabase(name) }
    }
}
