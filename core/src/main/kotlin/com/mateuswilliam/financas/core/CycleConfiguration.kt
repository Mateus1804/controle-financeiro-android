package com.mateuswilliam.financas.core

import java.time.LocalDate

/** Shared by the configuration preview and its persisted change. */
object CycleConfiguration {
    fun rulesAfterChange(
        day: Int, today: LocalDate, dataset: ForecastDataset, linkedOccurrences: List<String> = emptyList(),
    ): List<CycleRule> {
        require(day in 1..31)
        val original = dataset.cycleRules
        val occurrenceStarts = (linkedOccurrences.asSequence() + dataset.overrides.asSequence().map { it.occurrenceKey })
            .filter { it.startsWith("cycle:") }.distinct()
            .mapNotNull { runCatching { LocalDate.parse(it.removePrefix("cycle:")) }.getOrNull() }
        val reserveStarts = dataset.reserveEvents.asSequence().mapNotNull { it.cycleStart }
        val lastUsedStart = (occurrenceStarts + reserveStarts)
            .filter { CycleCalendar.periodContaining(it, original).start == it }.maxOrNull()
        if (original.isEmpty() && lastUsedStart == null) return listOf(CycleRule(LocalDate.of(1970, 1, 1), day))

        val boundary = CycleCalendar.periodContaining(maxOf(today, lastUsedStart ?: today), original).endExclusive
        return (original.filter { it.effectiveStart < boundary } + CycleRule(boundary, day)).sortedBy { it.effectiveStart }
    }
}
