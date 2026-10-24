package com.mateuswilliam.financas.data

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountMigrationTest {
    @Test fun versionOneKeepsRecordsAndPendingNotificationSource() = runBlocking {
        val name = "migration-accounts-test"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.mateuswilliam.financas.data.FinanceDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) execSQL(setup.getString(index))
            version = 1
            execSQL("INSERT INTO records VALUES ('legacy', NULL, 4290, 'EXPENSE', 'PIX', 'FOOD', '  Banco antigo  ', 'Legado', 1792800000000, '')")
            execSQL("INSERT INTO drafts VALUES ('draft-legado', 'chave-legado', 'pacote.original', 'Banco antigo', 'Título', 'Texto', 4290, 'EXPENSE', 'PIX', 'FOOD', 'Conferir', 1792800000000, 1792800000000, 'PENDING', 1)")
            close()
        }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.MIGRATION_1_2).build()
        try {
            val dao = database.financeDao()
            assertEquals(4290L, dao.allRecords().single().amountCents)
            assertEquals("  Banco antigo  ", dao.allRecords().single().bank)
            assertEquals("pacote.original", dao.draftById("draft-legado")!!.sourcePackage)
            dao.insertAccount(AccountEntity("nova conta", "Nova conta", 1))
            assertEquals("Nova conta", dao.allAccounts().single().label)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
