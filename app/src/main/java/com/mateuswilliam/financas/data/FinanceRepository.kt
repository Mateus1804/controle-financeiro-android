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
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
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

data class LedgerRecords(val records: List<Record>, val manualRecords: List<Record>)

data class CommitmentSetup(
    val month: YearMonth, val income: MonthlyIncomePlan?,
    val fixedRecurring: Map<String, YearMonth>, val installments: List<InstallmentDue>,
)

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

    fun commitmentSetup(month: YearMonth) = combine(
        dao.observeMonthlyPlan(month.toString()), dao.observeFixedRecurring(),
        dao.observeInstallmentDues(month.atDay(1).toString(), month.atEndOfMonth().toString()),
    ) { income, fixed, installments ->
        CommitmentSetup(month, income?.toPlan(), fixed.associate { it.recurringId to YearMonth.parse(it.fromMonth) }, installments)
    }.distinctUntilChanged()

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
                if (record.movement != Movement.EXPENSE) unlinkCommitment(record.id)
                val updated = dao.updateRecord(RecordEntity.from(record, previous.sourceDraftId)) == 1
                if (updated) {
                    if (record.movement == Movement.EXPENSE) reconcileRecurringCommitment(record)
                    rememberAccount(record.bank); rememberAccount(record.destination)
                }
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

    private suspend fun reconcileRecurringCommitment(record: Record) {
        val occurrence = dao.occurrenceByRecord(record.id) ?: return
        if (occurrence.status != "RECORDED") return
        val due = LocalDate.parse(occurrence.dueDate)
        val key = CommitmentPlanning.recurringId(occurrence.recurringId, due)
        val existing = dao.commitmentById(key)
        if (existing != null) {
            require(existing.recordId == null || existing.recordId == record.id) { "O vencimento está vinculado a outra despesa." }
            if (existing.recordId == null) check(dao.linkCommitment(key, record.id) == 1)
        } else {
            val fixed = dao.fixedRecurringById(occurrence.recurringId) ?: return
            if (YearMonth.from(due) < YearMonth.parse(fixed.fromMonth)) return
            dao.insertCommitment(CommitmentEntity(key, record.description, record.amountCents, occurrence.dueDate,
                null, null, occurrence.recurringId, record.id))
        }
    }

    suspend fun dismiss(id: String) = writes.withLock { dao.reviewDraft(id, "DISMISSED") }
    suspend fun deleteRecord(id: String) = writes.withLock {
        database.withTransaction {
            unlinkCommitment(id)
            dao.unlinkOccurrence(id)
            dao.deleteRecord(id)
        }
    }

    private suspend fun unlinkCommitment(recordId: String) {
        val purchaseId = dao.commitmentByRecord(recordId)?.purchaseId
        dao.unlinkCommitment(recordId)
        if (purchaseId != null) {
            val purchase = checkNotNull(dao.purchaseById(purchaseId))
            saveNextPurchaseRevision(purchase)
        }
    }

    suspend fun exportRecords(): List<Record> = dao.allRecords().map { it.toRecord() }

    suspend fun saveMonthlyPlan(plan: MonthlyIncomePlan) = writes.withLock {
        CommitmentPlanning.validate(plan)
        dao.upsertMonthlyPlan(MonthlyPlanEntity(plan.month.toString(), plan.incomeCents, plan.healthyLimitBasisPoints))
    }

    suspend fun createPurchase(purchase: InstallmentPurchase, draftId: String? = null, expectedRevision: Int? = null): Boolean = writes.withLock {
        val schedule = CommitmentPlanning.schedule(purchase)
        require(draftId == null || expectedRevision != null) { "Confira a revisão atual da notificação antes de criar a compra." }
        database.withTransaction {
            if (dao.purchaseById(purchase.id) != null) return@withTransaction false
            if (draftId != null) {
                val draft = dao.draftById(draftId) ?: return@withTransaction false
                if (draft.status != "PENDING") return@withTransaction false
                if (draft.revision != expectedRevision) throw DraftChangedException()
            }
            dao.insertPurchase(PurchaseEntity.from(purchase, draftId))
            insertPurchaseSchedule(purchase, schedule)
            rememberAccount(purchase.bank)
            if (draftId != null) dao.reviewDraft(draftId, "SAVED")
            true
        }
    }

    private suspend fun insertPurchaseSchedule(purchase: InstallmentPurchase, schedule: InstallmentSchedule) {
        CommitmentReports.fromInstallments(purchase.id, purchase.title, schedule).zip(schedule.pending).forEach { (item, part) ->
            dao.insertCommitment(CommitmentEntity(item.id, item.label, item.amountCents, item.dueDate.toString(),
                purchase.id, part.number, null, null))
        }
    }

    suspend fun purchaseManagement(installmentId: String): PurchaseManagement? = database.withTransaction {
        val item = dao.commitmentById(installmentId) ?: return@withTransaction null
        val purchaseId = item.purchaseId ?: return@withTransaction null
        val number = item.installmentNumber ?: return@withTransaction null
        val purchase = dao.purchaseById(purchaseId) ?: return@withTransaction null
        if (purchase.openCancelled || item.recordId != null) return@withTransaction null
        val installments = dao.installmentsForPurchase(purchaseId)
        PurchaseManagement(purchase.toPurchase(), purchase.revision, item.id,
            Installment(number, LocalDate.parse(item.dueDate), item.amountCents),
            installments.count { it.recordId == null }, installments.none { it.recordId != null || it.adjusted })
    }

    suspend fun editPurchase(expectedRevision: Long, purchase: InstallmentPurchase): Boolean = writes.withLock {
        val schedule = CommitmentPlanning.schedule(purchase)
        database.withTransaction {
            val previous = dao.purchaseById(purchase.id) ?: return@withTransaction false
            if (previous.openCancelled || previous.revision != expectedRevision) return@withTransaction false
            val installments = dao.installmentsForPurchase(purchase.id)
            if (installments.isEmpty() || installments.any { it.recordId != null || it.adjusted }) return@withTransaction false
            val edited = PurchaseEntity.from(purchase, previous.sourceDraftId).copy(revision = previous.revision)
            if (edited == previous) return@withTransaction true
            check(dao.deleteReplaceableInstallments(purchase.id) == installments.size)
            saveNextPurchaseRevision(edited)
            insertPurchaseSchedule(purchase, schedule)
            rememberAccount(purchase.bank)
            true
        }
    }

    suspend fun adjustInstallment(id: String, expectedRevision: Long, adjustment: InstallmentAdjustment): Boolean = writes.withLock {
        database.withTransaction {
            val item = dao.commitmentById(id) ?: return@withTransaction false
            val purchase = item.purchaseId?.let { dao.purchaseById(it) } ?: return@withTransaction false
            if (purchase.openCancelled || purchase.revision != expectedRevision || item.recordId != null) return@withTransaction false
            val date = adjustment.dueDate.toString()
            if (item.amountCents == adjustment.amountCents && item.dueDate == date) return@withTransaction true
            check(dao.adjustOpenInstallment(id, adjustment.amountCents, date) == 1)
            saveNextPurchaseRevision(purchase)
            true
        }
    }

    suspend fun cancelOpenInstallments(id: String, expectedRevision: Long, expectedOpenCount: Int): Int = writes.withLock {
        database.withTransaction {
            val purchase = dao.purchaseById(id) ?: return@withTransaction 0
            if (purchase.openCancelled || purchase.revision != expectedRevision) return@withTransaction 0
            val count = dao.installmentsForPurchase(id).count { it.recordId == null }
            if (count == 0 || count != expectedOpenCount) return@withTransaction 0
            saveNextPurchaseRevision(purchase.copy(openCancelled = true))
            count
        }
    }

    private suspend fun saveNextPurchaseRevision(purchase: PurchaseEntity) {
        check(dao.updatePurchase(purchase.copy(revision = Math.addExact(purchase.revision, 1L))) == 1)
    }

    suspend fun confirmInstallment(id: String, record: Record, expectedPurchaseRevision: Long? = null): Boolean = writes.withLock {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        require(record.movement == Movement.EXPENSE) { "A parcela precisa ser confirmada como despesa." }
        val saved = database.withTransaction {
            val item = dao.commitmentById(id) ?: return@withTransaction false
            val purchase = item.purchaseId?.let { dao.purchaseById(it) } ?: return@withTransaction false
            if (item.recordId != null || purchase.openCancelled ||
                (expectedPurchaseRevision != null && purchase.revision != expectedPurchaseRevision)
            ) return@withTransaction false
            dao.insertRecord(RecordEntity.from(record, null))
            check(dao.linkCommitment(id, record.id) == 1)
            saveNextPurchaseRevision(purchase)
            rememberAccount(record.bank)
            true
        }
        if (saved) withContext(Dispatchers.IO) { settings.rememberOrigin(record.bank) }
        saved
    }

    suspend fun setFixedRecurring(id: String, fromMonth: YearMonth?) = writes.withLock {
        database.withTransaction {
            if (fromMonth == null) { dao.removeFixedRecurring(id); return@withTransaction }
            CommitmentPlanning.validate(MonthlyIncomePlan(fromMonth, 0))
            val plan = dao.recurringById(id)?.toPlan() ?: throw IllegalArgumentException("Recorrência não encontrada.")
            Recurrences.error(plan)?.let { throw IllegalArgumentException(it) }
            require(plan.movement == Movement.EXPENSE) { "Somente despesas podem ser marcadas como compromissos fixos." }
            dao.upsertFixedRecurring(FixedRecurringEntity(id, fromMonth.toString()))
            for (occurrence in dao.occurrencesForRecurring(id)) {
                val date = LocalDate.parse(occurrence.dueDate)
                if (YearMonth.from(date) < fromMonth || occurrence.status != "RECORDED") continue
                val record = occurrence.recordId?.let { dao.recordById(it) } ?: continue
                if (record.movement != Movement.EXPENSE) continue
                val key = CommitmentPlanning.recurringId(id, date)
                if (dao.commitmentById(key) == null) dao.insertCommitment(CommitmentEntity(key,
                    record.description, record.amountCents, occurrence.dueDate, null, null, id, record.id))
            }
        }
    }

    fun observeCommitmentReport(month: YearMonth) = database.invalidationTracker.createFlow(
        "monthly_plans", "commitments", "purchases", "records", "occurrences", "recurring", "fixed_recurring",
    ).conflate().map { commitmentReport(month) }.distinctUntilChanged()

    suspend fun commitmentReport(month: YearMonth): CommitmentReport = withContext(Dispatchers.Default) {
        database.withTransaction {
            val plan = dao.monthlyPlan(month.toString())?.toPlan() ?: MonthlyIncomePlan(month, 0)
            CommitmentPlanning.validate(plan)
            val start = month.atDay(1).toString()
            val end = month.atEndOfMonth().toString()
            val stored = dao.commitmentsInPeriod(start, end)
            val records = stored.mapNotNull { it.recordId }.chunked(900).flatMap { dao.recordsByIds(it) }.associateBy { it.id }
            val items = stored.map { CommitmentPlanning.resolve(it.toCommitment(), it.recordId?.let { id -> records[id]?.toRecord() }) }.toMutableList()
            val occupied = items.map { it.id }.toMutableSet()
            dao.occurrencesInPeriod(start, end).forEach { occupied.add(CommitmentPlanning.recurringId(it.recurringId, LocalDate.parse(it.dueDate))) }
            val recurring = dao.allRecurring().associateBy { it.id }
            dao.allFixedRecurring().forEach { fixed ->
                val recurringPlan = recurring[fixed.recurringId]?.toPlan() ?: return@forEach
                val item = CommitmentPlanning.forecast(recurringPlan, YearMonth.parse(fixed.fromMonth), month)
                if (item != null && occupied.add(item.id)) items.add(item)
            }
            CommitmentReports.month(items, month, plan.incomeCents, plan.healthyLimitBasisPoints)
        }
    }

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
            if (plan.movement != Movement.EXPENSE) dao.removeFixedRecurring(plan.id)
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
            val fixed = dao.fixedRecurringById(id)
            if (fixed != null && YearMonth.from(LocalDate.parse(due)) >= YearMonth.parse(fixed.fromMonth)) {
                dao.insertCommitment(CommitmentEntity(CommitmentPlanning.recurringId(id, LocalDate.parse(due)), plan.title,
                    plan.amountCents, due, null, null, id, record.id.takeIf { record.movement == Movement.EXPENSE }))
            }
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
        dao.clearCommitments(); dao.clearFixedRecurring(); dao.clearPurchases(); dao.clearMonthlyPlans()
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
