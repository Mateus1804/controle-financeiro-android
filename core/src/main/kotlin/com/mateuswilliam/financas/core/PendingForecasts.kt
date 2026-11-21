package com.mateuswilliam.financas.core

/** Forecasts still due after accounting for explicitly linked records. */
class PendingForecasts(dataset: ForecastDataset, records: List<Record>) {
    private val cycleRules = dataset.cycleRules
    private val schedule by lazy { ForecastCalculator.Schedule(dataset) }
    private val linked = records.mapNotNull { record ->
        val item = record.forecastItemId ?: return@mapNotNull null
        val occurrence = record.forecastOccurrenceKey ?: return@mapNotNull null
        (item to occurrence) to record
    }.groupBy({ it.first }, { it.second })

    fun lines(period: BudgetPeriod, includeCovered: Boolean = false): List<ForecastLine> = buildList {
        var cycle = CycleCalendar.periodContaining(period.start, cycleRules)
        while (cycle.start < period.endExclusive) {
            addAll(cycleLines(cycle, includeCovered).filter { it.date in period })
            cycle = CycleCalendar.periodContaining(cycle.endExclusive, cycleRules)
        }
    }.distinctBy { Triple(it.itemId, it.occurrenceKey, it.reusedInstallmentStart) }
        .sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title })

    private fun cycleLines(cycle: BudgetPeriod, includeCovered: Boolean): List<ForecastLine> {
        val forecasts = schedule.lines(cycle)
        val covered = forecasts.asSequence().filter {
            it.kind == ForecastKind.EXPENSE && !it.statementTotal && it.statementKey.isNotBlank()
        }.groupBy { it.statementKey }.mapValues { (_, components) ->
            components.sumOf { component ->
                linkedRecords(component).sumOf { it.expenseContribution() }
            }
        }.toMutableMap()
        return forecasts.mapNotNull { line ->
            if (linkedRecords(line).isNotEmpty()) {
                return@mapNotNull if (includeCovered) line.copy(included = false, hasLinkedRecord = true) else null
            }
            if (!line.included || line.kind != ForecastKind.EXPENSE || !line.statementTotal) return@mapNotNull line
            val alreadyCovered = minOf(line.amountCents, covered[line.statementKey] ?: 0L)
            covered[line.statementKey] = (covered[line.statementKey] ?: 0L) - alreadyCovered
            if (alreadyCovered == line.amountCents) {
                if (includeCovered) line.copy(included = false, coveredCents = alreadyCovered) else null
            } else line.copy(amountCents = line.amountCents - alreadyCovered, coveredCents = alreadyCovered)
        }
    }

    private fun linkedRecords(line: ForecastLine): List<Record> =
        if (line.reusedInstallmentStart == null) linked[line.itemId to line.occurrenceKey].orEmpty() else emptyList()
}
