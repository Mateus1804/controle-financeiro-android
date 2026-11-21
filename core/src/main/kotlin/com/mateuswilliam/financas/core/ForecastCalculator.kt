package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import java.util.TreeSet

object ForecastCalculator {
    private data class Occurrence(
        val key: String,
        val date: LocalDate,
        val amount: Long,
        val reusedInstallmentStart: LocalDate? = null,
    )
    private fun monthDate(month: YearMonth, day: Int) = month.atDay(minOf(day, month.lengthOfMonth()))

    private data class RuleSchedule(
        val rule: ForecastRule,
        val nextSchedule: LocalDate?,
        val installmentStart: LocalDate?,
        val installments: Installments.Terms?,
        val revisesInstallments: Boolean,
    )

    private fun Installments.Terms.numbersIn(first: YearMonth, last: YearMonth): IntRange {
        val firstOffset = maxOf(0L, firstMonth.until(first, ChronoUnit.MONTHS))
        val lastOffset = minOf((numbers.last - numbers.first).toLong(), firstMonth.until(last, ChronoUnit.MONTHS))
        return if (firstOffset <= lastOffset) {
            (numbers.first + firstOffset.toInt())..(numbers.first + lastOffset.toInt())
        } else IntRange.EMPTY
    }

    private class ItemSchedule(
        private val itemId: String,
        versions: List<ForecastRule>,
        private val overrides: Map<String, ForecastOverride>,
    ) {
        private val monthlyOverrides = TreeMap(overrides.keys.asSequence()
            .filter { it.startsWith("month:") }
            .mapNotNull { runCatching { YearMonth.parse(it.removePrefix("month:")) }.getOrNull() }
            .distinct()
            .withIndex()
            .groupBy { (_, month) -> overrides["month:$month"]?.date?.let(YearMonth::from) ?: month })
        private val movedInstallments = TreeMap(overrides.values.asSequence().mapNotNull { adjustment ->
            val date = adjustment.date ?: return@mapNotNull null
            val key = adjustment.occurrenceKey
            val number = key.removePrefix("installment:").toIntOrNull() ?: return@mapNotNull null
            if (number !in 1..Installments.MAX_INSTALLMENTS || key != "installment:$number") return@mapNotNull null
            date to number
        }.groupBy({ it.first }, { it.second }))
        private val rules: List<RuleSchedule>
        // Each entry gives the first owner for the newly issued numbers up to that count.
        private val firstInstallmentSeries = TreeMap<Int, LocalDate>()

        init {
            val ordered = versions.sortedBy { it.effectiveFrom }
            val nextSchedules = arrayOfNulls<LocalDate>(ordered.size)
            for (index in ordered.lastIndex - 1 downTo 0) {
                val current = ordered[index]
                val next = ordered[index + 1]
                nextSchedules[index] = if (current.frequency == next.frequency && current.cycleAnchored == next.cycleAnchored) {
                    nextSchedules[index + 1]
                } else next.effectiveFrom
            }
            val seenSeries = mutableSetOf<LocalDate>()
            var issuedCount = 0
            var installmentStart: LocalDate? = null
            rules = ordered.mapIndexed { index, rule ->
                val installments = if (rule.frequency == ForecastFrequency.INSTALLMENTS) {
                    Installments.termsFromTotal(rule.amountCents, rule.installmentCount,
                        monthDate(YearMonth.from(rule.startDate), rule.dayOfMonth), dayOfMonth = rule.dayOfMonth)
                } else null
                val series = if (installments != null) {
                    installmentStart ?: rule.effectiveFrom
                } else null
                installmentStart = series
                if (series != null && rule.installmentCount > issuedCount) {
                    issuedCount = rule.installmentCount
                    firstInstallmentSeries[issuedCount] = series
                }
                RuleSchedule(rule, nextSchedules[index], series, installments, series != null && !seenSeries.add(series))
            }
        }

        private fun installmentNumbersFor(period: BudgetPeriod, firstMonth: YearMonth, lastMonth: YearMonth): Map<LocalDate, TreeSet<Int>> {
            if (firstInstallmentSeries.isEmpty()) return emptyMap()
            val selected = mutableMapOf<LocalDate, TreeSet<Int>>()
            rules.forEach { schedule ->
                val installments = schedule.installments ?: return@forEach
                val series = checkNotNull(schedule.installmentStart)
                // An existing occurrence can be clamped to the effective date of a revision.
                val numbers = if (schedule.revisesInstallments && schedule.rule.effectiveFrom in period) {
                    installments.numbers
                } else installments.numbersIn(firstMonth, lastMonth)
                if (!numbers.isEmpty()) selected.getOrPut(series, ::TreeSet).addAll(numbers)
            }
            for (numbers in movedInstallments.subMap(period.start, true, period.endExclusive, false).values) {
                for (number in numbers) {
                    val owner = firstInstallmentSeries.ceilingEntry(number)?.value ?: continue
                    selected.getOrPut(owner, ::TreeSet).add(number)
                }
            }
            return selected
        }

        fun appendLines(period: BudgetPeriod, firstMonth: YearMonth, lastMonth: YearMonth, lines: MutableList<ForecastLine>) {
            val months = buildSet {
                add(firstMonth)
                add(lastMonth)
                // Find additional months by their adjusted date and keep the input order for tied rows.
                monthlyOverrides.subMap(firstMonth, true, lastMonth, true).values.flatten()
                    .sortedBy { it.index }.forEach { add(it.value) }
            }
            val installmentNumbers = installmentNumbersFor(period, firstMonth, lastMonth)
            val candidates = linkedMapOf<Pair<LocalDate?, String>, Pair<ForecastRule, Occurrence>>()
            rules.forEach { (rule, nextSchedule, series, installmentTerms) ->
                val occurrences = when (rule.frequency) {
                    ForecastFrequency.ONCE -> listOf(Occurrence("once", rule.startDate, rule.amountCents))
                    ForecastFrequency.INSTALLMENTS -> {
                        val installments = checkNotNull(installmentTerms)
                        val start = checkNotNull(series)
                        val numbers = installmentNumbers[start] ?: return@forEach
                        // Replay each selected number through every version of its series.
                        numbers.headSet(rule.installmentCount, true).map { number ->
                            val installment = installments.installment(number)
                            val reusedStart = start.takeIf { firstInstallmentSeries.ceilingEntry(number)?.value != it }
                            Occurrence("installment:$number", installment.dueDate, installment.amountCents, reusedStart)
                        }
                    }
                    ForecastFrequency.MONTHLY -> if (rule.cycleAnchored) {
                        if (rule.startDate < period.endExclusive) listOf(Occurrence("cycle:${period.start}", period.start, rule.amountCents)) else emptyList()
                    } else {
                        months.map { Occurrence("month:$it", monthDate(it, rule.dayOfMonth), rule.amountCents) }
                    }
                }
                occurrences.forEach occurrenceLoop@{ occurrence ->
                    val adjustment = if (occurrence.reusedInstallmentStart == null) overrides[occurrence.key] else null
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
                    if (nextSchedule != null && (adjustment?.date ?: eligibilityDate) >= nextSchedule) return@occurrenceLoop
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
                            if (cutoffDate < rule.effectiveFrom) return@occurrenceLoop
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
            candidates.values.forEach { (rule, occurrence) ->
                val key = occurrence.key
                val reusedStart = occurrence.reusedInstallmentStart
                val override = if (reusedStart == null) overrides[key] else null
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
    }

    internal class Schedule(dataset: ForecastDataset) {
        private val items: List<ItemSchedule>

        init {
            val overrides = dataset.overrides.groupBy { it.itemId }
            items = dataset.rules.groupBy { it.itemId }.map { (itemId, versions) ->
                ItemSchedule(itemId, versions, overrides[itemId].orEmpty().associateBy { it.occurrenceKey })
            }
        }

        fun lines(period: BudgetPeriod, excluded: Set<Pair<String, String>> = emptySet()): List<ForecastLine> {
            val firstMonth = YearMonth.from(period.start)
            val lastMonth = YearMonth.from(period.endExclusive.minusDays(1))
            val lines = mutableListOf<ForecastLine>()
            items.forEach { it.appendLines(period, firstMonth, lastMonth, lines) }
            val statements = lines.filter { it.included && it.statementTotal && it.statementKey.isNotBlank() }
                .map { it.statementKey to it.kind }.toSet()
            return lines.map { line ->
                if (!line.statementTotal && line.statementKey.isNotBlank() && (line.statementKey to line.kind) in statements)
                    line.copy(included = false) else line
            }.filterNot { it.reusedInstallmentStart == null && (it.itemId to it.occurrenceKey) in excluded }
                .sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title }.thenBy { it.itemId })
        }
    }

    fun report(dataset: ForecastDataset, period: BudgetPeriod, excluded: Set<Pair<String, String>> = emptySet()): ForecastReport {
        val finalLines = Schedule(dataset).lines(period, excluded)
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
