package com.mateuswilliam.financas.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        FinanceDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun versionOneKeepsRecordsAndPendingNotificationSource() = runBlocking {
        val name = "migration-accounts-test"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        helper.createDatabase(name, 1).apply {
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
