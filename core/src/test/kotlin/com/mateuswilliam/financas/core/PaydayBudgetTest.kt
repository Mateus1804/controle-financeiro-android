package com.mateuswilliam.financas.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PaydayBudgetTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun line(id: String, date: LocalDate, cents: Long, kind: ForecastKind = ForecastKind.EXPENSE,
                     category: Category = Category.HOME, group: ForecastGroup = ForecastGroup.COMMITMENT, included: Boolean = true): ForecastLine {
        val rule = ForecastRule(id, date, id, kind, cents, category, ForecastFrequency.ONCE, date, group = group)
        return ForecastLine(id, "once", id, date, cents, kind, category, group, ForecastFrequency.ONCE, false, !included, included, "", false, rule)
    }

    @Test fun `escolhe o proximo salario previsto ignorando passado e outras receitas`() {
        val lines = listOf(line("velho", today.minusDays(1), 1, ForecastKind.INCOME, Category.SALARY),
            line("bonus", today.plusDays(2), 1, ForecastKind.INCOME, Category.OTHER),
            line("sal2", today.plusDays(20), 1, ForecastKind.INCOME, Category.SALARY),
            line("sal1", today.plusDays(10), 1, ForecastKind.INCOME, Category.SALARY))
        assertEquals(today.plusDays(10), PaydayBudgets.nextPayday(lines, emptyList(), today))
        assertEquals(today.plusDays(3), PaydayBudgets.nextPayday(lines, listOf(today.plusDays(3)), today))
        assertNull(PaydayBudgets.nextPayday(emptyList(), emptyList(), today))
    }
    @Test fun `desconta apenas compromissos e nao desconta a reserva`() {
        val payday = today.plusDays(14)
        val lines = listOf(line("aluguel", today.plusDays(3), 100000), line("antes", today.minusDays(1), 5000),
            line("no-dia", payday, 7000), line("variavel", today.plusDays(2), 9000, group = ForecastGroup.VARIABLE),
            line("ignorada", today.plusDays(4), 8000, included = false))
        val budget = PaydayBudgets.compute(300000, lines, today, payday)
        assertEquals(100000L, budget.pendingCommitmentsCents)
        assertEquals(200000L, budget.spendableCents)
        assertEquals(14, budget.daysRemaining)
        assertEquals(100000L, budget.weeklyCents)
        assertEquals(0L, budget.deficitCents)
    }
    @Test fun `media semanal e proporcional aos dias restantes`() {
        val budget = PaydayBudgets.compute(10000, emptyList(), today, today.plusDays(10))
        assertEquals(7000L, budget.weeklyCents)
    }
    @Test fun `deficit considera somente compromissos acima do saldo`() {
        val budget = PaydayBudgets.compute(50000, listOf(line("a", today, 60000)), today, today.plusDays(7))
        assertEquals(-10000L, budget.spendableCents)
        assertEquals(10000L, budget.deficitCents); assertEquals(0L, budget.totalCents); assertEquals(0L, budget.weeklyCents)
    }
    @Test fun `pagamento hoje nao tem media semanal`() {
        assertNull(PaydayBudgets.compute(1000, emptyList(), today, today).weeklyCents)
    }
    @Test fun `mil reais menos trezentos em dez dias deixa setecentos e media de quatrocentos e noventa`() {
        val budget = PaydayBudgets.compute(100000, listOf(line("conta", today.plusDays(2), 30000)), today, today.plusDays(10))
        assertEquals(70000L, budget.totalCents)
        assertEquals(49000L, budget.weeklyCents)
    }

    @Test fun `variaveis ficam em cenario separado sem duplicar ocorrencias`() {
        val variable = line("variavel", today.plusDays(2), 20000, group = ForecastGroup.VARIABLE)
        val lines = listOf(line("fixa", today.plusDays(1), 30000), variable, variable,
            line("ignorada", today.plusDays(2), 9000, group = ForecastGroup.VARIABLE, included = false))
        val budget = PaydayBudgets.compute(100000, lines, today, today.plusDays(10))
        assertEquals(70000L, budget.totalCents)
        assertEquals(20000L, budget.variableForecastCents)
        assertEquals(50000L, budget.withVariablesCents)
        assertEquals(49000L, budget.weeklyCents)
    }

}
