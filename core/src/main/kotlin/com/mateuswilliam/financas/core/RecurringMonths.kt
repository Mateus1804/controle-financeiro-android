package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth

enum class RecurringMonthStatus { PENDING, RECORDED, SKIPPED }

/** A competência é a identidade; corrigir o vencimento não cria outra obrigação. */
data class RecurringMonth(
    val recurringId: String,
    val month: YearMonth,
    val plan: RecurringPlan,
    val status: RecurringMonthStatus,
    val recordId: String?,
    val fixedExpense: Boolean,
    val revision: Long,
)

object RecurringMonths {
    fun planForMonth(template: RecurringPlan, month: YearMonth): RecurringPlan {
        require(month.year in 1..9999) { "Use uma competência com ano entre 1 e 9999." }
        val anchor = LocalDate.parse(template.nextDate)
        val due = if (YearMonth.from(anchor) == month) anchor else month.atDay(minOf(template.dayOfMonth, month.lengthOfMonth()))
        return template.copy(nextDate = due.toString(), lastRemindedDate = "")
    }

    fun validateEdit(expected: RecurringMonth, edited: RecurringPlan) {
        require(edited.id == expected.recurringId) { "A identidade da recorrência não pode mudar." }
        Recurrences.error(edited)?.let { throw IllegalArgumentException(it) }
        require(YearMonth.from(LocalDate.parse(edited.nextDate)) == expected.month) {
            "Escolha um vencimento dentro da competência ${expected.month}."
        }
    }

    fun commitmentId(id: String, month: YearMonth) = "recurring-month:$id:$month"
}
