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
        val ids = records.filter { it.occurredAt <= asOf }.mapTo(hashSetOf()) { it.id }
        return buildSet {
            data.recordLinks.forEach { (key, linked) -> if (linked.any { it in ids }) add(key) }
            data.rules.forEach { rule ->
                val source = rule.legacySource
                if (source != null && source.startsWith("record:") && source.removePrefix("record:") in ids)
                    add(rule.itemId to "once")
            }
        }
    }

    fun month(records: List<Record>, data: ForecastDataset?, month: YearMonth, zone: ZoneId, asOf: Long, manualSalary: Long? = null): SalarySummary {
        val realized = Ledger.month(records.filter { it.occurredAt <= asOf }, month, zone)
        val salaryEntries = realized.records.filter { it.category == Category.SALARY && it.incomeContribution() != 0L }
        val actualSalary = salaryEntries.sumOf { it.incomeContribution() }
        val period = BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))
        val projected = data?.let {
            val excluded = represented(records, it, asOf)
            buildList {
                var cycle = CycleCalendar.periodContaining(period.start, it.cycleRules)
                while (cycle.start < period.endExclusive) {
                    addAll(ForecastCalculator.report(it, cycle, excluded).lines.filter { line -> line.included && line.date in period })
                    cycle = CycleCalendar.periodContaining(cycle.endExclusive, it.cycleRules)
                }
            }.distinctBy { line -> line.itemId to line.occurrenceKey }
        }
        val salary = if (salaryEntries.isNotEmpty()) actualSalary else manualSalary ?: projected.orEmpty()
            .filter { it.included && it.kind == ForecastKind.INCOME && it.category == Category.SALARY }
            .sumOf { it.amountCents }
        return SalarySummary(salary.takeIf { it > 0 }, salaryEntries.isEmpty() && salary > 0, realized.expenses,
            projected?.filter { it.kind == ForecastKind.EXPENSE }?.sumOf { it.amountCents })
    }
}
