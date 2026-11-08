package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecordInputBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FinanceDatabase
    private lateinit var preferences: CapturePreferences
    private lateinit var repository: FinanceRepository
    private fun record(id: String = "preservado") = Record(id, 4290, Movement.EXPENSE, PaymentMethod.PIX,
        Category.FOOD, "Conta fictícia", "Compra fictícia", 1_792_800_000_000L)

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        preferences = CapturePreferences(context).also { it.reset() }
        repository = FinanceRepository(database, preferences)
    }

    @After fun cleanup() { preferences.reset(); database.close() }

    @Test fun invalidIdentifiersCannotEnterTheLedgerOrBlockExistingBackups() = runBlocking<Unit> {
        repository.save(record(), null)
        for (id in listOf("", " ", "x".repeat(129), "registro\u0000inválido")) {
            assertTrue(runCatching { repository.save(record(id), null) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(listOf(record()), repository.exportRecords())
        val snapshot = repository.snapshot()
        assertEquals(snapshot.records, BackupSnapshot.decode(BackupSnapshot.encode(snapshot)).records)
    }

    @Test fun nullInRecordTextIsRejectedBeforeMutatingRecordsOrAccounts() = runBlocking<Unit> {
        repository.save(record(), null)
        val accounts = database.financeDao().allAccounts()
        for (invalid in listOf(
            record("banco-inválido").copy(bank = "Conta\u0000inválida"),
            record("descrição-inválida").copy(description = "Compra\u0000inválida"),
            record("destino-inválido").copy(movement = Movement.TRANSFER, destination = "Destino\u0000inválido"),
        )) {
            assertTrue(runCatching { repository.save(invalid, null) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(listOf(record()), repository.exportRecords())
        assertEquals(accounts, database.financeDao().allAccounts())
        val snapshot = repository.snapshot()
        assertEquals(snapshot.records, BackupSnapshot.decode(BackupSnapshot.encode(snapshot)).records)
    }

    @Test fun nullInANewAccountCannotPoisonTheBackup() = runBlocking<Unit> {
        repository.addAccount("Conta preservada")
        val accounts = database.financeDao().allAccounts()
        assertTrue(runCatching { repository.addAccount("Conta\u0000inválida") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(accounts, database.financeDao().allAccounts())
        val snapshot = repository.snapshot()
        assertEquals(snapshot.accounts, BackupSnapshot.decode(BackupSnapshot.encode(snapshot)).accounts)
    }

    @Test fun validLegacyDestinationRemainsReadableAndRestorable() = runBlocking<Unit> {
        database.financeDao().insertRecord(RecordEntity.from(record().copy(destination = "Conta anterior"), null))
        val snapshot = repository.snapshot()
        val restored = BackupSnapshot.decode(BackupSnapshot.encode(snapshot))
        assertEquals("Conta anterior", restored.records.single().destination)
        assertEquals(snapshot.records, restored.records)
    }

    @Test fun acceptedUnicodeBoundaryValuesRoundTripWithoutChangingTheRecord() = runBlocking<Unit> {
        val valid = record("ç".repeat(128)).copy(bank = "Conta São João", description = "Pão, café e almoço — exemplo",
            movement = Movement.TRANSFER, destination = "Poupança familiar", method = PaymentMethod.UNKNOWN)
        assertTrue(repository.save(valid, null))
        val snapshot = repository.snapshot()
        val restored = BackupSnapshot.decode(BackupSnapshot.encode(snapshot))
        assertEquals(valid, restored.records.single().toRecord())
        assertEquals(snapshot.accounts, restored.accounts)
    }
}
