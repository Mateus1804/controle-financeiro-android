package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.mateuswilliam.financas.core.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

private const val STAGE_PREFIX = "restore-stage-"
private val stageFile = Regex("""(${STAGE_PREFIX}[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.db)(?:-wal|-shm|-journal|-mj.*)?""")

class StagedRestore internal constructor(val databaseName: String, val createdAt: Long, val records: Int,
    val pending: Int, val accounts: Int, val forecasts: Int, internal val display: BackupDisplay)

class BackupStore internal constructor(private val context: Context, private val repository: () -> FinanceRepository) {
    constructor(context: Context, repository: FinanceRepository) : this(context, { repository })

    private val stages = Mutex()
    private val activeStages = mutableSetOf<String>()
    private val cleanupState = MutableStateFlow(false)
    val cleanupPending = cleanupState.asStateFlow()

    suspend fun create(password: CharArray): ByteArray = try {
        withContext(Dispatchers.IO) {
            removeAbandonedStages()
            val plaintext = BackupSnapshot.encode(repository().snapshot())
            try { BackupCrypto.encrypt(plaintext, password) } finally { plaintext.fill(0) }
        }
    } finally { password.fill('\u0000') }

    suspend fun stage(input: InputStream, password: CharArray): StagedRestore = stage(password) { input }

    internal suspend fun stage(password: CharArray, openInput: () -> InputStream): StagedRestore {
        val name = "$STAGE_PREFIX${UUID.randomUUID()}.db"
        var delivered = false
        try {
            val preview = withContext(Dispatchers.IO) {
                val file = readBackupFile(openInput())
                val plaintext = try { BackupCrypto.decrypt(file, password) } finally { file.fill(0) }
                val snapshot = try { BackupSnapshot.decode(plaintext) } finally { plaintext.fill(0) }
                stages.withLock {
                    removeAbandonedStagesLocked()
                    prepareStage(name, snapshot).also { activeStages.add(name) }
                }
            }
            delivered = true
            return preview
        } finally {
            password.fill('\u0000')
            if (!delivered) discardStage(name)
        }
    }

    private fun readBackupFile(input: InputStream): ByteArray = input.use { stream ->
        val output = ByteArrayOutputStream()
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

    private suspend fun prepareStage(name: String, snapshot: FinanceSnapshot): StagedRestore {
        val temporary = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
        return try {
            temporary.withTransaction { insertSnapshot(temporary.financeDao(), snapshot) }
            verify(temporary)
            val copy = readSnapshot(temporary.financeDao(), snapshot.createdAt, snapshot.display)
            BackupSnapshot.validate(copy)
            require(copy.records.toSet() == snapshot.records.toSet() && copy.drafts.toSet() == snapshot.drafts.toSet() &&
                copy.accounts.toSet() == snapshot.accounts.toSet() && copy.accountBalanceEvents.toSet() == snapshot.accountBalanceEvents.toSet() &&
                copy.forecastRules.toSet() == snapshot.forecastRules.toSet() && copy.forecastOverrides.toSet() == snapshot.forecastOverrides.toSet() &&
                copy.cycleRules.toSet() == snapshot.cycleRules.toSet() && copy.reserveEvents.toSet() == snapshot.reserveEvents.toSet() &&
                copy.forecastMeta.toSet() == snapshot.forecastMeta.toSet()) { "A conferência da base temporária falhou." }
            StagedRestore(name, snapshot.createdAt, copy.records.size, copy.drafts.count { it.status == "PENDING" },
                copy.accounts.size, copy.forecastRules.map { it.itemId }.distinct().size, copy.display)
        } finally { temporary.close() }
    }

    /** Retorna se as preferências de exibição foram salvas após restaurar os dados financeiros. */
    suspend fun restore(staged: StagedRestore): Boolean = try {
        withContext(Dispatchers.IO) {
            stages.withLock {
                val name = staged.databaseName
                require(activeStages.remove(name) && context.getDatabasePath(name).exists()) { "A prévia expirou. Selecione o backup novamente." }
                val database = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
                val snapshot = try {
                    verify(database)
                    readSnapshot(database.financeDao(), staged.createdAt, staged.display)
                } finally { database.close() }
                BackupSnapshot.validate(snapshot)
                repository().restore(snapshot)
            }
        }
    } finally {
        discardStage(staged.databaseName)
    }

    suspend fun discard(staged: StagedRestore) = discardStage(staged.databaseName)

    private suspend fun discardStage(name: String) = withContext(NonCancellable + Dispatchers.IO) {
        stages.withLock {
            activeStages.remove(name)
            removeAbandonedStagesLocked()
        }
    }

    suspend fun removeAbandonedStages() = withContext(Dispatchers.IO) {
        stages.withLock { removeAbandonedStagesLocked() }
    }

    private fun abandonedStageNames(): Set<String> = context.databaseList()
        .mapNotNull { stageFile.matchEntire(it)?.groupValues?.get(1) }
        .toSet() - activeStages

    private fun removeAbandonedStagesLocked() {
        cleanupState.value = try {
            var deletionFailed = false
            for (name in abandonedStageNames()) {
                val deleted = try { context.deleteDatabase(name) } catch (_: Exception) { false }
                if (!deleted) deletionFailed = true
            }
            // O Android pode retornar sucesso mesmo quando só parte dos arquivos foi removida.
            deletionFailed || abandonedStageNames().isNotEmpty()
        } catch (_: Exception) {
            true
        }
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
    records = dao.allRecords(), drafts = dao.allDrafts(), accounts = dao.allAccounts(), display = display,
    accountBalanceEvents = dao.allAccountBalanceEvents(), forecastRules = dao.allForecastRules(), forecastOverrides = dao.allForecastOverrides(),
    cycleRules = dao.allCycleRules(), reserveEvents = dao.allReserveEvents(), forecastMeta = dao.allForecastMeta())

internal suspend fun insertSnapshot(dao: FinanceDao, snapshot: FinanceSnapshot) {
    snapshot.forecastRules.forEach { dao.upsertForecastRule(it) }
    snapshot.forecastOverrides.forEach { dao.upsertForecastOverride(it) }
    snapshot.cycleRules.forEach { dao.upsertCycleRule(it) }
    snapshot.reserveEvents.forEach { dao.insertReserveEvent(it) }
    snapshot.forecastMeta.forEach { dao.upsertForecastMeta(it) }
    snapshot.accounts.forEach { dao.insertAccount(it) }
    snapshot.accountBalanceEvents.forEach { dao.insertAccountBalanceEvent(it) }
    snapshot.drafts.forEach { dao.insertDraft(it) }
    snapshot.records.forEach { dao.insertRecord(it) }
}
