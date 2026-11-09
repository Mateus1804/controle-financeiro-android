package com.mateuswilliam.financas.core

import java.time.LocalDate

/** Shared by the configuration preview and its persisted change. */
object CycleConfiguration {
    fun rulesAfterChange(day: Int, today: LocalDate, dataset: ForecastDataset): List<CycleRule> {
        require(day in 1..31)
        val established = dataset.cycleRules.isNotEmpty() || dataset.reserveEvents.any { it.cycleStart != null }
        if (!established) return listOf(CycleRule(LocalDate.of(1970, 1, 1), day))
        val original = dataset.cycleRules.ifEmpty { listOf(CycleRule(LocalDate.of(1970, 1, 1), 1)) }
        val boundary = CycleCalendar.periodContaining(today, original).endExclusive
        return (original.filter { it.effectiveStart < boundary } + CycleRule(boundary, day)).sortedBy { it.effectiveStart }
    }
}
