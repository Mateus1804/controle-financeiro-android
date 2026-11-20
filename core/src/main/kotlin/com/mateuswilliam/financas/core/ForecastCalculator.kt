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
        dataset.rules.groupBy { it.itemId }.forEach { (itemId, versions) ->
            val ordered = versions.sortedBy { it.effectiveFrom }
            val candidates = linkedMapOf<Pair<LocalDate?, String>, Pair<ForecastRule, Occurrence>>()
            val firstInstallmentSeries = mutableMapOf<String, LocalDate>()
            var installmentStart: LocalDate? = null
            ordered.forEachIndexed { index, rule ->
                if (rule.frequency != ForecastFrequency.INSTALLMENTS) installmentStart = null
                else if (installmentStart == null) installmentStart = rule.effectiveFrom
                val nextSchedule = ordered.subList(index + 1, ordered.size).firstOrNull {
                    it.frequency != rule.frequency || it.cycleAnchored != rule.cycleAnchored
                }
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
                    val series = installmentStart
                    // An old link can survive cancellation of its occurrence. Keep its ownership
                    // with the first schedule that issued that installment number.
                    val original = series == null || firstInstallmentSeries.getOrPut(occurrence.key) { series } == series
                    val adjustment = if (original) overrides[itemId to occurrence.key] else null
                    val eligibilityDate = when {
                        rule.cycleAnchored -> maxOf(period.start, rule.startDate)
                        // A one-time entry can be rescheduled before its rule took effect.
                        rule.frequency == ForecastFrequency.ONCE -> maxOf(occurrence.date, rule.effectiveFrom)
                        // The first occurrence can carry an explicit date due to a series edit.
                        rule.frequency == ForecastFrequency.MONTHLY && adjustment?.date != null &&
                            occurrence.date < rule.startDate && YearMonth.from(occurrence.date) == YearMonth.from(rule.startDate) ->
                            maxOf(occurrence.date, adjustment.date)
                        else -> occurrence.date
                    }
                    if (nextSchedule != null && (adjustment?.date ?: eligibilityDate) >= nextSchedule.effectiveFrom) return@forEach
                    val key = series to occurrence.key
                    val existing = candidates[key]
                    val started = occurrence.date >= rule.startDate || rule.cycleAnchored || adjustment != null ||
                        ((rule.frequency == ForecastFrequency.INSTALLMENTS || existing != null) &&
                            YearMonth.from(occurrence.date) >= YearMonth.from(rule.startDate))
                    if (started) {
                        if (existing == null) {
                            if (rule.effectiveFrom <= eligibilityDate) candidates[key] = rule to occurrence
                        } else {
                            val cutoffDate = adjustment?.date ?: existing.second.date
                            if (cutoffDate < rule.effectiveFrom) return@forEach
                            // A stable occurrence belongs to its previous schedule until the change boundary.
                            // Changes never move an already closed occurrence into a later cycle. If a new
                            // due day precedes the change boundary, its transition occurs at that boundary.
                            candidates[key] = rule to occurrence.copy(date = maxOf(occurrence.date, rule.effectiveFrom))
                        }
                    }
                }
            }
            // Older versions reused installment numbers within one item. The original occurrence
            // owns its stored overrides and links even when later schedules reuse that number.
            candidates.forEach { (identity, pair) ->
                val (seriesStart, key) = identity
                val (rule, occurrence) = pair
                val reusedStart = seriesStart?.takeIf { firstInstallmentSeries[key] != it }
                val override = if (reusedStart == null) overrides[itemId to key] else null
                val date = override?.date ?: occurrence.date
                if (date in period && (override?.active ?: rule.active)) {
                    val ignored = override?.ignored == true
                    lines += ForecastLine(itemId, key, override?.title ?: rule.title, date,
                        override?.amountCents ?: occurrence.amount, rule.kind, override?.category ?: rule.category,
                        rule.group, rule.frequency, rule.estimated, ignored, !ignored,
                        rule.statementKey, rule.statementTotal, rule, reusedInstallmentStart = reusedStart)
                }
            }
        }
        val statements = lines.filter { it.included && it.statementTotal && it.statementKey.isNotBlank() }
            .map { it.statementKey to it.kind }.toSet()
        val finalLines = lines.map { line ->
            if (!line.statementTotal && line.statementKey.isNotBlank() && (line.statementKey to line.kind) in statements)
                line.copy(included = false) else line
        }.filterNot { it.reusedInstallmentStart == null && (it.itemId to it.occurrenceKey) in excluded }
            .sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title }.thenBy { it.itemId })
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
