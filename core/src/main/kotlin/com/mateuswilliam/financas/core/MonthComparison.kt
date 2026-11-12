package com.mateuswilliam.financas.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CategoryDifference(val category: Category, val currentCents: Long, val previousCents: Long) {
    val deltaCents: Long get() = currentCents - previousCents
}

/**
 * Gastos líquidos por categoria contra o mês anterior. Para o mês em andamento, [throughDay] limita
 * os dois meses aos mesmos dias (o anterior é limitado ao seu último dia).
 */
data class MonthComparison(
    val current: YearMonth, val previous: YearMonth, val throughDay: Int?, val differences: List<CategoryDifference>,
) {
    val currentTotalCents: Long get() = differences.sumOf { it.currentCents }
    val previousTotalCents: Long get() = differences.sumOf { it.previousCents }
}

object MonthComparisons {
    fun compare(records: List<Record>, month: YearMonth, today: LocalDate, zone: ZoneId): MonthComparison {
        val previous = month.minusMonths(1)
        val inProgress = YearMonth.from(today) == month
        val throughDay = if (inProgress) today.dayOfMonth else null
        fun totals(target: YearMonth): Map<Category, Long> {
            val limit = throughDay?.let { minOf(it, target.lengthOfMonth()) }
            return records.filter {
                val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
                YearMonth.from(date) == target && (limit == null || date.dayOfMonth <= limit)
            }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.expenseContribution() } }
        }
        val now = totals(month); val before = totals(previous)
        val diffs = (now.keys + before.keys).map { CategoryDifference(it, now[it] ?: 0L, before[it] ?: 0L) }
            .filter { it.currentCents != 0L || it.previousCents != 0L }
            .sortedWith(compareByDescending<CategoryDifference> { kotlin.math.abs(it.deltaCents) }.thenBy { it.category.name })
        return MonthComparison(month, previous, throughDay, diffs)
    }

    /** Texto como "+R$ 120,00"; a diferença nula não ganha sinal. */
    fun signed(deltaCents: Long): String = when {
        deltaCents > 0 -> "+${Money.format(deltaCents)}"
        deltaCents < 0 -> "−${Money.format(-deltaCents)}"
        else -> Money.format(0)
    }
}
