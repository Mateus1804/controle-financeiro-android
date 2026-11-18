package com.mateuswilliam.financas.core

import java.time.YearMonth

/** A month labels all financial cycles starting in it, including a short cycle after a day change. */
object FinancialPeriods {
    private fun firstStart(month: YearMonth, rules: List<CycleRule>): java.time.LocalDate {
        val first = month.atDay(1)
        val containing = CycleCalendar.periodContaining(first, rules)
        return if (containing.start == first) first else containing.endExclusive
    }

    fun month(month: YearMonth, rules: List<CycleRule>): BudgetPeriod =
        BudgetPeriod(firstStart(month, rules), firstStart(month.plusMonths(1), rules))

    fun previous(period: BudgetPeriod, rules: List<CycleRule>): BudgetPeriod =
        month(YearMonth.from(period.start).minusMonths(1), rules)
}
