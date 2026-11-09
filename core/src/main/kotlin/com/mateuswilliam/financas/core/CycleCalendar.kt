package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth

object CycleCalendar {
    private fun boundary(month: YearMonth, day: Int) = month.atDay(minOf(day, month.lengthOfMonth()))

    fun periodContaining(date: LocalDate, rules: List<CycleRule>): BudgetPeriod {
        require(rules.all { it.day in 1..31 })
        val ordered = (listOf(CycleRule(LocalDate.MIN, 1)) + rules).sortedBy { it.effectiveStart }
        val index = ordered.indexOfLast { it.effectiveStart <= date }
        val rule = ordered[index]
        val month = YearMonth.from(date)
        val regular = boundary(month, rule.day)
        val previous = if (regular <= date) regular else boundary(month.minusMonths(1), rule.day)
        val start = maxOf(previous, rule.effectiveStart)
        val nextRegular = if (regular > date) regular else boundary(month.plusMonths(1), rule.day)
        val end = ordered.getOrNull(index + 1)?.effectiveStart?.let { minOf(it, nextRegular) } ?: nextRegular
        return BudgetPeriod(start, end)
    }
}
