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
import java.time.ZoneId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CaptureResult(val id: String, val isNew: Boolean)
class DraftChangedException : IllegalStateException("A notificação mudou. Abra a pendência novamente para conferir os dados atuais.")

class FinanceRepository(private val database: FinanceDatabase, private val settings: CapturePreferences) {
    private val dao = database.financeDao()
    private val writes = Mutex()
    val pending = dao.pending()
    val allAccounts = combine(dao.accounts(), dao.usedAccounts()) { saved, used ->
        AccountCatalog.merge(saved.map { it.toOption() }, used.map { AccountOption("", it.label, it.lastUsedAt) })
    }.flowOn(Dispatchers.Default)
    val accounts = combine(allAccounts, dao.accounts()) { merged, saved ->
        val archived = saved.filter { it.archived }.map { it.nameKey }.toSet()
        merged.filterNot { it.key in archived }
    }
    fun observeAccountBalances(asOf: Long = System.currentTimeMillis()) =
        observeAccountLedgerHistory().map { it.at(asOf).balances }.distinctUntilChanged().flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun observeAccountLedger(clockChanges: Flow<Unit> = flowOf(Unit)): Flow<AccountLedger> =
        combine(observeAccountLedgerHistory(), clockChanges) { history, _ -> history }
            .flatMapLatest { it.observe(System::currentTimeMillis, ZoneId::systemDefault) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    // O relógio atualiza a projeção em memória. Só uma nova observação ou uma invalidação lê o banco.
    private fun observeAccountLedgerHistory() = database.invalidationTracker.createFlow(
        "accounts", "account_balance_events", "records",
    ).conflate().map {
        val (accounts, records, references) = database.withTransaction {
            Triple(dao.allAccounts(), dao.allRecords(), dao.allAccountBalanceEvents())
        }
        AccountLedgerHistory(accounts, records, references)
    }

    suspend fun configureAccountBalance(name: String, balanceCents: Long, effectiveAt: Long, reason: String,
        correctionOf: Long? = null,
    ) = writes.withLock {
        val label = AccountNames.clean(name)
        require(label.isNotBlank() && label.length <= 80 && '\u0000' !in label) { "Informe um nome de conta com até 80 caracteres." }
        require(balanceCents in -Money.MAX_CENTS..Money.MAX_CENTS) { "Saldo fora do limite permitido." }
        require(!AccountFunds.isReserve(label) || balanceCents >= 0) { "A reserva não pode ter saldo inicial negativo." }
        if (correctionOf == null) {
            val zone = ZoneId.systemDefault()
            val date = java.time.Instant.ofEpochMilli(effectiveAt).atZone(zone).toLocalDate()
            require(effectiveAt > 0 && effectiveAt == date.atStartOfDay(zone).toInstant().toEpochMilli()) {
                "Informe o início da data de referência."
            }
            require(!date.isAfter(LocalDate.now(zone))) { "Use uma data de referência até hoje." }
        }
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank() && cleanReason.length <= 200 && '\u0000' !in cleanReason) { "Informe um motivo com até 200 caracteres." }
        database.withTransaction {
            val key = AccountNames.key(label)
            val original = correctionOf?.let { id ->
                dao.accountBalanceEvent(id).also { event ->
                    require(event?.accountKey == key && event.effectiveAt == effectiveAt) {
                        "O saldo original mudou. Abra o lançamento novamente."
                    }
                }
            }
            val oldReferences = if (AccountFunds.isReserve(label)) dao.allAccountBalanceEvents().map { it.toReference() } else null
            val now = System.currentTimeMillis()
            rememberAccount(label)
            dao.insertAccountBalanceEvent(AccountBalanceEventEntity(accountKey = key, balanceCents = balanceCents,
                effectiveAt = effectiveAt, createdAt = maxOf(now, original?.createdAt ?: now), reason = cleanReason))
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

    suspend fun addAccount(name: String, requireActive: Boolean = false): String = writes.withLock {
        val label = AccountNames.clean(name)
        require(label.isNotBlank() && label.length <= 80 && '\u0000' !in label) { "Informe um nome de conta com até 80 caracteres." }
        database.withTransaction {
            require(!requireActive || dao.accountByKey(AccountNames.key(label))?.archived != true) {
                "Esta conta está arquivada. Reative-a no Resumo."
            }
            rememberAccount(label)
        }
        dao.accountByKey(AccountNames.key(label))!!.label
    }

    suspend fun capture(input: NotificationInput, simulation: Boolean = false): CaptureResult? = writes.withLock {
        if (!simulation && !settings.permits(input.packageName)) return@withLock null
        if (!validDraftSource(input.sourceKey, input.packageName)) return@withLock null
        if (NotificationParser.isSensitive(input.title, input.body)) return@withLock null
        val title = normalizeDraftText(input.title)
        val body = normalizeDraftText(input.body)
        if (title.isBlank() && body.isBlank()) return@withLock null
        if ((title != input.title || body != input.body) && NotificationParser.isSensitive(title, body)) return@withLock null
        val suggestion = NotificationParser.parse(input.copy(title = title, body = body))
        database.withTransaction {
            val existing = dao.draftBySource(input.sourceKey)
            if (existing != null) {
                if (existing.status != "PENDING") return@withTransaction null
                if (existing.sourceTitle != title || existing.sourceBody != body) {
                    dao.updateDraft(existing.id, title, body, suggestion.amountCents,
                        suggestion.movement, suggestion.method, suggestion.category, suggestion.explanation)
                }
                CaptureResult(existing.id, false)
            } else {
                val id = UUID.randomUUID().toString()
                dao.insertDraft(DraftEntity(id, input.sourceKey, input.packageName, normalizeDraftText(input.bank),
                    title, body, suggestion.amountCents, suggestion.movement, suggestion.method,
                    suggestion.category, suggestion.explanation, input.occurredAt, System.currentTimeMillis()))
                CaptureResult(id, true)
            }
        }
    }

    suspend fun save(record: Record, draftId: String?, editing: Boolean = false, expectedRevision: Int? = null): Boolean = writes.withLock {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        val saved = database.withTransaction {
            val previous = if (editing) dao.recordById(record.id) ?: return@withTransaction false else null
            record.forecastItemId?.let { id ->
                val key = requireNotNull(record.forecastOccurrenceKey)
                require(ForecastSnapshots.validOccurrenceKey(key)) { "Vínculo de previsão inválido." }
                val kind = if (record.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE
                require(dao.hasForecastRule(id, kind.name)) { "Previsão vinculada não encontrada." }
                val keepsLink = previous?.forecastItemId == id && previous.forecastOccurrenceKey == key
                if (!keepsLink) {
                    require(!dao.hasForecastOccurrenceLink(id, key)) {
                        "Esta ocorrência já está vinculada a outro lançamento."
                    }
                    val forecast = ForecastDataset(
                        dao.forecastRulesFor(id).map { it.model() },
                        dao.forecastOverridesFor(id).map { it.model() },
                        dao.allCycleRules().map { it.model() },
                    )
                    require(forecast.findForecastOccurrences(setOf(key)).any { it.kind == kind && !it.ignored }) {
                        "Esta ocorrência mudou. Escolha uma previsão disponível ou retire o vínculo."
                    }
                }
            }
            val original = record.originalRecordId?.let { dao.recordById(it)?.toRecord() }
            val record = if (original != null) Refunds.applyOriginal(record, original) else record
            RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
            if (record.movement == Movement.TRANSFER) {
                val sourceKey = AccountNames.key(record.bank)
                val destinationKey = AccountNames.key(record.destination)
                val unchangedLegacy = previous?.movement == Movement.TRANSFER &&
                    AccountNames.key(previous.bank) == sourceKey && AccountNames.key(previous.destination) == destinationKey
                if (!unchangedLegacy) {
                    require(canTransferWithAccount(sourceKey) && canTransferWithAccount(destinationKey)) {
                        "Cadastre as duas contas próprias antes de transferir."
                    }
                }
                if (sourceKey == AccountFunds.RESERVE_KEY) {
                    val reserve = AccountOption(sourceKey, AccountFunds.RESERVE_NAME, 0)
                    val otherRecords = dao.allRecords().filterNot { editing && it.id == record.id }.map { it.toRecord() }
                    val available = checkNotNull(AccountBalances.calculate(listOf(reserve), emptySet(),
                        dao.allAccountBalanceEvents().map { it.toReference() }, otherRecords, record.occurredAt,
                        assumeZeroOpening = true).single().balanceCents)
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

    private suspend fun canTransferWithAccount(key: String): Boolean {
        val account = dao.accountByKey(key)
        return if (account == null) key == AccountFunds.RESERVE_KEY else !account.archived
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

    /** Retorna se as preferências de exibição foram salvas após restaurar os dados financeiros. */
    suspend fun restore(original: FinanceSnapshot): Boolean = writes.withLock {
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
        try {
            withContext(Dispatchers.IO) { settings.restoreDisplay(snapshot.display) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
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
