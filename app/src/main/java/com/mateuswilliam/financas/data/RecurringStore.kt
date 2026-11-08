package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.YearMonth

/** Todas as chamadas de escrita pertencem à transação do repositório. */
internal class RecurringStore(private val dao: FinanceDao) {
    suspend fun ensureOrigin(stored: RecurringEntity): RecurringEntity {
        val fixed = dao.fixedRecurringById(stored.id)?.fromMonth
        val start = stored.startMonth.ifBlank {
            (dao.occurrencesForRecurring(stored.id).map { it.dueDate.take(7) } +
                listOfNotNull(stored.nextDate.take(7), fixed)).min()
        }
        val current = if (stored.startMonth == start) stored else stored.copy(startMonth = start).also { dao.upsertRecurring(it) }
        if (dao.recurringRevision(current.id, start) == null) {
            dao.upsertRecurringRevision(RecurringRevisionEntity.from(current.toPlan(), YearMonth.parse(start), fixed))
        }
        return current
    }

    suspend fun materialize(month: YearMonth) {
        require(month.year in 1..9999) { "Use uma competência com ano entre 1 e 9999." }
        for (stored in dao.allRecurring()) {
            val current = ensureOrigin(stored)
            if (month.toString() < current.startMonth || dao.recurringMonth(current.id, month.toString()) != null) continue
            val rule = dao.recurringRevision(current.id, month.toString()) ?: continue
            val plan = RecurringMonths.planForMonth(rule.toPlan(), month)
            val fixed = rule.fixedFromMonth?.let { month >= YearMonth.parse(it) } == true && plan.movement == Movement.EXPENSE
            var row = RecurringMonthEntity.from(plan, month, fixed)
                .copy(lastRemindedDate = current.lastRemindedDate.takeIf { it == plan.nextDate }.orEmpty())
            val occurrence = dao.occurrencesForRecurring(current.id).filter { it.dueDate.take(7) == month.toString() }
                .sortedWith(compareBy<OccurrenceEntity> { when (it.status) { "RECORDED" -> 0; "REMOVED" -> 1; else -> 2 } }.thenByDescending { it.dueDate })
                .firstOrNull()
            if (occurrence != null) {
                val paid = occurrence.recordId?.let { dao.recordById(it) }
                val commitment = dao.commitmentById(CommitmentPlanning.recurringId(current.id, LocalDate.parse(occurrence.dueDate)))
                row = row.copy(title = commitment?.label ?: paid?.description ?: row.title,
                    amountCents = commitment?.amountCents ?: paid?.amountCents ?: row.amountCents,
                    movement = if (commitment != null) Movement.EXPENSE else row.movement,
                    method = paid?.method ?: row.method, category = paid?.category ?: row.category,
                    bank = paid?.bank ?: row.bank, dueDate = occurrence.dueDate,
                    status = when (occurrence.status) { "RECORDED" -> "RECORDED"; "SKIPPED" -> "SKIPPED"; else -> "PENDING" },
                    recordId = paid?.id, fixedExpense = commitment != null || fixed)
            }
            if (!current.archived || occurrence != null) dao.insertRecurringMonth(row)
        }
    }

    suspend fun months(month: YearMonth): List<RecurringMonth> {
        materialize(month)
        val origins = dao.allRecurring().associateBy { it.id }
        return dao.recurringMonths(month.toString()).filter { origins[it.recurringId]?.archived != true || it.status == "RECORDED" }
            .map { row ->
                val paid = row.recordId?.let { dao.recordById(it) }
                val plan = row.toPlan().let { if (paid?.movement == it.movement) it.copy(amountCents = paid.amountCents) else it }
                row.toMonth().copy(plan = plan)
            }
    }

