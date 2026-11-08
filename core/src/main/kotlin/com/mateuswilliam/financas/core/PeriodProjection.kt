package com.mateuswilliam.financas.core

import java.time.YearMonth
import java.time.ZoneId

data class PeriodProjection(
    val realized: MonthlySummary,
    val pendingIncome: Long,
    val pendingExpenses: Long,
    val pendingCount: Int,
) {
    val projectedResult: Long get() = realized.result + pendingIncome - pendingExpenses
}

/** Forecasts are only unfulfilled occurrences; analytical budgets/reference income are not transactions. */
object PeriodProjections {
    fun month(records: List<Record>, pending: List<Record>, month: YearMonth, zone: ZoneId, asOf: Long = System.currentTimeMillis()): PeriodProjection {
        val realized = records.filter { it.occurredAt <= asOf }
        val realizedIds = realized.mapTo(hashSetOf()) { it.id }
        val actual = Ledger.month(realized, month, zone)
        val pendingSummary = Ledger.month((records.filter { it.occurredAt > asOf } + pending).distinctBy { it.id }.filterNot { candidate ->
            candidate.id in realizedIds
        }, month, zone)
        return PeriodProjection(actual, pendingSummary.income, pendingSummary.expenses, pendingSummary.records.size)
    }
}
