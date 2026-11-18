package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** Recorded cash, scheduled records and remaining forecasts are separate views of one financial period. */
data class CentralFinancialSummary(
    val recorded: MonthlySummary,
    val scheduled: MonthlySummary,
    val forecast: ForecastReport,
    val projectedReport: ForecastReport,
    val reserveTransfersInCents: Long,
    val reserveTransfersOutCents: Long,
) {
    val expensesByCategory: Map<Category, Long> get() {
        val totals = mutableMapOf<Category, Long>()
        fun add(category: Category, cents: Long) {
            if (cents != 0L) totals[category] = Math.addExact(totals[category] ?: 0L, cents)
        }
        (recorded.records + scheduled.records).forEach { add(it.category, it.expenseContribution()) }
        forecast.lines.filter { it.included && it.kind == ForecastKind.EXPENSE }.forEach { add(it.category, it.amountCents) }
        return totals.filterValues { it != 0L }
    }
    val projectedIncomeCents: Long get() = projectedReport.incomeCents
    val projectedExpensesCents: Long get() = projectedReport.totalExpensesCents
    val projectedResultCents: Long get() = projectedReport.marginCents
}

object CentralFinance {
    fun summarize(records: List<Record>, data: ForecastDataset, period: BudgetPeriod, zone: ZoneId, asOf: Long): CentralFinancialSummary {
        val recorded = Ledger.period(records.filter { it.occurredAt <= asOf }, period, zone)
        val scheduled = Ledger.period(records.filter { it.occurredAt > asOf }, period, zone)
        // Even scheduled linked records replace their occurrence in the projection, but not in actual cash.
        val represented = records.filter { it.forecastItemId != null && it.forecastOccurrenceKey != null }
            .mapTo(hashSetOf()) { it.forecastItemId!! to it.forecastOccurrenceKey!! }
        // Old reserve events lack a source account and must not create new cash or allocations.
        val clean = data.copy(reserveEvents = emptyList())
        val lines = buildList {
            var cycle = CycleCalendar.periodContaining(period.start, data.cycleRules)
            while (cycle.start < period.endExclusive) {
                addAll(ForecastCalculator.report(clean, cycle, represented).lines.filter { it.date in period })
                cycle = CycleCalendar.periodContaining(cycle.endExclusive, data.cycleRules)
            }
        }.distinctBy { it.itemId to it.occurrenceKey }.sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title })
        val forecast = report(period, lines, 0, 0, 0, 0)
        val all = recorded.records + scheduled.records
        fun group(record: Record): ForecastGroup {
            val original = record.originalRecordId?.let { id -> records.firstOrNull { it.id == id } }
            val linked = original ?: record
            val date = Instant.ofEpochMilli(linked.occurredAt).atZone(zone).toLocalDate()
            return data.rules.filter { it.itemId == linked.forecastItemId && it.effectiveFrom <= date }
                .maxByOrNull { it.effectiveFrom }?.group ?: ForecastGroup.VARIABLE
        }
        val commitments = all.filter { group(it) == ForecastGroup.COMMITMENT }.sumOf { it.expenseContribution() }
        val expenses = all.sumOf { it.expenseContribution() }
        val transfers = recorded.records.filter { it.movement == Movement.TRANSFER }
        val incoming = transfers.filter { AccountFunds.isReserve(it.destination) && !AccountFunds.isReserve(it.bank) }.sumOf { it.amountCents }
        val outgoing = transfers.filter { AccountFunds.isReserve(it.bank) && !AccountFunds.isReserve(it.destination) }.sumOf { it.amountCents }
        val projected = report(period, lines, all.sumOf { it.incomeContribution() }, commitments, expenses - commitments, maxOf(0L, incoming - outgoing))
        return CentralFinancialSummary(recorded, scheduled, forecast, projected, incoming, outgoing)
    }

    private fun report(period: BudgetPeriod, lines: List<ForecastLine>, actualIncome: Long, actualCommitments: Long, actualVariable: Long, allocated: Long): ForecastReport {
        val included = lines.filter { it.included && !it.ignored }
        val income = Math.addExact(actualIncome, included.filter { it.kind == ForecastKind.INCOME }.sumOf { it.amountCents })
        val expenses = included.filter { it.kind == ForecastKind.EXPENSE }
        val commitments = Math.addExact(actualCommitments, expenses.filter { it.group == ForecastGroup.COMMITMENT }.sumOf { it.amountCents })
        val variable = Math.addExact(actualVariable, expenses.filter { it.group == ForecastGroup.VARIABLE }.sumOf { it.amountCents })
        val total = Math.addExact(commitments, variable)
        val margin = Math.subtractExact(income, total)
        val percentage = if (income > 0) BigDecimal.valueOf(total).movePointRight(2).divide(BigDecimal.valueOf(income), 2, RoundingMode.HALF_UP) else null
        return ForecastReport(period, lines, income, commitments, variable, total, margin, percentage,
            allocated, maxOf(0L, Math.subtractExact(margin, allocated)), null)
    }
}
