package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal class PaydayForecastCalendar(dataset: ForecastDataset, records: List<Record>, today: LocalDate) {
    data class CycleSample(val period: BudgetPeriod, val count: Long = 1)

    private val cycleRules = dataset.cycleRules
    private fun cycleAt(date: LocalDate) = CycleCalendar.periodContaining(date, cycleRules)
    private val firstCycle = cycleAt(today)

    private val exceptions = sortedSetOf<LocalDate>().apply {
        val eventMonths = buildSet {
            for (rule in dataset.rules) {
                add(YearMonth.from(rule.effectiveFrom))
                val start = YearMonth.from(rule.startDate)
                add(start)
                if (rule.frequency == ForecastFrequency.INSTALLMENTS) {
                    for (index in 1 until rule.installmentCount) add(start.plusMonths(index.toLong()))
                }
            }
            for (override in dataset.overrides) {
                override.date?.let { add(YearMonth.from(it)) }
                originalMonth(override.occurrenceKey)?.let(::add)
            }
            val itemIds = dataset.rules.mapTo(mutableSetOf()) { it.itemId }
            for (record in records) {
                if (record.forecastItemId in itemIds) record.forecastOccurrenceKey?.let(::originalMonth)?.let(::add)
            }
        }
        // The calculator may use either month touched by a cycle. Inspect boundary cycles
        // individually whenever rule eligibility, overrides, links or installments change.
        for (month in eventMonths) {
            if (month.atEndOfMonth() < firstCycle.start) continue
            add(cycleAt(month.atDay(1)).start)
            add(cycleAt(month.atEndOfMonth()).start)
        }
        for (rule in cycleRules) {
            if (rule.effectiveStart <= firstCycle.start) continue
            add(rule.effectiveStart)
            // The previous cycle can be shortened by the new cycle rule.
            add(cycleAt(rule.effectiveStart.minusDays(1)).start)
        }
    }.tailSet(firstCycle.start, false)

    fun samples(end: LocalDate?, countRepeated: Boolean = false): Sequence<CycleSample> = sequence {
        val last = end?.let { cycleAt(it).start }
        val markers = sequenceOf(firstCycle.start) + exceptions.asSequence().takeWhile { last == null || it < last } +
            listOfNotNull(last).asSequence()
        var start = firstCycle.start
        for (marker in markers) {
            if (start < marker) yieldAll(regularCycles(start, marker, countRepeated))
            if (marker >= start && (end == null || marker < end)) {
                val cycle = cycleAt(marker)
                yield(CycleSample(cycle))
                start = cycle.endExclusive
            }
        }
        if (end == null) yieldAll(regularCycles(start, null, countRepeated = false))
    }

    private fun regularCycles(start: LocalDate, end: LocalDate?, countRepeated: Boolean): Collection<CycleSample> {
        var month = YearMonth.from(start)
        val months = end?.let { ChronoUnit.MONTHS.between(month, YearMonth.from(it)) }
        val samples = linkedMapOf<Pair<Int, Int>, CycleSample>()
        // Between exceptions, ordinary monthly forecasts depend only on clamping to the lengths
        // of the two calendar months. Every shape can appear within eight years. Gregorian month
        // lengths repeat every 400 years, so even long expense ranges can count equal cycles once.
        val limit = if (countRepeated) 4_800L else 96L
        for (index in 0 until minOf(months ?: limit, limit)) {
            val next = month.plusMonths(1)
            val shape = month.lengthOfMonth() to next.lengthOfMonth()
            val count = if (countRepeated) (checkNotNull(months) - 1 - index) / limit + 1 else 1
            val previous = samples[shape]
            if (previous == null) samples[shape] = CycleSample(cycleAt(month.atEndOfMonth()), count)
            else if (countRepeated) samples[shape] = previous.copy(count = previous.count + count)
            month = next
        }
        return samples.values
    }

    private fun originalMonth(key: String): YearMonth? = when {
        key.startsWith("month:") -> runCatching { YearMonth.parse(key.removePrefix("month:")) }.getOrNull()
        key.startsWith("cycle:") -> runCatching { YearMonth.from(LocalDate.parse(key.removePrefix("cycle:"))) }.getOrNull()
        else -> null
    }
}
