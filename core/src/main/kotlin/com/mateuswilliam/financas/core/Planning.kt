package com.mateuswilliam.financas.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyBudget(val month: String, val category: Category, val limitCents: Long)
data class BudgetUsage(val budget: MonthlyBudget, val spentCents: Long) {
    val remainingCents: Long get() = budget.limitCents - spentCents
}

object Budgets {
    fun error(budget: MonthlyBudget): String? = when {
        runCatching { YearMonth.parse(budget.month).toString() == budget.month }.getOrDefault(false).not() -> "Informe um mês válido."
        budget.limitCents !in 1..Money.MAX_CENTS -> "Informe um limite mensal maior que zero e dentro do limite monetário."
        else -> null
    }

    fun usage(records: List<Record>, budgets: List<MonthlyBudget>, month: YearMonth, zone: ZoneId): List<BudgetUsage> {
        val totals = mutableMapOf<Category, Long>()
        records.forEach { record ->
            if (YearMonth.from(Instant.ofEpochMilli(record.occurredAt).atZone(zone)) == month) {
                val value = when (record.movement) { Movement.EXPENSE -> record.amountCents; Movement.REFUND -> -record.amountCents; else -> 0L }
                totals[record.category] = Math.addExact(totals[record.category] ?: 0L, value)
            }
        }
        return budgets.filter { it.month == month.toString() }.sortedBy { it.category.ordinal }
            .map { BudgetUsage(it, (totals[it.category] ?: 0L).coerceAtLeast(0)) }
    }
}

data class RecurringPlan(
    val id: String, val title: String, val amountCents: Long, val movement: Movement,
    val method: PaymentMethod, val category: Category, val bank: String,
    val nextDate: String, val dayOfMonth: Int, val active: Boolean = true, val lastRemindedDate: String = "",
) {
    fun proposedRecord(recordId: String, zone: ZoneId) = Record(recordId, amountCents, movement, method, category,
        bank, title, LocalDate.parse(nextDate).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
}

object Recurrences {
    fun error(plan: RecurringPlan): String? = when {
        plan.id.isBlank() || plan.id.length > 128 -> "Identificador de recorrência inválido."
        plan.movement !in setOf(Movement.EXPENSE, Movement.INCOME) -> "Recorrências aceitam apenas despesas ou receitas previstas."
        plan.dayOfMonth !in 1..31 -> "Informe um dia mensal entre 1 e 31."
        runCatching { LocalDate.parse(plan.nextDate).toString() == plan.nextDate }.getOrDefault(false).not() -> "Informe uma data de previsão válida."
        else -> RecordValidation.error(plan.proposedRecord(plan.id, ZoneId.of("UTC")))
    }

    // O dia original permanece 31 mesmo quando fevereiro termina antes dele.
    fun followingDate(current: String, dayOfMonth: Int): String {
        require(dayOfMonth in 1..31)
        val month = YearMonth.from(LocalDate.parse(current)).plusMonths(1)
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth())).toString()
    }
}