    suspend fun saveTemplate(plan: RecurringPlan, effectiveMonth: YearMonth, replaceFuture: Boolean = false) {
        Recurrences.error(plan)?.let { throw IllegalArgumentException(it) }
        require(effectiveMonth.year in 1..9999) { "Use uma competência com ano entre 1 e 9999." }
        val previous = dao.recurringById(plan.id)?.let { ensureOrigin(it) }
        require(previous?.archived != true) { "Esta previsão foi excluída." }
        val start = previous?.startMonth ?: YearMonth.from(LocalDate.parse(plan.nextDate)).toString()
        require(effectiveMonth.toString() >= start) { "A configuração não pode começar antes da recorrência." }
        val effectiveRule = previous?.let { dao.recurringRevision(plan.id, effectiveMonth.toString()) }
        val fixed = (if (effectiveRule != null) effectiveRule.fixedFromMonth else dao.fixedRecurringById(plan.id)?.fromMonth)
            .takeIf { plan.movement == Movement.EXPENSE }
        val reminder = if (previous?.nextDate == plan.nextDate) previous.lastRemindedDate else ""
        dao.upsertRecurring(RecurringEntity.from(plan.copy(lastRemindedDate = reminder)).copy(startMonth = start))
        if (replaceFuture) dao.deleteFutureRecurringRevisions(plan.id, effectiveMonth.toString())
        dao.upsertRecurringRevision(RecurringRevisionEntity.from(plan, effectiveMonth, fixed))
        if (plan.movement != Movement.EXPENSE) dao.removeFixedRecurring(plan.id)
        for (row in dao.allRecurringMonths().filter { it.recurringId == plan.id && it.month >= effectiveMonth.toString() && it.status == "PENDING" }) {
            val rule = dao.recurringRevision(plan.id, row.month) ?: continue
            if (row.overridden && !replaceFuture) {
                if (row.active != rule.active) dao.updateRecurringMonth(row.copy(active = rule.active, revision = Math.addExact(row.revision, 1)))
                continue
            }
            val revised = RecurringMonths.planForMonth(rule.toPlan(), YearMonth.parse(row.month))
            dao.updateRecurringMonth(RecurringMonthEntity.from(revised, YearMonth.parse(row.month),
                rule.fixedFromMonth?.let { row.month >= it } == true && revised.movement == Movement.EXPENSE)
                .copy(revision = Math.addExact(row.revision, 1)))
        }
    }

    suspend fun saveMonth(expected: RecurringMonth, edited: RecurringPlan): Boolean {
        RecurringMonths.validateEdit(expected, edited)
        val row = editable(expected) ?: return false
        val updated = RecurringMonthEntity.from(edited.copy(dayOfMonth = row.dayOfMonth), expected.month,
            row.fixedExpense && edited.movement == Movement.EXPENSE)
        return dao.updateRecurringMonth(updated.copy(revision = Math.addExact(row.revision, 1), overridden = true)) == 1
    }

    private suspend fun editable(expected: RecurringMonth): RecurringMonthEntity? {
        val row = dao.recurringMonth(expected.recurringId, expected.month.toString()) ?: return null
        if (row.revision != expected.revision || row.status != "PENDING" || dao.recurringById(row.recurringId)?.archived != false) return null
        return row
    }

    suspend fun confirm(expected: RecurringMonth, record: Record, allowLegacyMovementChange: Boolean = false): Boolean {
        RecordValidation.error(record)?.let { throw IllegalArgumentException(it) }
        require(record.movement in setOf(Movement.EXPENSE, Movement.INCOME)) { "O registro de uma previsão precisa ser receita ou despesa." }
        val row = editable(expected)?.takeIf { it.active } ?: return false
        require(allowLegacyMovementChange || record.movement == row.movement) { "O lançamento precisa manter o tipo desta previsão. Edite a previsão do mês para alterar o tipo." }
        val occurrence = dao.occurrence(row.recurringId, row.dueDate)
        if (occurrence?.status == "RECORDED") return false
        dao.insertRecord(RecordEntity.from(record, null))
        dao.upsertOccurrence(OccurrenceEntity(row.recurringId, row.dueDate, "RECORDED", record.id))
        if (row.fixedExpense) {
            val key = CommitmentPlanning.recurringId(row.recurringId, LocalDate.parse(row.dueDate))
            val existing = dao.commitmentById(key)
            if (existing == null) dao.insertCommitment(CommitmentEntity(key, row.title, row.amountCents, row.dueDate,
                null, null, row.recurringId, record.id.takeIf { record.movement == Movement.EXPENSE }))
            else if (record.movement == Movement.EXPENSE) check(dao.linkCommitment(key, record.id) == 1)
        }
        check(dao.updateRecurringMonth(row.copy(status = "RECORDED", recordId = record.id,
            revision = Math.addExact(row.revision, 1), lastRemindedDate = "")) == 1)
        return true
    }

