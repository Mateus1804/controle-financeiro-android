package com.mateuswilliam.financas.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthlySummary(
    val records: List<Record>,
    val income: Long,
    val expenses: Long,
    val categories: Map<Category, Long>,
) {
    val result: Long get() = income - expenses
}

object Ledger {
    fun month(records: List<Record>, month: YearMonth, zone: ZoneId): MonthlySummary =
        period(records, BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1)), zone)

    fun period(records: List<Record>, period: BudgetPeriod, zone: ZoneId): MonthlySummary {
        val selected = records.filter {
            Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() in period
        }.sortedByDescending { it.occurredAt }
        val expenses = selected.sumOf {
            it.expenseContribution()
        }
        val categories = Category.entries.associateWith { category -> selected.filter { it.category == category }.sumOf {
            it.expenseContribution()
        } }.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap()
        return MonthlySummary(selected, selected.sumOf { it.incomeContribution() }, expenses, categories)
    }
}
