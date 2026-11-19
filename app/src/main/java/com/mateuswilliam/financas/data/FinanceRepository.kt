package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.NotificationParser
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.RecordValidation
import com.mateuswilliam.financas.core.AccountCatalog
import com.mateuswilliam.financas.core.AccountNames
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AccountLedger(
    val asOf: Long,
    val records: List<Record>,
    val manualRecords: List<Record>,
    val balances: List<AccountBalance>,
    val references: List<AccountBalanceReference>,
)

data class CaptureResult(val id: String, val isNew: Boolean)
class DraftChangedException : IllegalStateException("A notificação mudou. Abra a pendência novamente para conferir os dados atuais.")

class FinanceRepository(private val database: FinanceDatabase, private val settings: CapturePreferences) {
    private val dao = database.financeDao()
    private val writes = Mutex()
    val pending = dao.pending()
    val records = dao.records().map { rows -> rows.map { it.toRecord() } }.flowOn(Dispatchers.Default)
    val allAccounts = combine(dao.accounts(), dao.usedAccounts()) { saved, used ->
        AccountCatalog.merge(saved.map { it.toOption() }, used.map { AccountOption("", it.label, it.lastUsedAt) })
    }.flowOn(Dispatchers.Default)
    val accounts = combine(allAccounts, dao.accounts()) { merged, saved ->
        val archived = saved.filter { it.archived }.map { it.nameKey }.toSet()
        merged.filterNot { it.key in archived }
    }
    fun observeAccountBalances(asOf: Long = System.currentTimeMillis()) =
        observeAccountLedger { asOf }.map { it.balances }.distinctUntilChanged()

