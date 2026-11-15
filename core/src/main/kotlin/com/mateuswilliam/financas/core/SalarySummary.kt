package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

data class SalarySummary(
    val salaryCents: Long?,
    val isEstimatedSalary: Boolean,
    val spentCents: Long,
    val pendingExpensesCents: Long?,
) {
    val usedPercentage: BigDecimal? get() = salaryCents?.let {
        BigDecimal.valueOf(spentCents).movePointRight(2).divide(BigDecimal.valueOf(it), 2, RoundingMode.HALF_UP)
    }
    val remainingCents: Long? get() = salaryCents?.minus(spentCents)
    val projectedRemainingCents: Long? get() = pendingExpensesCents?.let { remainingCents?.minus(it) }
}

object SalarySummaries {
    fun represented(records: List<Record>, data: ForecastDataset, asOf: Long): Set<Pair<String, String>> {
        return records.filter { it.occurredAt <= asOf && it.forecastItemId != null && it.forecastOccurrenceKey != null }
            .mapTo(hashSetOf()) { it.forecastItemId!! to it.forecastOccurrenceKey!! }
    }

    fun month(records: List<Record>, data: ForecastDataset?, month: YearMonth, zone: ZoneId, asOf: Long): SalarySummary {
        val realized = Ledger.month(records.filter { it.occurredAt <= asOf }, month, zone)
        val salaryEntries = realized.records.filter { it.category == Category.SALARY && it.incomeContribution() != 0L }
        val actualSalary = salaryEntries.sumOf { it.incomeContribution() }
        val projected = data?.let { MonthlyForecasts.lines(it, month, represented(records, it, asOf)).filter { line -> line.included } }
        val salary = if (salaryEntries.isNotEmpty()) actualSalary else projected.orEmpty()
            .filter { it.included && it.kind == ForecastKind.INCOME && it.category == Category.SALARY }
            .sumOf { it.amountCents }
        return SalarySummary(salary.takeIf { it > 0 }, salaryEntries.isEmpty() && salary > 0, realized.expenses,
            projected?.filter { it.kind == ForecastKind.EXPENSE }?.sumOf { it.amountCents })
    }
}
