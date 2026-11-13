package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Quanto ainda pode ser gasto até o próximo pagamento do salário.
 * É uma referência de planejamento: não altera saldos, lançamentos nem previsões.
 */
data class PaydayBudget(
    val payday: LocalDate, val daysRemaining: Int, val availableCents: Long,
    val pendingCommitmentsCents: Long, val savingsCents: Long,
) {
    val spendableCents: Long get() = availableCents - pendingCommitmentsCents - savingsCents
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

    /** Compromissos de despesa ainda pendentes em [today, payday). */
    fun pendingCommitments(lines: List<ForecastLine>, today: LocalDate, payday: LocalDate): Long =
        lines.filter { it.included && !it.ignored && it.kind == ForecastKind.EXPENSE && it.group == ForecastGroup.COMMITMENT && it.date >= today && it.date < payday }
            .distinctBy { it.itemId to it.occurrenceKey }.sumOf { it.amountCents }

    fun compute(availableCents: Long, lines: List<ForecastLine>, savingsCents: Long, today: LocalDate, payday: LocalDate): PaydayBudget {
        require(payday >= today) { "O pagamento precisa ser hoje ou depois." }
        return PaydayBudget(payday, ChronoUnit.DAYS.between(today, payday).toInt(), availableCents,
            pendingCommitments(lines, today, payday), savingsCents)
    }
}
