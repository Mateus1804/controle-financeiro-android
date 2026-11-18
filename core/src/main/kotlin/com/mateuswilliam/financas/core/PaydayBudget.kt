package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Quanto ainda pode ser gasto até o próximo pagamento do salário.
 * É uma referência de planejamento: não altera saldos, lançamentos nem previsões.
 */
data class PaydayBudget(
    val payday: LocalDate, val daysRemaining: Int, val availableCents: Long,
    val pendingCommitmentsCents: Long, val variableForecastCents: Long = 0,
) {
    val spendableCents: Long get() = availableCents - pendingCommitmentsCents
    val withVariablesCents: Long get() = spendableCents - variableForecastCents
    val deficitCents: Long get() = maxOf(0L, -spendableCents)
    val totalCents: Long get() = maxOf(0L, spendableCents)

    /** Média por semana proporcional aos dias restantes; nula quando o pagamento é hoje. */
    val weeklyCents: Long? get() = if (daysRemaining <= 0) null else
        BigDecimal.valueOf(totalCents).multiply(BigDecimal.valueOf(7))
            .divide(BigDecimal.valueOf(daysRemaining.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
}

object PaydayBudgets {
    /** Próximo pagamento de salário previsto a partir de [today]; datas de [extraDates] vêm de recorrências. */
    fun nextPayday(lines: List<ForecastLine>, extraDates: List<LocalDate>, today: LocalDate): LocalDate? =
        (lines.filter { it.included && !it.ignored && it.kind == ForecastKind.INCOME && it.category == Category.SALARY }.map { it.date } + extraDates)
            .filter { it >= today }.minOrNull()

    /** Linhas previstas de [today] até [horizonDays] dias à frente, atravessando ciclos; exclui o que já foi lançado. */
    fun upcomingLines(dataset: ForecastDataset, excluded: Set<Pair<String, String>>, today: LocalDate, horizonDays: Long = 62): List<ForecastLine> {
        val end = today.plusDays(horizonDays)
        val lines = mutableListOf<ForecastLine>()
        var cycle = CycleCalendar.periodContaining(today, dataset.cycleRules)
        while (cycle.start < end) {
            lines += ForecastCalculator.report(dataset, cycle, excluded).lines.filter { it.date >= today && it.date < end }
            cycle = CycleCalendar.periodContaining(cycle.endExclusive, dataset.cycleRules)
        }
        return lines.distinctBy { it.itemId to it.occurrenceKey }
    }

    /** Scheduled entries replace linked forecasts immediately; received salary never remains a future payday. */
    fun upcomingLines(dataset: ForecastDataset, records: List<Record>, zone: ZoneId, asOf: Long, horizonDays: Long = 62): List<ForecastLine> {
        require(horizonDays > 0)
        val today = Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate()
        val end = today.plusDays(horizonDays)
        val represented = records.filter { it.forecastItemId != null && it.forecastOccurrenceKey != null }
            .mapTo(hashSetOf()) { it.forecastItemId!! to it.forecastOccurrenceKey!! }
        val forecast = upcomingLines(dataset.copy(reserveEvents = emptyList()), represented, today, horizonDays)
        val scheduled = records.filter { it.occurredAt > asOf && it.movement in setOf(Movement.INCOME, Movement.EXPENSE) }
            .mapNotNull { record ->
                val date = Instant.ofEpochMilli(record.occurredAt).atZone(zone).toLocalDate()
                if (date >= end) return@mapNotNull null
                val kind = if (record.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE
                val linked = dataset.rules.filter { it.itemId == record.forecastItemId && it.effectiveFrom <= date }
                    .maxByOrNull { it.effectiveFrom }
                val id = record.forecastItemId ?: "record:${record.id}"
                val key = record.forecastOccurrenceKey ?: "record:${record.id}"
                val rule = linked ?: ForecastRule(id, date, record.description, kind, record.amountCents,
                    record.category, ForecastFrequency.ONCE, date, group = ForecastGroup.VARIABLE, account = record.bank)
                ForecastLine(id, key, record.description, date, record.amountCents, kind, record.category,
                    rule.group, rule.frequency, false, false, true, rule.statementKey, rule.statementTotal, rule)
            }
        return (forecast + scheduled).sortedWith(compareBy<ForecastLine> { it.date }.thenBy { it.title })
    }

    /** Compromissos de despesa ainda pendentes em [today, payday). */
    fun pendingCommitments(lines: List<ForecastLine>, today: LocalDate, payday: LocalDate): Long =
        pending(lines, today, payday, ForecastGroup.COMMITMENT)

    private fun pending(lines: List<ForecastLine>, today: LocalDate, payday: LocalDate, group: ForecastGroup): Long =
        lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && it.group == group && it.date >= today && it.date < payday }
            .distinctBy { it.itemId to it.occurrenceKey }.sumOf { it.amountCents }

    fun compute(availableCents: Long, lines: List<ForecastLine>, today: LocalDate, payday: LocalDate): PaydayBudget {
        require(payday >= today) { "O pagamento precisa ser hoje ou depois." }
        return PaydayBudget(payday, ChronoUnit.DAYS.between(today, payday).toInt(), availableCents,
            pendingCommitments(lines, today, payday), pending(lines, today, payday, ForecastGroup.VARIABLE))
    }
}
