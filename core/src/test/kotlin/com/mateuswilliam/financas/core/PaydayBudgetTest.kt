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
    @Test fun `desconta apenas compromissos pendentes antes do pagamento e o valor guardado`() {
        val payday = today.plusDays(14)
        val lines = listOf(line("aluguel", today.plusDays(3), 100000), line("antes", today.minusDays(1), 5000),
            line("no-dia", payday, 7000), line("variavel", today.plusDays(2), 9000, group = ForecastGroup.VARIABLE),
            line("ignorada", today.plusDays(4), 8000, included = false))
        val budget = PaydayBudgets.compute(300000, lines, 50000, today, payday)
        assertEquals(100000L, budget.pendingCommitmentsCents)
        assertEquals(150000L, budget.spendableCents)
        assertEquals(14, budget.daysRemaining)
        assertEquals(75000L, budget.weeklyCents)
        assertEquals(0L, budget.deficitCents)
    }
    @Test fun `media semanal e proporcional aos dias restantes`() {
        val budget = PaydayBudgets.compute(10000, emptyList(), 0, today, today.plusDays(10))
        assertEquals(7000L, budget.weeklyCents)
    }
    @Test fun `deficit aparece quando compromissos e meta superam o saldo`() {
        val budget = PaydayBudgets.compute(50000, listOf(line("a", today, 60000)), 10000, today, today.plusDays(7))
        assertEquals(-20000L, budget.spendableCents)
        assertEquals(20000L, budget.deficitCents); assertEquals(0L, budget.totalCents); assertEquals(0L, budget.weeklyCents)
    }
    @Test fun `pagamento hoje nao tem media semanal`() {
        assertNull(PaydayBudgets.compute(1000, emptyList(), 0, today, today).weeklyCents)
    }
}
