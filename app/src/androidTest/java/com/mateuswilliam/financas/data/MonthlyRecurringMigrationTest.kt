package com.mateuswilliam.financas.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.core.RecurringMonthStatus
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MonthlyRecurringMigrationTest {
    @Test fun migrationPreservesBothLegacyPaymentsAndUsesOneStableMonthlyObligation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "monthly-recurring-migration"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/7.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { db.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            db.execSQL("INSERT INTO recurring VALUES ('r','Aluguel',6000,'EXPENSE','PIX','HOME','Carteira','2026-12-10',10,1,'',0)")
            db.execSQL("INSERT INTO fixed_recurring VALUES ('r','2026-11')")
            db.execSQL("INSERT INTO records VALUES ('paid-a',NULL,4500,'EXPENSE','PIX','HOME','Carteira','Primeiro pagamento',1792800000000,'')")
            db.execSQL("INSERT INTO records VALUES ('paid-b',NULL,6500,'EXPENSE','PIX','HOME','Carteira','Segundo pagamento',1792800000000,'')")
            db.execSQL("INSERT INTO occurrences VALUES ('r','2026-11-10','RECORDED','paid-a'),('r','2026-11-15','RECORDED','paid-b')")
            db.execSQL("INSERT INTO commitments VALUES ('recurring:r:2026-11-10','Aluguel antigo',4000,'2026-11-10',NULL,NULL,'r','paid-a',0,0),('recurring:r:2026-11-15','Aluguel corrigido',6000,'2026-11-15',NULL,NULL,'r','paid-b',0,0)")
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { db.execSQL(setup.getString(it)) }
            db.version = 7
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10).build()
        try {
            val repo = FinanceRepository(database, CapturePreferences(context))
            val dao = database.financeDao()
            assertEquals(2, dao.allRecords().size)
            assertEquals(2, dao.allOccurrences().size)
            assertEquals(2, dao.allCommitments().size)
            val monthly = repo.observeRecurringMonths(YearMonth.of(2026, 11)).first().single()
            assertEquals(RecurringMonthStatus.RECORDED, monthly.status)
            assertEquals("2026-11-15", monthly.plan.nextDate)
            assertEquals("Aluguel corrigido", monthly.plan.title)
            assertEquals(6500L, monthly.plan.amountCents)
            assertEquals(11000L, repo.commitmentReport(YearMonth.of(2026, 11)).committedCents)
            assertEquals(2, repo.exportRecords().size)
            BackupSnapshot.validate(repo.snapshot())
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
