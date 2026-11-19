package com.mateuswilliam.financas.core

/** Forecasts still due after accounting for explicitly linked records. */
class PendingForecasts(dataset: ForecastDataset, records: List<Record>) {
    private val data = dataset.copy(reserveEvents = emptyList())
    private val linked = records.mapNotNull { record ->
        val item = record.forecastItemId ?: return@mapNotNull null
        val occurrence = record.forecastOccurrenceKey ?: return@mapNotNull null
        (item to occurrence) to record
    }.groupBy({ it.first }, { it.second })

    fun lines(period: BudgetPeriod): List<ForecastLine> = buildList {
        var cycle = CycleCalendar.periodContaining(period.start, data.cycleRules)
        while (cycle.start < period.endExclusive) {
            addAll(cycleLines(cycle).filter { it.date in period })
            cycle = CycleCalendar.periodContaining(cycle.endExclusive, data.cycleRules)
        }
    }.distinctBy { it.itemId to it.occurrenceKey }.sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title })

    private fun cycleLines(cycle: BudgetPeriod): List<ForecastLine> {
        val forecasts = ForecastCalculator.report(data, cycle).lines
        val covered = forecasts.asSequence().filter {
            it.kind == ForecastKind.EXPENSE && !it.statementTotal && it.statementKey.isNotBlank()
        }.groupBy { it.statementKey }.mapValues { (_, components) ->
            components.sumOf { component ->
                linked[component.itemId to component.occurrenceKey].orEmpty().sumOf { it.expenseContribution() }
            }
        }.toMutableMap()
        return forecasts.mapNotNull { line ->
            if ((line.itemId to line.occurrenceKey) in linked) return@mapNotNull null
            if (!line.included || line.kind != ForecastKind.EXPENSE || !line.statementTotal) return@mapNotNull line
            val alreadyCovered = minOf(line.amountCents, covered[line.statementKey] ?: 0L)
            covered[line.statementKey] = (covered[line.statementKey] ?: 0L) - alreadyCovered
            if (alreadyCovered == line.amountCents) null
            else line.copy(amountCents = line.amountCents - alreadyCovered, coveredCents = alreadyCovered)
        }
    }
}
