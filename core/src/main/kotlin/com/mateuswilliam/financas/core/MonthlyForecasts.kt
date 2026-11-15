package com.mateuswilliam.financas.core

import java.time.YearMonth

/** Consulta o mês preservando as identidades das ocorrências dos ciclos configurados. */
object MonthlyForecasts {
    fun lines(data: ForecastDataset, month: YearMonth, excluded: Set<Pair<String, String>> = emptySet()): List<ForecastLine> {
        val monthPeriod = BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))
        return buildList {
            var cycle = CycleCalendar.periodContaining(monthPeriod.start, data.cycleRules)
            while (cycle.start < monthPeriod.endExclusive) {
                addAll(ForecastCalculator.report(data, cycle, excluded).lines.filter { it.date in monthPeriod })
                cycle = CycleCalendar.periodContaining(cycle.endExclusive, data.cycleRules)
            }
        }.distinctBy { it.itemId to it.occurrenceKey }.sortedBy { it.date }
    }
}
