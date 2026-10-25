package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import com.mateuswilliam.financas.core.NotificationInput
import com.mateuswilliam.financas.core.NotificationParser
import com.mateuswilliam.financas.core.Record
import com.mateuswilliam.financas.core.RecordValidation
import com.mateuswilliam.financas.core.AccountCatalog
import com.mateuswilliam.financas.core.AccountNames
import com.mateuswilliam.financas.core.Budgets
import com.mateuswilliam.financas.core.MonthlyBudget
import com.mateuswilliam.financas.core.RecurringPlan
import com.mateuswilliam.financas.core.Recurrences
import com.mateuswilliam.financas.core.Movement
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LedgerRecords(val records: List<Record>, val manualRecords: List<Record>)

data class CaptureResult(val id: String, val isNew: Boolean)
class DraftChangedException : IllegalStateException("A notificação mudou. Abra a pendência novamente para conferir os dados atuais.")

class FinanceRepository(private val database: FinanceDatabase, private val settings: CapturePreferences) {
    private val dao = database.financeDao()
    private val writes = Mutex()
    val pending = dao.pending()
    // Uma emissão mantém o extrato e os candidatos manuais na mesma revisão do banco.
    val ledger = dao.records().map { rows ->
        val records = ArrayList<Record>(rows.size)
        val manual = ArrayList<Record>()
        rows.forEach { row ->
            val record = row.toRecord()
            records.add(record)
            if (row.sourceDraftId == null) manual.add(record)
        }
        LedgerRecords(records, manual)
    }.flowOn(Dispatchers.Default)
    val records = ledger.map { it.records }
    val manualRecords = ledger.map { it.manualRecords }
    val accounts = combine(dao.accounts(), dao.usedAccounts()) { saved, used ->
        AccountCatalog.merge(saved.map { it.toOption() }, used.map { com.mateuswilliam.financas.core.AccountOption("", it.label, it.lastUsedAt) })
    }.flowOn(Dispatchers.Default)
    val budgets = dao.budgets().map { rows -> rows.map { it.toBudget() } }
    val recurring = dao.recurring().map { rows -> rows.map { it.toPlan() } }

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
        require(label.isNotBlank() && label.length <= 80) { "Informe um nome de conta com até 80 caracteres." }
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
            if (editing) {
                val previous = dao.recordById(record.id) ?: return@withTransaction false
                val updated = dao.updateRecord(RecordEntity.from(record, previous.sourceDraftId)) == 1
                if (updated) { rememberAccount(record.bank); rememberAccount(record.destination) }
                return@withTransaction updated
            }
            if (draftId != null) {
                val draft = dao.draftById(draftId) ?: return@withTransaction false
                if (draft.status != "PENDING") return@withTransaction false
                if (expectedRevision != null && expectedRevision != draft.revision) throw DraftChangedException()
            }
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
    suspend fun deleteRecord(id: String) = writes.withLock { database.withTransaction { dao.unlinkOccurrence(id); dao.deleteRecord(id) } }
    suspend fun exportRecords(): List<Record> = dao.allRecords().map { it.toRecord() }

    suspend fun saveBudget(budget: MonthlyBudget) = writes.withLock {
        Budgets.error(budget)?.let { throw IllegalArgumentException(it) }
        dao.upsertBudget(BudgetEntity(budget.month, budget.category, budget.limitCents))
    }
    suspend fun deleteBudget(month: String, category: com.mateuswilliam.financas.core.Category) = writes.withLock { dao.deleteBudget(month, category) }

    suspend fun saveRecurring(plan: RecurringPlan) = writes.withLock {
        Recurrences.error(plan)?.let { throw IllegalArgumentException(it) }
        database.withTransaction {
            require(dao.occurrence(plan.id, plan.nextDate) == null) { "Esse vencimento já foi tratado. Escolha outra data para a previsão." }
            val old = dao.recurringById(plan.id)
            val reminder = if (old?.nextDate == plan.nextDate) old.lastRemindedDate else ""
            dao.upsertRecurring(RecurringEntity.from(plan.copy(lastRemindedDate = reminder)))
            rememberAccount(plan.bank)
        }
    }

    suspend fun confirmRecurring(id: String, due: String, record: Record): Boolean = writes.withLock {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        require(record.movement in setOf(Movement.EXPENSE, Movement.INCOME)) { "O registro de uma previsão precisa ser receita ou despesa." }
        val saved = database.withTransaction {
            val plan = dao.recurringById(id) ?: return@withTransaction false
            if (!plan.active || plan.nextDate != due || dao.occurrence(id, due) != null) return@withTransaction false
            dao.insertRecord(RecordEntity.from(record, null))
            dao.insertOccurrence(OccurrenceEntity(id, due, "RECORDED", record.id))
            check(dao.advanceRecurring(id, due, Recurrences.followingDate(due, plan.dayOfMonth)) == 1)
            rememberAccount(record.bank)
            true
        }
        if (saved) withContext(Dispatchers.IO) { settings.rememberOrigin(record.bank) }
        saved
    }

    suspend fun skipRecurring(id: String, due: String): Boolean = writes.withLock {
        database.withTransaction {
            val plan = dao.recurringById(id) ?: return@withTransaction false
            if (plan.nextDate != due || dao.occurrence(id, due) != null) return@withTransaction false
            dao.insertOccurrence(OccurrenceEntity(id, due, "SKIPPED", null))
            check(dao.advanceRecurring(id, due, Recurrences.followingDate(due, plan.dayOfMonth)) == 1)
            true
        }
    }

    suspend fun notifyDue(today: LocalDate, send: (RecurringPlan) -> Boolean) = writes.withLock {
        if (!settings.snapshot().remindersEnabled) return@withLock
        dao.allRecurring().filter { it.active && !LocalDate.parse(it.nextDate).isAfter(today) && it.lastRemindedDate != it.nextDate }
            .forEach { if (settings.snapshot().remindersEnabled && send(it.toPlan())) dao.markReminded(it.id, it.nextDate) }
    }

    suspend fun snapshot(): FinanceSnapshot = writes.withLock {
        val display = settings.snapshot().let { BackupDisplay(it.appearance, it.hideAmounts, it.lastOrigin) }
        database.withTransaction { readSnapshot(dao, System.currentTimeMillis(), display) }
    }

    suspend fun restore(snapshot: FinanceSnapshot) = writes.withLock {
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
        dao.clearOccurrences(); dao.clearRecurring(); dao.clearBudgets()
        dao.clearRecords(); dao.clearDrafts(); dao.clearAccounts()
    }

    suspend fun clear() = writes.withLock {
        // O bloqueio permanece configurado até a transação apagar os dados financeiros.
        withContext(Dispatchers.IO) { settings.pauseForRestore() }
        database.withTransaction { clearTables() }
        withContext(Dispatchers.IO) { runCatching { settings.reset() }.isSuccess }
    }
}
