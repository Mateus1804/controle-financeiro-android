package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.mateuswilliam.financas.core.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

data class StagedRestore internal constructor(val databaseName: String, val createdAt: Long, val records: Int,
    val pending: Int, val accounts: Int, val budgets: Int, val recurring: Int, internal val display: BackupDisplay)

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
            val temporary = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            database = temporary
            temporary.withTransaction { insertSnapshot(temporary.financeDao(), snapshot) }
            verify(temporary)
            val copy = readSnapshot(temporary.financeDao(), snapshot.createdAt, snapshot.display)
            BackupSnapshot.validate(copy)
            require(copy.records.associateBy { it.id } == snapshot.records.associateBy { it.id } &&
                copy.drafts.associateBy { it.id } == snapshot.drafts.associateBy { it.id } &&
                copy.accounts.toSet() == snapshot.accounts.toSet() && copy.budgets.toSet() == snapshot.budgets.toSet() &&
                copy.recurring.toSet() == snapshot.recurring.toSet() && copy.occurrences.toSet() == snapshot.occurrences.toSet()) { "A conferência da base temporária falhou." }
            StagedRestore(name, snapshot.createdAt, copy.records.size, copy.drafts.count { it.status == "PENDING" },
                copy.accounts.size, copy.budgets.size, copy.recurring.size, copy.display)
        } catch (exception: Exception) {
            database?.close(); context.deleteDatabase(name); throw exception
        } finally { database?.close(); password.fill('\u0000') }
    }

    suspend fun restore(staged: StagedRestore) = withContext(Dispatchers.IO) {
        require(staged.databaseName.startsWith("restore-stage-") && context.getDatabasePath(staged.databaseName).exists()) { "A prévia expirou. Selecione o backup novamente." }
        val database = Room.databaseBuilder(context, FinanceDatabase::class.java, staged.databaseName).build()
        try {
            verify(database)
            val snapshot = readSnapshot(database.financeDao(), staged.createdAt, staged.display)
            BackupSnapshot.validate(snapshot)
            repository.restore(snapshot)
        } finally { database.close(); discard(staged) }
    }

    suspend fun discard(staged: StagedRestore) = withContext(Dispatchers.IO) { context.deleteDatabase(staged.databaseName); Unit }

    suspend fun removeAbandonedStages() = withContext(Dispatchers.IO) {
        context.databaseList().filter { it.startsWith("restore-stage-") && !it.endsWith("-wal") && !it.endsWith("-shm") && !it.endsWith("-journal") }
            .forEach { context.deleteDatabase(it) }
    }

    private fun verify(database: FinanceDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            require(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) { "A integridade da base temporária não foi confirmada." }
        }
    }
}

internal suspend fun readSnapshot(dao: FinanceDao, createdAt: Long, display: BackupDisplay) = FinanceSnapshot(createdAt,
    dao.allRecords(), dao.allDrafts(), dao.allAccounts(), dao.allBudgets(), dao.allRecurring(), dao.allOccurrences(), display)

internal suspend fun insertSnapshot(dao: FinanceDao, snapshot: FinanceSnapshot) {
    snapshot.accounts.forEach { dao.insertAccount(it) }
    snapshot.drafts.forEach { dao.insertDraft(it) }
    snapshot.records.forEach { dao.insertRecord(it) }
    snapshot.budgets.forEach { dao.upsertBudget(it) }
    snapshot.recurring.forEach { dao.upsertRecurring(it) }
    snapshot.occurrences.forEach { dao.insertOccurrence(it) }
}