    suspend fun skip(expected: RecurringMonth): Boolean {
        val row = editable(expected)?.takeIf { it.active } ?: return false
        val previous = dao.occurrence(row.recurringId, row.dueDate)
        if (previous?.status == "RECORDED") return false
        if (previous?.status != "REMOVED") dao.upsertOccurrence(OccurrenceEntity(row.recurringId, row.dueDate, "SKIPPED", null))
        return dao.updateRecurringMonth(row.copy(status = "SKIPPED", revision = Math.addExact(row.revision, 1), lastRemindedDate = "")) == 1
    }

    suspend fun setFixed(id: String, fromMonth: YearMonth?) {
        val current = dao.recurringById(id)?.let { ensureOrigin(it) }
        require(current?.archived != true) { "Esta previsão foi excluída." }
        if (fromMonth != null) {
            CommitmentPlanning.validate(MonthlyIncomePlan(fromMonth, 0))
            require(current != null && current.movement == Movement.EXPENSE) { "Somente despesas podem ser marcadas como compromissos fixos." }
            dao.upsertFixedRecurring(FixedRecurringEntity(id, fromMonth.toString()))
        } else dao.removeFixedRecurring(id)
        dao.allRecurringRevisions().filter { it.recurringId == id }.forEach {
            dao.upsertRecurringRevision(it.copy(fixedFromMonth = fromMonth?.toString().takeIf { _ -> it.movement == Movement.EXPENSE }))
        }
        dao.allRecurringMonths().filter { it.recurringId == id && it.status == "PENDING" }.forEach {
            dao.updateRecurringMonth(it.copy(fixedExpense = fromMonth != null && it.month >= fromMonth.toString() && it.movement == Movement.EXPENSE,
                revision = Math.addExact(it.revision, 1)))
        }
        if (fromMonth != null) {
            for (occurrence in dao.occurrencesForRecurring(id)) {
                if (occurrence.status != "RECORDED" || occurrence.dueDate.take(7) < fromMonth.toString()) continue
                val record = occurrence.recordId?.let { dao.recordById(it) }?.takeIf { it.movement == Movement.EXPENSE } ?: continue
                val key = CommitmentPlanning.recurringId(id, LocalDate.parse(occurrence.dueDate))
                if (dao.commitmentById(key) == null) dao.insertCommitment(CommitmentEntity(key, record.description,
                    record.amountCents, occurrence.dueDate, null, null, id, record.id))
                dao.recurringMonth(id, occurrence.dueDate.take(7))?.let { row ->
                    if (row.recordId == record.id) {
                        val snapshot = checkNotNull(dao.commitmentById(key))
                        dao.updateRecurringMonth(row.copy(fixedExpense = true, amountCents = snapshot.amountCents,
                            title = snapshot.label, revision = Math.addExact(row.revision, 1)))
                    }
                }
            }
        }
    }

