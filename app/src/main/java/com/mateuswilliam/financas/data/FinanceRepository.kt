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
    private val recurringStore = RecurringStore(dao)
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
    val allAccounts = combine(dao.accounts(), dao.usedAccounts()) { saved, used ->
        AccountCatalog.merge(saved.map { it.toOption() }, used.map { AccountOption("", it.label, it.lastUsedAt) })
    }.flowOn(Dispatchers.Default)
    val accounts = combine(allAccounts, dao.accounts()) { merged, saved ->
        val archived = saved.filter { it.archived }.map { it.nameKey }.toSet()
        merged.filterNot { it.key in archived }
    }
    val accountBalanceEvents = dao.accountBalanceEvents().map { rows -> rows.map { it.toReference() } }
    fun observeAccountBalances(asOf: Long = System.currentTimeMillis()) = database.invalidationTracker.createFlow(
        "accounts", "account_balance_events", "records",
    ).conflate().map {
        database.withTransaction {
            val saved = dao.allAccounts()
            val entries = dao.allRecords().map { it.toRecord() }
            AccountBalances.calculate(AccountCatalog.options(saved.map { it.toOption() }, entries),
                saved.filter { it.archived }.map { it.nameKey }.toSet(), dao.allAccountBalanceEvents().map { it.toReference() }, entries, asOf)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
    suspend fun accountBalanceHistory(key: String) = dao.allAccountBalanceEvents().filter { it.accountKey == key }.map { it.toReference() }

    suspend fun configureAccountBalance(name: String, balanceCents: Long, effectiveAt: Long, reason: String) = writes.withLock {
        val label = AccountNames.clean(name)
        require(label.isNotBlank() && label.length <= 80 && '\u0000' !in label) { "Informe um nome de conta com até 80 caracteres." }
        require(balanceCents in -Money.MAX_CENTS..Money.MAX_CENTS) { "Saldo fora do limite permitido." }
        val date = java.time.Instant.ofEpochMilli(effectiveAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        require(effectiveAt > 0 && effectiveAt == date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) { "Informe o início da data de referência." }
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank() && cleanReason.length <= 200 && '\u0000' !in cleanReason) { "Informe um motivo com até 200 caracteres." }
        database.withTransaction {
            rememberAccount(label)
            dao.insertAccountBalanceEvent(AccountBalanceEventEntity(accountKey = AccountNames.key(label), balanceCents = balanceCents,
                effectiveAt = effectiveAt, createdAt = System.currentTimeMillis(), reason = cleanReason))
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
            val original = record.originalRecordId?.let { dao.recordById(it)?.toRecord() }
            val record = if (original != null) Refunds.applyOriginal(record, original) else record
            RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
            if (record.movement == Movement.TRANSFER) {
                val previous = if (editing) dao.recordById(record.id) else null
                val unchangedLegacy = previous?.movement == Movement.TRANSFER &&
                    AccountNames.key(previous.bank) == AccountNames.key(record.bank) && AccountNames.key(previous.destination) == AccountNames.key(record.destination)
                require(AccountNames.key(record.bank) != AccountNames.key(record.destination)) { "Escolha contas diferentes para a transferência." }
                if (!unchangedLegacy) require(dao.accountByKey(AccountNames.key(record.bank))?.archived == false &&
                    dao.accountByKey(AccountNames.key(record.destination))?.archived == false) { "Cadastre as duas contas próprias antes de transferir." }
            }
            if (editing) {
                val previous = dao.recordById(record.id) ?: return@withTransaction false
                if (record.movement != Movement.EXPENSE) unlinkCommitment(record.id)
                val updated = dao.updateRecord(RecordEntity.from(record, previous.sourceDraftId)) == 1
                if (updated) {
                    dao.refundsForRecord(record.id).forEach { refund ->
                        val normalized = Refunds.applyOriginal(refund.toRecord(), record)
                        check(dao.updateRecord(RecordEntity.from(normalized, refund.sourceDraftId)) == 1)
                    }
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
            dao.reopenRecurringMonth(id)
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
        val (item, purchase) = openInstallment(installmentId) ?: return@withTransaction null
        val installments = dao.installmentsForPurchase(purchase.id)
        PurchaseManagement(purchase.toPurchase(), purchase.revision, item.id,
            Installment(checkNotNull(item.installmentNumber), LocalDate.parse(item.dueDate), item.amountCents),
            installments.count { it.isOpenInstallment() }, installments.canReplaceSchedule())
    }

    private fun CommitmentEntity.isOpenInstallment() =
        purchaseId != null && installmentNumber != null && recordId == null && !excluded

    private fun List<CommitmentEntity>.canReplaceSchedule() =
        isNotEmpty() && all { it.isOpenInstallment() && !it.adjusted }

    // Consultada dentro da mesma transação que altera ou confirma a parcela.
    private suspend fun openInstallment(id: String, expectedRevision: Long? = null): Pair<CommitmentEntity, PurchaseEntity>? {
        val item = dao.commitmentById(id)?.takeIf { it.isOpenInstallment() } ?: return null
        val purchase = item.purchaseId?.let { dao.purchaseById(it) } ?: return null
        if (purchase.openCancelled || (expectedRevision != null && purchase.revision != expectedRevision)) return null
        return item to purchase
    }

    suspend fun editPurchase(expectedRevision: Long, purchase: InstallmentPurchase): Boolean = writes.withLock {
        val schedule = CommitmentPlanning.schedule(purchase)
        database.withTransaction {
            val previous = dao.purchaseById(purchase.id) ?: return@withTransaction false
            if (previous.openCancelled || previous.revision != expectedRevision) return@withTransaction false
            val installments = dao.installmentsForPurchase(purchase.id)
            if (!installments.canReplaceSchedule()) return@withTransaction false
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
            val (item, purchase) = openInstallment(id, expectedRevision) ?: return@withTransaction false
            val date = adjustment.dueDate.toString()
            if (item.amountCents == adjustment.amountCents && item.dueDate == date) return@withTransaction true
            check(dao.adjustOpenInstallment(id, adjustment.amountCents, date) == 1)
            saveNextPurchaseRevision(purchase)
            true
        }
    }

    suspend fun excludeInstallmentForecast(id: String, expectedRevision: Long): Boolean = writes.withLock {
        database.withTransaction {
            val (_, purchase) = openInstallment(id, expectedRevision) ?: return@withTransaction false
            check(dao.excludeOpenInstallment(id) == 1)
            saveNextPurchaseRevision(purchase)
            true
        }
    }

    suspend fun cancelOpenInstallments(id: String, expectedRevision: Long, expectedOpenCount: Int): Int = writes.withLock {
        database.withTransaction {
            val purchase = dao.purchaseById(id) ?: return@withTransaction 0
            if (purchase.openCancelled || purchase.revision != expectedRevision) return@withTransaction 0
            val count = dao.installmentsForPurchase(id).count { it.isOpenInstallment() }
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
            val (_, purchase) = openInstallment(id, expectedPurchaseRevision) ?: return@withTransaction false
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
        database.withTransaction { recurringStore.setFixed(id, fromMonth) }
    }
    suspend fun setFixedRecurringFromMonth(id: String, effectiveMonth: YearMonth, enabled: Boolean) = writes.withLock {
        database.withTransaction { recurringStore.setFixedFromMonth(id, effectiveMonth, enabled) }
    }
    suspend fun setRecurringActiveFromMonth(id: String, effectiveMonth: YearMonth, active: Boolean) = writes.withLock {
        database.withTransaction { recurringStore.setActiveFromMonth(id, effectiveMonth, active) }
    }

    fun observeRecurringMonths(month: YearMonth) = database.invalidationTracker.createFlow(
        "recurring", "recurring_revisions", "recurring_months", "occurrences", "records", "fixed_recurring", "commitments",
    ).conflate().map { database.withTransaction { recurringStore.months(month) } }.distinctUntilChanged()

    suspend fun recurringConfiguration(id: String, month: YearMonth): RecurringPlan? = database.withTransaction {
        require(month.year in 1..9999) { "Use uma competência com ano entre 1 e 9999." }
        val origin = dao.recurringById(id)?.let { recurringStore.ensureOrigin(it) } ?: return@withTransaction null
        if (origin.archived || month.toString() < origin.startMonth) return@withTransaction null
        dao.recurringRevision(id, month.toString())?.let { RecurringMonths.planForMonth(it.toPlan(), month) }
    }

    suspend fun saveRecurringMonth(expected: RecurringMonth, edited: RecurringPlan): Boolean = writes.withLock {
        database.withTransaction {
            val saved = recurringStore.saveMonth(expected, edited)
            if (saved) rememberAccount(edited.bank)
            saved
        }
    }

    suspend fun confirmRecurringMonth(expected: RecurringMonth, record: Record): Boolean = writes.withLock {
        val saved = database.withTransaction {
            val confirmed = recurringStore.confirm(expected, record)
            if (confirmed) rememberAccount(record.bank)
            confirmed
        }
        if (saved) withContext(Dispatchers.IO) { settings.rememberOrigin(record.bank) }
        saved
    }

    suspend fun skipRecurringMonth(expected: RecurringMonth): Boolean = writes.withLock {
        database.withTransaction { recurringStore.skip(expected) }
    }

    fun observeCommitmentReport(month: YearMonth) = database.invalidationTracker.createFlow(
        "monthly_plans", "commitments", "purchases", "records", "occurrences", "recurring", "fixed_recurring", "recurring_months", "recurring_revisions",
    ).conflate().map { commitmentReport(month) }.distinctUntilChanged()

    suspend fun commitmentReport(month: YearMonth): CommitmentReport = withContext(Dispatchers.Default) {
        database.withTransaction {
            val plan = dao.monthlyPlan(month.toString())?.toPlan() ?: MonthlyIncomePlan(month, 0)
            CommitmentPlanning.validate(plan)
            recurringStore.materialize(month)
            val monthly = dao.recurringMonths(month.toString()).associateBy { it.recurringId }
            val origins = dao.allRecurring().associateBy { it.id }
            val stored = dao.commitmentsInPeriod(month.atDay(1).toString(), month.atEndOfMonth().toString())
            // A linha mensal substitui sua fotografia legada. Pagamentos legados adicionais
            // da mesma competência continuam contados e nunca são apagados pela migração.
            val retained = stored.filter { item ->
                val row = item.recurringId?.let { monthly[it] }
                row == null || (item.recordId != null && item.recordId != row.recordId)
            }
            val ids = (retained.mapNotNull { it.recordId } + monthly.values.mapNotNull { it.recordId }).distinct()
            val records = ids.chunked(900).flatMap { dao.recordsByIds(it) }.associateBy { it.id }
            val items = retained.map { CommitmentPlanning.resolve(it.toCommitment(), it.recordId?.let { id -> records[id]?.toRecord() }) }.toMutableList()
            monthly.values.forEach { row ->
                if (!row.fixedExpense || row.movement != Movement.EXPENSE || row.status == "SKIPPED") return@forEach
                if (row.status == "PENDING" && (!row.active || origins[row.recurringId]?.archived == true)) return@forEach
                val expected = FixedCommitment(RecurringMonths.commitmentId(row.recurringId, month), row.title,
                    row.amountCents, LocalDate.parse(row.dueDate))
                val paid = row.recordId?.let { records[it]?.toRecord() }?.takeIf { it.movement == Movement.EXPENSE }
                items.add(CommitmentPlanning.resolve(expected, paid))
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
        database.withTransaction {
            require(dao.occurrence(plan.id, plan.nextDate) == null) { "Esse vencimento já foi tratado. Escolha outra data para a previsão." }
            recurringStore.saveTemplate(plan, YearMonth.from(LocalDate.parse(plan.nextDate)))
            rememberAccount(plan.bank)
        }
    }

    suspend fun saveRecurringFromMonth(plan: RecurringPlan, effectiveMonth: YearMonth) = writes.withLock {
        database.withTransaction {
            recurringStore.saveTemplate(plan, effectiveMonth, replaceFuture = true)
            rememberAccount(plan.bank)
        }
    }

    /** Cadastro unificado: configuração e classificação são gravadas na mesma transação. */
    suspend fun saveRecurringMovement(plan: RecurringPlan, effectiveMonth: YearMonth, fixedExpense: Boolean) = writes.withLock {
        database.withTransaction {
            recurringStore.saveTemplate(plan, effectiveMonth, replaceFuture = true)
            recurringStore.setFixedFromMonth(plan.id, effectiveMonth, fixedExpense && plan.movement == Movement.EXPENSE, pendingOnly = true)
            rememberAccount(plan.bank)
        }
    }

    suspend fun archiveRecurringForecast(expected: RecurringPlan): Boolean = writes.withLock {
        database.withTransaction {
            val current = dao.recurringById(expected.id) ?: return@withTransaction false
            if (current.archived || current.toPlan().copy(lastRemindedDate = expected.lastRemindedDate) != expected) return@withTransaction false
            dao.upsertRecurring(current.copy(active = false, lastRemindedDate = "", archived = true))
            true
        }
    }

    /** Adaptador legado; a interface mensal não depende nem avança este cursor. */
    suspend fun confirmRecurring(id: String, due: String, record: Record): Boolean = writes.withLock {
        val saved = database.withTransaction {
            val template = dao.recurringById(id) ?: return@withTransaction false
            if (!template.active || template.archived || template.nextDate != due || dao.occurrence(id, due) != null) return@withTransaction false
            val month = YearMonth.from(LocalDate.parse(due))
            recurringStore.materialize(month)
            val expected = dao.recurringMonth(id, month.toString())?.toMonth() ?: return@withTransaction false
            if (!recurringStore.confirm(expected, record, allowLegacyMovementChange = true)) return@withTransaction false
            check(dao.advanceRecurring(id, due, Recurrences.followingDate(due, template.dayOfMonth)) == 1)
            rememberAccount(record.bank)
            true
        }
        if (saved) withContext(Dispatchers.IO) { settings.rememberOrigin(record.bank) }
        saved
    }

    suspend fun skipRecurring(id: String, due: String): Boolean = writes.withLock {
        database.withTransaction {
            val template = dao.recurringById(id) ?: return@withTransaction false
            if (!template.active || template.archived || template.nextDate != due || dao.occurrence(id, due) != null) return@withTransaction false
            val month = YearMonth.from(LocalDate.parse(due))
            recurringStore.materialize(month)
            val expected = dao.recurringMonth(id, month.toString())?.toMonth() ?: return@withTransaction false
            if (!recurringStore.skip(expected)) return@withTransaction false
            check(dao.advanceRecurring(id, due, Recurrences.followingDate(due, template.dayOfMonth)) == 1)
            true
        }
    }

    suspend fun notifyDue(today: LocalDate, send: (RecurringPlan) -> Boolean) = writes.withLock {
        if (!settings.snapshot().remindersEnabled) return@withLock
        database.withTransaction {
            recurringStore.materialize(YearMonth.from(today))
            dao.allRecurring().filterNot { it.archived }.map { YearMonth.from(LocalDate.parse(it.nextDate)) }.distinct()
                .filter { it <= YearMonth.from(today) }.forEach { recurringStore.materialize(it) }
        }
        val origins = dao.allRecurring().associateBy { it.id }
        dao.allRecurringMonths().filter { it.status == "PENDING" && it.active && origins[it.recurringId]?.archived == false &&
            !LocalDate.parse(it.dueDate).isAfter(today) && it.lastRemindedDate != it.dueDate }
            .forEach { if (settings.snapshot().remindersEnabled && send(it.toPlan())) database.withTransaction {
                val current = dao.recurringMonth(it.recurringId, it.month)
                if (current?.status == "PENDING" && current.revision == it.revision) {
                    dao.updateRecurringMonth(current.copy(lastRemindedDate = it.dueDate))
                    dao.markReminded(it.recurringId, it.dueDate)
                }
            } }
    }

    suspend fun snapshot(): FinanceSnapshot = writes.withLock {
        val display = settings.snapshot().let { BackupDisplay(it.appearance, it.hideAmounts, it.lastOrigin) }
        database.withTransaction { readSnapshot(dao, System.currentTimeMillis(), display) }
    }

    suspend fun restore(original: FinanceSnapshot) = writes.withLock {
        val snapshot = RecurringSnapshots.upgrade(original)
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
        dao.clearRecurringMonths(); dao.clearRecurringRevisions()
        dao.clearOccurrences(); dao.clearRecurring(); dao.clearBudgets()
        dao.clearRecords(); dao.clearDrafts(); dao.clearAccountBalanceEvents(); dao.clearAccounts()
    }

    suspend fun clear() = writes.withLock {
        // O bloqueio permanece configurado até a transação apagar os dados financeiros.
        withContext(Dispatchers.IO) { settings.pauseForRestore() }
        database.withTransaction { clearTables() }
        withContext(Dispatchers.IO) { runCatching { settings.reset() }.isSuccess }
    }
}