    // Extrato, referências e saldos usam a mesma leitura do banco e o mesmo horário.
    internal fun observeAccountLedger(now: () -> Long = System::currentTimeMillis) = database.invalidationTracker.createFlow(
        "accounts", "account_balance_events", "records",
    ).conflate().map {
        database.withTransaction {
            val asOf = now()
            val saved = dao.allAccounts()
            val rows = dao.allRecords()
            val records = rows.map { it.toRecord() }
            val manualRecords = records.filterIndexed { index, _ -> rows[index].sourceDraftId == null }
            val references = dao.allAccountBalanceEvents().map { it.toReference() }
            val balances = AccountBalances.calculate(AccountCatalog.options(saved.map { it.toOption() }, records),
                saved.filter { it.archived }.map { it.nameKey }.toSet(), references, records, asOf)
            AccountLedger(asOf, records, manualRecords, balances, references)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun configureAccountBalance(name: String, balanceCents: Long, effectiveAt: Long, reason: String) = writes.withLock {
        val label = AccountNames.clean(name)
        require(label.isNotBlank() && label.length <= 80 && '\u0000' !in label) { "Informe um nome de conta com até 80 caracteres." }
        require(balanceCents in -Money.MAX_CENTS..Money.MAX_CENTS) { "Saldo fora do limite permitido." }
        require(!AccountFunds.isReserve(label) || balanceCents >= 0) { "A reserva não pode ter saldo inicial negativo." }
        val date = java.time.Instant.ofEpochMilli(effectiveAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        require(effectiveAt > 0 && effectiveAt == date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) { "Informe o início da data de referência." }
        require(!date.isAfter(LocalDate.now())) { "Use uma data de referência até hoje." }
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank() && cleanReason.length <= 200 && '\u0000' !in cleanReason) { "Informe um motivo com até 200 caracteres." }
        database.withTransaction {
            val oldReferences = if (AccountFunds.isReserve(label)) dao.allAccountBalanceEvents().map { it.toReference() } else null
            rememberAccount(label)
            dao.insertAccountBalanceEvent(AccountBalanceEventEntity(accountKey = AccountNames.key(label), balanceCents = balanceCents,
                effectiveAt = effectiveAt, createdAt = System.currentTimeMillis(), reason = cleanReason))
            if (oldReferences != null) {
                val records = dao.allRecords().map { it.toRecord() }
                require(!AccountBalances.worsensReserveDeficit(records, records, oldReferences,
                    dao.allAccountBalanceEvents().map { it.toReference() })) {
                    "Este saldo de referência deixaria a reserva negativa em uma data posterior."
                }
            }
        }
    }
    suspend fun setAccountArchived(key: String, archived: Boolean) = writes.withLock {
        database.withTransaction {
            if (dao.accountByKey(key) == null) {
                val historic = dao.allRecords().asSequence().flatMap { sequenceOf(it.bank, it.destination) }
                    .firstOrNull { AccountNames.key(it) == key }
                require(historic != null) { "Conta não encontrada." }
                rememberAccount(historic)
            }
            dao.setAccountArchived(key, archived)
        }
    }
    private suspend fun rememberAccount(name: String) {
        val label = AccountNames.clean(name)
        if (label.isBlank()) return
        val key = AccountNames.key(label)
        val time = System.currentTimeMillis()
        dao.insertAccount(AccountEntity(key, label, time))
        dao.touchAccount(key, time)
    }

    suspend fun addAccount(name: String): String = writes.withLock {
        val label = AccountNames.clean(name)
        require(label.isNotBlank() && label.length <= 80 && '\u0000' !in label) { "Informe um nome de conta com até 80 caracteres." }
        database.withTransaction { rememberAccount(label) }
        dao.accountByKey(AccountNames.key(label))!!.label
    }

    suspend fun capture(input: NotificationInput, simulation: Boolean = false): CaptureResult? = writes.withLock {
        if (!simulation && !settings.permits(input.packageName)) return@withLock null
        if (NotificationParser.isSensitive(input.title, input.body)) return@withLock null
        val suggestion = NotificationParser.parse(input)
        database.withTransaction {
            val existing = dao.draftBySource(input.sourceKey)
            if (existing != null) {
                if (existing.status != "PENDING") return@withTransaction null
                if (existing.sourceTitle != input.title || existing.sourceBody != input.body) {
                    dao.updateDraft(existing.id, input.title, input.body, suggestion.amountCents,
                        suggestion.movement, suggestion.method, suggestion.category, suggestion.explanation)
                }
                CaptureResult(existing.id, false)
            } else {
                val id = UUID.randomUUID().toString()
                dao.insertDraft(DraftEntity(id, input.sourceKey, input.packageName, input.bank,
                    input.title, input.body, suggestion.amountCents, suggestion.movement, suggestion.method,
                    suggestion.category, suggestion.explanation, input.occurredAt, System.currentTimeMillis()))
                CaptureResult(id, true)
            }
        }
    }

    suspend fun save(record: Record, draftId: String?, editing: Boolean = false, expectedRevision: Int? = null): Boolean = writes.withLock {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        val saved = database.withTransaction {
            record.forecastItemId?.let { id ->
                require(ForecastSnapshots.validOccurrenceKey(record.forecastOccurrenceKey!!))
                require(dao.allForecastRules().any { it.itemId == id && it.kind == if (record.movement == Movement.INCOME) "INCOME" else "EXPENSE" }) { "Previsão vinculada não encontrada." }
            }
            val original = record.originalRecordId?.let { dao.recordById(it)?.toRecord() }
            val record = if (original != null) Refunds.applyOriginal(record, original) else record
            RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
            val previous = if (editing) dao.recordById(record.id) ?: return@withTransaction false else null
            if (record.movement == Movement.TRANSFER) {
                val unchangedLegacy = previous?.movement == Movement.TRANSFER &&
                    AccountNames.key(previous.bank) == AccountNames.key(record.bank) && AccountNames.key(previous.destination) == AccountNames.key(record.destination)
                require(AccountNames.key(record.bank) != AccountNames.key(record.destination)) { "Escolha contas diferentes para a transferência." }
                if (!unchangedLegacy) require(dao.accountByKey(AccountNames.key(record.bank))?.archived == false &&
                    dao.accountByKey(AccountNames.key(record.destination))?.archived == false) { "Cadastre as duas contas próprias antes de transferir." }
                if (AccountFunds.isReserve(record.bank)) {
                    val reserve = dao.accountByKey(AccountNames.key(record.bank))
                    require(reserve != null) { "Cadastre a conta Reserva antes de resgatar." }
                    val otherRecords = dao.allRecords().filterNot { editing && it.id == record.id }.map { it.toRecord() }
                    val available = AccountBalances.calculate(listOf(reserve.toOption()), emptySet(),
                        dao.allAccountBalanceEvents().map { it.toReference() }, otherRecords, record.occurredAt).single().balanceCents
                    require(available != null) { "Informe o saldo inicial da reserva antes de resgatar." }
                    require(record.amountCents <= available) { "Saldo insuficiente na reserva na data do resgate." }
                }
            }
            if (previous != null) {
                val refunds = dao.refundsForRecord(record.id).map { row ->
                    RecordEntity.from(Refunds.applyOriginal(row.toRecord(), record), row.sourceDraftId)
                }
                validateReserveChange(listOf(previous.toRecord(), record) + refunds.map { it.toRecord() },
                    listOf(record) + refunds.map { it.toRecord() }, setOf(record.id) + refunds.map { it.id })
                val updated = dao.updateRecord(RecordEntity.from(record, previous.sourceDraftId)) == 1
                if (updated) {
                    refunds.forEach { check(dao.updateRecord(it) == 1) }
                    rememberAccount(record.bank); rememberAccount(record.destination)
                }
                return@withTransaction updated
            }
            if (draftId != null) {
                val draft = dao.draftById(draftId) ?: return@withTransaction false
                if (draft.status != "PENDING") return@withTransaction false
                if (expectedRevision != null && expectedRevision != draft.revision) throw DraftChangedException()
            }
            validateReserveChange(listOf(record), listOf(record), emptySet())
            dao.insertRecord(RecordEntity.from(record, draftId))
            rememberAccount(record.bank)
            rememberAccount(record.destination)
            if (draftId != null) dao.reviewDraft(draftId, "SAVED")
            true
        }
        if (saved && draftId == null) withContext(Dispatchers.IO) { settings.rememberOrigin(record.bank) }
        saved
    }

    suspend fun dismiss(id: String) = writes.withLock { dao.reviewDraft(id, "DISMISSED") }
    suspend fun deleteRecord(id: String) = writes.withLock {
        database.withTransaction {
            dao.recordById(id)?.let {
                validateReserveChange(listOf(it.toRecord()), emptyList(), setOf(id))
                dao.deleteRecord(id)
            }
            Unit
        }
    }

    private suspend fun validateReserveChange(affected: List<Record>, replacements: List<Record>, removedIds: Set<String>) {
        if (affected.none(AccountBalances::touchesReserve)) return
        val before = dao.allRecords().map { it.toRecord() }
        val after = before.filterNot { it.id in removedIds } + replacements
        val references = dao.allAccountBalanceEvents().map { it.toReference() }
        require(!AccountBalances.worsensReserveDeficit(before, after, references)) {
            "Esta alteração deixaria a reserva negativa. Confira os resgates e aportes afetados."
        }
    }
    suspend fun exportRecords(): List<Record> = dao.allRecords().map { it.toRecord() }

    suspend fun snapshot(): FinanceSnapshot = writes.withLock {
        val display = settings.snapshot().let { BackupDisplay(it.appearance, it.hideAmounts, it.lastOrigin) }
        database.withTransaction { readSnapshot(dao, System.currentTimeMillis(), display) }
    }

    suspend fun restore(original: FinanceSnapshot) = writes.withLock {
        val snapshot = original
        BackupSnapshot.validate(snapshot)
        val previousSettings = settings.snapshot()
        withContext(Dispatchers.IO) { settings.pauseForRestore() }
        try {
            database.withTransaction { clearTables(); insertSnapshot(dao, snapshot) }
        } catch (exception: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                settings.setEnabled(previousSettings.enabled)
                settings.setReminders(previousSettings.remindersEnabled)
            }
            throw exception
        }
        withContext(Dispatchers.IO) { settings.restoreDisplay(snapshot.display) }
    }

    private suspend fun clearTables() {
        dao.clearForecastOverrides(); dao.clearForecastRules(); dao.clearCycleRules(); dao.clearReserveEvents(); dao.clearForecastMeta()
        dao.clearRecords(); dao.clearDrafts(); dao.clearAccountBalanceEvents(); dao.clearAccounts()
    }

    suspend fun clear() = writes.withLock {
        // O bloqueio permanece configurado até a transação apagar os dados financeiros.
        withContext(Dispatchers.IO) { settings.pauseForRestore() }
        database.withTransaction { clearTables() }
        withContext(Dispatchers.IO) { runCatching { settings.reset() }.isSuccess }
    }
}
