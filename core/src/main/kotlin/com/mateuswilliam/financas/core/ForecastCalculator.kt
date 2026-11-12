package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

object ForecastCalculator {
    private data class Occurrence(val key: String, val date: LocalDate, val amount: Long)
    private fun monthDate(month: YearMonth, day: Int) = month.atDay(minOf(day, month.lengthOfMonth()))

    fun report(dataset: ForecastDataset, period: BudgetPeriod, excluded: Set<Pair<String, String>> = emptySet()): ForecastReport {
        val overrides = dataset.overrides.associateBy { it.itemId to it.occurrenceKey }
        val lines = mutableListOf<ForecastLine>()
        val salaryMonths = listOf(YearMonth.from(period.start), YearMonth.from(period.endExclusive.minusDays(1)))
            .distinct().mapNotNull { month -> SalaryForecasts.forMonth(dataset, month)?.let { month to it } }.toMap()
        val rules = dataset.rules.filterNot { SalaryForecasts.manages(it.itemId) }
        rules.groupBy { it.itemId }.forEach { (itemId, versions) ->
            val ordered = versions.sortedBy { it.effectiveFrom }
            val candidates = linkedMapOf<String, Pair<ForecastRule, Occurrence>>()
            ordered.forEach { rule ->
                val occurrences = when (rule.frequency) {
                    ForecastFrequency.ONCE -> listOf(Occurrence("once", rule.startDate, rule.amountCents))
                    ForecastFrequency.INSTALLMENTS -> Installments.fromTotal(rule.amountCents, rule.installmentCount,
                        monthDate(YearMonth.from(rule.startDate), rule.dayOfMonth), dayOfMonth = rule.dayOfMonth).pending.map {
                        Occurrence("installment:${it.number}", it.dueDate, it.amountCents)
                    }
                    ForecastFrequency.MONTHLY -> if (rule.cycleAnchored) {
                        if (rule.startDate < period.endExclusive) listOf(Occurrence("cycle:${period.start}", period.start, rule.amountCents)) else emptyList()
                    } else {
                        // Include moved occurrences even when their original month is outside the report.
                        val months = mutableSetOf(YearMonth.from(period.start), YearMonth.from(period.endExclusive.minusDays(1)))
                        overrides.keys.filter { it.first == itemId && it.second.startsWith("month:") }.forEach {
                            runCatching { YearMonth.parse(it.second.removePrefix("month:")) }.getOrNull()?.let(months::add)
                        }
                        months.map { Occurrence("month:$it", monthDate(it, rule.dayOfMonth), rule.amountCents) }
                    }
                }
                occurrences.forEach { occurrence ->
                    val existing = candidates[occurrence.key]
                    val hasOverride = overrides.containsKey(itemId to occurrence.key)
                    val started = occurrence.date >= rule.startDate || rule.cycleAnchored || hasOverride ||
                        ((rule.frequency == ForecastFrequency.INSTALLMENTS || existing != null) &&
                            YearMonth.from(occurrence.date) >= YearMonth.from(rule.startDate))
                    if (started) {
                        if (existing == null) {
                            val eligibilityDate = if (rule.cycleAnchored) maxOf(period.start, rule.startDate) else occurrence.date
                            if (rule.effectiveFrom <= eligibilityDate) candidates[occurrence.key] = rule to occurrence
                        } else if (existing.second.date >= rule.effectiveFrom) {
                            // A stable occurrence belongs to its previous schedule until the change boundary.
                            // Changes never move an already closed occurrence into a later cycle. If a new
                            // due day precedes the change boundary, its transition occurs at that boundary.
                            candidates[occurrence.key] = rule to occurrence.copy(date = maxOf(occurrence.date, rule.effectiveFrom))
                        }
                    }
                }
            }
            candidates.forEach { (key, pair) ->
                if ((itemId to key) in excluded) return@forEach
                val (rule, occurrence) = pair
                val override = overrides[itemId to key]
                val date = override?.date ?: occurrence.date
                if (date in period && (override?.active ?: rule.active)) {
                    val ignored = override?.ignored == true
                    lines += ForecastLine(itemId, key, override?.title ?: rule.title, date,
                        override?.amountCents ?: occurrence.amount, rule.kind, override?.category ?: rule.category,
                        rule.group, rule.frequency, rule.estimated, ignored, !ignored,
                        rule.statementKey, rule.statementTotal, rule)
                }
            }
        }
        lines.removeAll { line ->
            line.kind == ForecastKind.INCOME && line.category == Category.SALARY &&
                YearMonth.from(line.date) in salaryMonths
        }
        salaryMonths.values.filter { it.startDate in period }.forEach { rule ->
            lines += ForecastLine(rule.itemId, "once", rule.title, rule.startDate, rule.amountCents,
                rule.kind, rule.category, rule.group, rule.frequency, false, false, true, "", false, rule)
        }
        val statements = lines.filter { it.included && it.statementTotal && it.statementKey.isNotBlank() }
            .map { it.statementKey to it.kind }.toSet()
        val finalLines = lines.map { line ->
            if (!line.statementTotal && line.statementKey.isNotBlank() && (line.statementKey to line.kind) in statements)
                line.copy(included = false) else line
        }.sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title }.thenBy { it.itemId })
        val included = finalLines.filter { it.included }
        val income = included.filter { it.kind == ForecastKind.INCOME }.sumOf { it.amountCents }
        val expenses = included.filter { it.kind == ForecastKind.EXPENSE }
        val commitments = expenses.filter { it.group == ForecastGroup.COMMITMENT }.sumOf { it.amountCents }
        val variable = expenses.filter { it.group == ForecastGroup.VARIABLE }.sumOf { it.amountCents }
        val total = commitments + variable
        val margin = income - total
        val allocated = dataset.reserveEvents.filter { it.kind == ReserveEventKind.ADD && it.cycleStart == period.start }.sumOf { it.amountCents }
        return ForecastReport(period, finalLines, income, commitments, variable, total, margin,
            if (income > 0) BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(income), 2, RoundingMode.HALF_UP) else null,
            allocated, maxOf(0L, margin - allocated), reserveBalance(dataset.reserveEvents))
    }

    fun reserveBalance(events: List<ReserveEvent>): Long? {
        var balance: Long? = null
        events.sortedWith(compareBy<ReserveEvent> { it.createdAt }.thenBy { it.id }).forEach { event ->
            require(ForecastValidation.reserveEventError(event.kind, event.amountCents, balance) == null)
            balance = when (event.kind) {
                ReserveEventKind.SET -> event.amountCents
                ReserveEventKind.ADD -> Math.addExact(balance ?: 0L, event.amountCents)
                ReserveEventKind.WITHDRAW -> balance!! - event.amountCents
            }
        }
        return balance
    }
}
