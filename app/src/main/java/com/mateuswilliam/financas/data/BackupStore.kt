package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.mateuswilliam.financas.core.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class StagedRestore internal constructor(val databaseName: String, val createdAt: Long, val records: Int,
    val pending: Int, val accounts: Int, val budgets: Int, val recurring: Int, internal val display: BackupDisplay, val monthlyPlans: Int = 0, val purchases: Int = 0, val commitments: Int = 0, val fixedRecurring: Int = 0)

class BackupStore(private val context: Context, private val repository: FinanceRepository) {
    suspend fun create(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        try {
            val plaintext = BackupSnapshot.encode(repository.snapshot())
            try { BackupCrypto.encrypt(plaintext, password) } finally { plaintext.fill(0) }
        } finally { password.fill('\u0000') }
    }

    suspend fun stage(input: InputStream, password: CharArray): StagedRestore = withContext(Dispatchers.IO) {
        var database: FinanceDatabase? = null
        val name = "restore-stage-${UUID.randomUUID()}.db"
        try {
            val file = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= BackupCrypto.MAX_FILE_BYTES) { "O arquivo excede o limite de 16 MiB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val plaintext = try { BackupCrypto.decrypt(file, password) } finally { file.fill(0) }
            val snapshot = try { BackupSnapshot.decode(plaintext) } finally { plaintext.fill(0) }
            val temporary = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3, FinanceDatabase.MIGRATION_3_4, FinanceDatabase.MIGRATION_4_5, FinanceDatabase.MIGRATION_5_6, FinanceDatabase.MIGRATION_6_7, FinanceDatabase.MIGRATION_7_8).build()
            database = temporary
            temporary.withTransaction { insertSnapshot(temporary.financeDao(), snapshot) }
            verify(temporary)
            val copy = readSnapshot(temporary.financeDao(), snapshot.createdAt, snapshot.display)
            BackupSnapshot.validate(copy)
            require(copy.records.associateBy { it.id } == snapshot.records.associateBy { it.id } &&
                copy.drafts.associateBy { it.id } == snapshot.drafts.associateBy { it.id } &&
                copy.accounts.toSet() == snapshot.accounts.toSet() && copy.budgets.toSet() == snapshot.budgets.toSet() &&
                copy.recurring.toSet() == snapshot.recurring.toSet() && copy.occurrences.toSet() == snapshot.occurrences.toSet() &&
                copy.monthlyPlans.toSet() == snapshot.monthlyPlans.toSet() && copy.purchases.toSet() == snapshot.purchases.toSet() &&
                copy.fixedRecurring.toSet() == snapshot.fixedRecurring.toSet() && copy.commitments.toSet() == snapshot.commitments.toSet() &&
                copy.recurringRevisions.toSet() == snapshot.recurringRevisions.toSet() && copy.recurringMonths.toSet() == snapshot.recurringMonths.toSet()) { "A conferência da base temporária falhou." }
            StagedRestore(name, snapshot.createdAt, copy.records.size, copy.drafts.count { it.status == "PENDING" },
                copy.accounts.size, copy.budgets.size, copy.recurring.size, copy.display, copy.monthlyPlans.size, copy.purchases.size, copy.commitments.size, copy.fixedRecurring.size)
        } catch (exception: Exception) {
            database?.close(); context.deleteDatabase(name); throw exception
        } finally { database?.close(); password.fill('\u0000') }
    }

    suspend fun restore(staged: StagedRestore) = withContext(Dispatchers.IO) {
        require(staged.databaseName.startsWith("restore-stage-") && context.getDatabasePath(staged.databaseName).exists()) { "A prévia expirou. Selecione o backup novamente." }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, staged.databaseName).addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3, FinanceDatabase.MIGRATION_3_4, FinanceDatabase.MIGRATION_4_5, FinanceDatabase.MIGRATION_5_6, FinanceDatabase.MIGRATION_6_7, FinanceDatabase.MIGRATION_7_8).build()
        try {
            verify(database)
            val snapshot = readSnapshot(database.financeDao(), staged.createdAt, staged.display)
            BackupSnapshot.validate(snapshot)
            repository.restore(snapshot)
        } finally { database.close(); withContext(NonCancellable) { discard(staged) } }
    }

    suspend fun discard(staged: StagedRestore) = withContext(Dispatchers.IO) { context.deleteDatabase(staged.databaseName); Unit }

    suspend fun removeAbandonedStages() = withContext(Dispatchers.IO) {
        context.databaseList().filter { it.startsWith("restore-stage-") && !it.endsWith("-wal") && !it.endsWith("-shm") && !it.endsWith("-journal") }
            .forEach { context.deleteDatabase(it) }
    }

    private fun verify(database: FinanceDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            require(!cursor.moveToFirst()) { "A base temporária contém vínculos inválidos." }
        }
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            require(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) { "A integridade da base temporária não foi confirmada." }
        }
    }
}

internal suspend fun readSnapshot(dao: FinanceDao, createdAt: Long, display: BackupDisplay) = FinanceSnapshot(createdAt,
    dao.allRecords(), dao.allDrafts(), dao.allAccounts(), dao.allBudgets(), dao.allRecurring(), dao.allOccurrences(), display,
    dao.allMonthlyPlans(), dao.allPurchases(), dao.allFixedRecurring(), dao.allCommitments(), dao.allRecurringRevisions(), dao.allRecurringMonths())

internal suspend fun insertSnapshot(dao: FinanceDao, original: FinanceSnapshot) {
    val snapshot = RecurringSnapshots.upgrade(original)
    snapshot.accounts.forEach { dao.insertAccount(it) }
    snapshot.drafts.forEach { dao.insertDraft(it) }
    snapshot.records.forEach { dao.insertRecord(it) }
    snapshot.budgets.forEach { dao.upsertBudget(it) }
    snapshot.recurring.forEach { dao.upsertRecurring(it) }
    snapshot.occurrences.forEach { dao.insertOccurrence(it) }
    snapshot.monthlyPlans.forEach { dao.upsertMonthlyPlan(it) }
    snapshot.purchases.forEach { dao.insertPurchase(it) }
    snapshot.fixedRecurring.forEach { dao.upsertFixedRecurring(it) }
    snapshot.commitments.forEach { dao.insertCommitment(it) }
    snapshot.recurringRevisions.forEach { dao.upsertRecurringRevision(it) }
    snapshot.recurringMonths.forEach { dao.insertRecurringMonth(it) }
}
