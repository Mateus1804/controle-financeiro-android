package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.YearMonth

/** Valida referências e programação antes de abrir a transação que substitui a base real. */
internal object CommitmentSnapshotValidation {
    fun validate(snapshot: FinanceSnapshot) {
        fun unique(keys: List<String>) {
            require(keys.all { it.isNotBlank() } && keys.toSet().size == keys.size) { "Identidades de comprometimento inválidas ou repetidas." }
        }
        fun month(text: String): YearMonth = YearMonth.parse(text).also {
            require(it.toString() == text)
            CommitmentPlanning.validate(MonthlyIncomePlan(it, 0))
        }
        unique(snapshot.monthlyPlans.map { it.month }); unique(snapshot.purchases.map { it.id })
        unique(snapshot.fixedRecurring.map { it.recurringId }); unique(snapshot.commitments.map { it.id })
        unique(snapshot.commitments.mapNotNull { it.recordId })
        val drafts = snapshot.drafts.associateBy { it.id }
        val records = snapshot.records.associateBy { it.id }
        val recurring = snapshot.recurring.associateBy { it.id }
        val purchases = snapshot.purchases.associateBy { it.id }
        val occurrences = snapshot.occurrences.associateBy { it.recurringId to it.dueDate }
        unique(snapshot.records.mapNotNull { it.sourceDraftId } + snapshot.purchases.mapNotNull { it.sourceDraftId })
        snapshot.monthlyPlans.forEach { require(month(it.month) == it.toPlan().month); CommitmentPlanning.validate(it.toPlan()) }
        snapshot.fixedRecurring.forEach { require(it.recurringId in recurring); month(it.fromMonth) }
        // Impede a expansão de um arquivo pequeno em milhões de parcelas ausentes.
        require(snapshot.purchases.sumOf { it.installmentCount.toLong() - it.paidInstallments.toLong() } in 0L..50_000L) {
            "Programação de parcelas maior que o limite do backup."
        }
        val expected = mutableMapOf<String, FixedCommitment>()
        snapshot.purchases.forEach { p ->
            val schedule = CommitmentPlanning.schedule(p.toPurchase())
            require(p.nextDueDate == p.toPurchase().nextDueDate.toString())
            require(p.sourceDraftId == null || drafts[p.sourceDraftId]?.status == "SAVED") { "Origem de compra inválida." }
            require(p.revision >= 0 && (!p.openCancelled || p.revision > 0)) { "Revisão ou cancelamento de compra inválido." }
            CommitmentReports.fromInstallments(p.id, p.title, schedule).forEach { expected[it.id] = it }
        }
        require(snapshot.commitments.count { it.purchaseId != null } == expected.size) { "Parcelas incompletas no backup." }
        val occurrenceByRecord = snapshot.occurrences.mapNotNull { o -> o.recordId?.let { it to o } }.toMap()
        val byId = snapshot.commitments.associateBy { it.id }
        snapshot.commitments.forEach { c ->
            require(c.id.length <= 256 && c.label.isNotBlank() && c.label.length <= 256 && '\u0000' !in c.id && '\u0000' !in c.label)
            val date = LocalDate.parse(c.dueDate)
            require(date.toString() == c.dueDate && date.year in 1..9999 && c.amountCents in 1..Money.MAX_CENTS)
            require((c.purchaseId != null) != (c.recurringId != null)) { "O compromisso precisa ter uma única origem." }
            if (c.purchaseId != null) {
                val purchase = purchases[c.purchaseId] ?: throw IllegalArgumentException("Compra ausente no backup.")
                require(c.installmentNumber != null)
                val plan = expected[c.id] ?: throw IllegalArgumentException("Parcela não prevista na compra.")
                require(c.id == "installment:${c.purchaseId}:${c.installmentNumber}" && c.label == plan.label)
                require(if (c.adjusted) purchase.revision > 0 else c.toCommitment() == plan) { "Valor ou vencimento de parcela inconsistente." }
                require(!c.excluded || (c.recordId == null && purchase.revision > 0)) { "Uma parcela excluída não pode estar registrada." }
                require(c.recordId !in occurrenceByRecord) { "Uma despesa não pode confirmar parcela e recorrência diferentes." }
            } else {
                require(c.recurringId in recurring && c.installmentNumber == null && !c.adjusted && !c.excluded)
                require(c.id == CommitmentPlanning.recurringId(c.recurringId!!, date))
                val occurrence = occurrences[c.recurringId to c.dueDate] ?: throw IllegalArgumentException("Vencimento recorrente ausente.")
                require(occurrence.status in setOf("RECORDED", "REMOVED"))
                val recordedExpense = occurrence.recordId?.let { records[it] }?.takeIf { it.movement == Movement.EXPENSE }
                require(c.recordId == recordedExpense?.id) { "Confirmação recorrente inconsistente." }
            }
            require(c.recordId == null || records[c.recordId]?.movement == Movement.EXPENSE) { "Vínculo financeiro de compromisso inválido." }
        }
        val occurrencesByRecurring = snapshot.occurrences.groupBy { it.recurringId }
        snapshot.fixedRecurring.forEach { fixed ->
            val from = month(fixed.fromMonth)
            occurrencesByRecurring[fixed.recurringId].orEmpty().forEach { o ->
                val record = o.recordId?.let { records[it] }
                if (o.status == "RECORDED" && record?.movement == Movement.EXPENSE && YearMonth.from(LocalDate.parse(o.dueDate)) >= from) {
                    require(byId[CommitmentPlanning.recurringId(fixed.recurringId, LocalDate.parse(o.dueDate))]?.recordId == record.id) {
                        "Compromisso recorrente confirmado ausente."
                    }
                }
            }
        }
    }
}