    /** Classificação com vigência: competências anteriores mantêm inclusive regras ainda não materializadas. */
    suspend fun setFixedFromMonth(id: String, effectiveMonth: YearMonth, enabled: Boolean, pendingOnly: Boolean = false) {
        CommitmentPlanning.validate(MonthlyIncomePlan(effectiveMonth, 0))
        val current = dao.recurringById(id)?.let { ensureOrigin(it) } ?: throw IllegalArgumentException("Recorrência não encontrada.")
        require(!current.archived) { "Esta previsão foi excluída." }
        require(effectiveMonth.toString() >= current.startMonth) { "Escolha uma competência da recorrência." }
        val rule = dao.recurringRevision(id, effectiveMonth.toString()) ?: throw IllegalArgumentException("Configuração da competência não encontrada.")
        require(!enabled || rule.movement == Movement.EXPENSE) { "Somente despesas podem ser compromissos fixos." }
        dao.upsertRecurringRevision(rule.copy(effectiveMonth = effectiveMonth.toString(), fixedFromMonth = effectiveMonth.toString().takeIf { enabled }))
        dao.allRecurringRevisions().filter { it.recurringId == id && it.effectiveMonth > effectiveMonth.toString() }.forEach {
            dao.upsertRecurringRevision(it.copy(fixedFromMonth = effectiveMonth.toString().takeIf { _ -> enabled && it.movement == Movement.EXPENSE }))
        }
        if (enabled && current.movement == Movement.EXPENSE) dao.upsertFixedRecurring(FixedRecurringEntity(id, effectiveMonth.toString()))
        else dao.removeFixedRecurring(id)
        dao.allRecurringMonths().filter { it.recurringId == id && it.month >= effectiveMonth.toString() && (it.status == "PENDING" || (!pendingOnly && enabled)) }.forEach {
            dao.updateRecurringMonth(it.copy(fixedExpense = enabled && it.movement == Movement.EXPENSE, revision = Math.addExact(it.revision, 1)))
        }
        if (enabled && !pendingOnly) {
            dao.occurrencesForRecurring(id).filter { it.status == "RECORDED" && it.dueDate.take(7) >= effectiveMonth.toString() }.forEach { occurrence ->
                val record = occurrence.recordId?.let { dao.recordById(it) }?.takeIf { it.movement == Movement.EXPENSE } ?: return@forEach
                val key = CommitmentPlanning.recurringId(id, LocalDate.parse(occurrence.dueDate))
                if (dao.commitmentById(key) == null) dao.insertCommitment(CommitmentEntity(key, record.description, record.amountCents,
                    occurrence.dueDate, null, null, id, record.id))
                dao.recurringMonth(id, occurrence.dueDate.take(7))?.takeIf { it.recordId == record.id }?.let { row ->
                    val snapshot = checkNotNull(dao.commitmentById(key))
                    dao.updateRecurringMonth(row.copy(amountCents = snapshot.amountCents, title = snapshot.label,
                        revision = Math.addExact(row.revision, 1)))
                }
            }
        }
    }

    suspend fun setActiveFromMonth(id: String, effectiveMonth: YearMonth, active: Boolean) {
        CommitmentPlanning.validate(MonthlyIncomePlan(effectiveMonth, 0))
        val current = dao.recurringById(id)?.let { ensureOrigin(it) } ?: throw IllegalArgumentException("Recorrência não encontrada.")
        require(!current.archived && effectiveMonth.toString() >= current.startMonth) { "Escolha uma competência ativa da recorrência." }
        val rule = dao.recurringRevision(id, effectiveMonth.toString()) ?: throw IllegalArgumentException("Configuração da competência não encontrada.")
        dao.upsertRecurringRevision(rule.copy(effectiveMonth = effectiveMonth.toString(), active = active))
        dao.allRecurringRevisions().filter { it.recurringId == id && it.effectiveMonth > effectiveMonth.toString() }.forEach {
            dao.upsertRecurringRevision(it.copy(active = active))
        }
        dao.allRecurringMonths().filter { it.recurringId == id && it.month >= effectiveMonth.toString() && it.status == "PENDING" && it.active != active }.forEach {
            dao.updateRecurringMonth(it.copy(active = active, revision = Math.addExact(it.revision, 1), lastRemindedDate = ""))
        }
        dao.upsertRecurring(current.copy(active = active, lastRemindedDate = ""))
    }
}
