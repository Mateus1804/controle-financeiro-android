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
    fun summarize(records: List<Record>, data: ForecastDataset, period: BudgetPeriod, zone: ZoneId, asOf: Long): CentralFinancialSummary =
        Inputs(records, data, zone, asOf).summarize(period)

    fun summarizePeriods(
        records: List<Record>, data: ForecastDataset, periods: List<BudgetPeriod>, zone: ZoneId, asOf: Long,
    ): List<CentralFinancialSummary> = periods.map(Inputs(records, data, zone, asOf)::summarize)

    private class Inputs(records: List<Record>, data: ForecastDataset, private val zone: ZoneId, asOf: Long) {
        private val pending = PendingForecasts(data, records)
        private val rulesByItem = data.rules.groupBy { it.itemId }
        private val recordsById = records.associateBy { it.id }
        private val recordedAndScheduled = records.partition { it.occurredAt <= asOf }

        private fun group(record: Record): ForecastGroup {
            val original = record.originalRecordId?.let(recordsById::get)
            val linked = original ?: record
            val rules = rulesByItem[linked.forecastItemId] ?: return ForecastGroup.VARIABLE
            val date = Instant.ofEpochMilli(linked.occurredAt).atZone(zone).toLocalDate()
            return rules.filter { it.effectiveFrom <= date }.maxByOrNull { it.effectiveFrom }?.group
                ?: ForecastGroup.VARIABLE
        }

        fun summarize(period: BudgetPeriod): CentralFinancialSummary {
            val recorded = Ledger.period(recordedAndScheduled.first, period, zone)
            val scheduled = Ledger.period(recordedAndScheduled.second, period, zone)
            val lines = pending.lines(period)
            val forecast = report(period, lines, 0, 0, 0, 0)
            val all = recorded.records + scheduled.records
            val commitments = all.filter { group(it) == ForecastGroup.COMMITMENT }.sumOf { it.expenseContribution() }
            val expenses = all.sumOf { it.expenseContribution() }
            val transfers = recorded.records.filter { it.movement == Movement.TRANSFER }
            val incoming = transfers.filter { AccountFunds.isReserve(it.destination) && !AccountFunds.isReserve(it.bank) }.sumOf { it.amountCents }
            val outgoing = transfers.filter { AccountFunds.isReserve(it.bank) && !AccountFunds.isReserve(it.destination) }.sumOf { it.amountCents }
            val projected = report(period, lines, all.sumOf { it.incomeContribution() }, commitments, expenses - commitments, maxOf(0L, incoming - outgoing))
            return CentralFinancialSummary(recorded, scheduled, forecast, projected, incoming, outgoing)
        }
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
