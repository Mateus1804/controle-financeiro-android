package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ForecastLinkIntegrityTest {
    private val start=LocalDate.parse("2026-11-15")
    private fun expense(id: String, cents: Long)=ForecastRule(id,start,id,ForecastKind.EXPENSE,cents,Category.OTHER,ForecastFrequency.MONTHLY,start)
    @Test fun representedStatementStillCoversItsComponents() {
        val total=expense("total",100000).copy(statementKey="fatura",statementTotal=true)
        val component=expense("componente",30000).copy(statementKey="fatura")
        val report=ForecastCalculator.report(ForecastDataset(rules=listOf(total,component)),BudgetPeriod(start,start.plusMonths(1)),setOf("total" to "month:2026-11"))
        assertEquals(0L,report.totalExpensesCents)
        assertFalse(report.lines.single { it.itemId == "componente" }.included)
    }
    @Test fun monthlyListPreservesConfiguredCycleKeysAndSkipAffectsPlanner() {
        val rule=expense("consumo",30000).copy(cycleAnchored=true)
        val data=ForecastDataset(rules=listOf(rule),cycleRules=listOf(CycleRule(LocalDate.parse("2026-01-15"),15)))
        val month = YearMonth.of(2026, 11)
        val period = BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))
        val line = PendingForecasts(data, emptyList()).lines(period).single()
        assertEquals("cycle:2026-11-15",line.occurrenceKey)
        val edited=data.copy(overrides=listOf(ForecastOverride(line.itemId,line.occurrenceKey,ignored=true)))
        assertEquals(0L,ForecastCalculator.report(edited,CycleCalendar.periodContaining(line.date,data.cycleRules)).totalExpensesCents)
        assertTrue(PendingForecasts(edited, emptyList()).lines(period).single().ignored)
    }
}
