package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth

data class InstallmentPurchase(
    val id: String, val title: String, val bank: String, val category: Category,
    val totalCents: Long, val installmentCount: Int, val nextDueDate: LocalDate,
    val paidInstallments: Int = 0, val dayOfMonth: Int = nextDueDate.dayOfMonth,
    val method: PaymentMethod = PaymentMethod.CARD,
)

data class MonthlyIncomePlan(val month: YearMonth, val incomeCents: Long, val healthyLimitBasisPoints: Int = 3000)

data class InstallmentAdjustment(val amountCents: Long, val dueDate: LocalDate) {
    init {
        require(amountCents in 1..Money.MAX_CENTS) { "Informe um valor válido para a parcela." }
        require(dueDate.year in 1..9999) { "Use vencimento com ano entre 1 e 9999." }
    }
}

object CommitmentPlanning {
    fun schedule(purchase: InstallmentPurchase): InstallmentSchedule {
        require(purchase.id.isNotBlank() && purchase.id.length <= 128 && '\u0000' !in purchase.id) { "Identificador de compra inválido." }
        require(purchase.title.isNotBlank() && purchase.title.length <= 200 && '\u0000' !in purchase.title) { "Informe uma descrição de até 200 caracteres." }
        require(purchase.bank.isNotBlank() && purchase.bank.length <= 80 && '\u0000' !in purchase.bank) { "Informe uma conta de até 80 caracteres." }
        val schedule = Installments.fromTotal(purchase.totalCents, purchase.installmentCount,
            purchase.nextDueDate, purchase.paidInstallments, purchase.dayOfMonth)
        return schedule.requirePersistableDates()
    }

    fun validate(plan: MonthlyIncomePlan) {
        require(plan.month.year in 1..9999) { "Use um mês com ano entre 1 e 9999." }
        CommitmentReports.month(emptyList(), plan.month, plan.incomeCents, plan.healthyLimitBasisPoints)
    }

    fun recurringId(id: String, date: LocalDate) = "recurring:$id:$date"

    fun forecast(plan: RecurringPlan, fromMonth: YearMonth, month: YearMonth): FixedCommitment? {
        if (!plan.active || plan.movement != Movement.EXPENSE || month < fromMonth) return null
        Recurrences.error(plan)?.let { throw IllegalArgumentException(it) }
        val next = LocalDate.parse(plan.nextDate)
        val due = if (YearMonth.from(next) == month) next else month.atDay(minOf(plan.dayOfMonth, month.lengthOfMonth()))
        if (due < next) return null
        return FixedCommitment(recurringId(plan.id, due), plan.title, plan.amountCents, due)
    }

    /** O vencimento identifica a obrigação; a data efetiva do pagamento não muda seu mês. */
    fun resolve(expected: FixedCommitment, record: Record?): FixedCommitment {
        if (record == null) return expected
        require(record.movement == Movement.EXPENSE && RecordValidation.error(record) == null) { "O compromisso precisa estar vinculado a uma despesa válida." }
        return expected.copy(amountCents = record.amountCents)
    }
}

internal fun InstallmentSchedule.requirePersistableDates(): InstallmentSchedule {
    require(pending.all { it.dueDate.year in 1..9999 }) { "Use vencimentos com ano entre 1 e 9999." }
    return this
}
