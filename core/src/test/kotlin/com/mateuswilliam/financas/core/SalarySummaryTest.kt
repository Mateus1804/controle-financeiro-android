package com.mateuswilliam.financas.core

import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SalarySummaryTest {
    private val month = YearMonth.of(2026, 11)
    private val zone = ZoneOffset.UTC
    private val day = month.atDay(10)
    private val at = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun record(id: String, cents: Long, movement: Movement, category: Category) =
        Record(id, cents, movement, PaymentMethod.PIX, category, "Carteira", id, at)

    private fun forecast(id: String, cents: Long, category: Category) =
        ForecastRule(id, day, id, if (category == Category.SALARY) ForecastKind.INCOME else ForecastKind.EXPENSE,
            cents, category, ForecastFrequency.ONCE, day)

    @Test fun salaryAndRemainingExpensesUseDifferentSources() {
        val records = listOf(record("gasto", 120000, Movement.EXPENSE, Category.FOOD).copy(forecastItemId = "vinculado", forecastOccurrenceKey = "once"),
            record("pix", 900000, Movement.INCOME, Category.OTHER_INCOME))
        val data = ForecastDataset(rules = listOf(forecast("salario", 300000, Category.SALARY),
            forecast("conta", 60000, Category.HOME),
            forecast("vinculado", 120000, Category.FOOD)))
        val summary = SalarySummaries.month(records, data, month, zone, at)
        assertEquals(300000L, summary.salaryCents)
        assertEquals("40.00", summary.usedPercentage.toString())
        assertEquals(180000L, summary.remainingCents)
        assertEquals(120000L, summary.projectedRemainingCents)
    }

    @Test fun missingSalaryDoesNotInventPercentageAndOverspendingIsNotCapped() {
        val expense = record("gasto", 120000, Movement.EXPENSE, Category.FOOD)
        val missing = SalarySummaries.month(listOf(expense), ForecastDataset(), month, zone, at)
        assertEquals(120000L, missing.spentCents)
        assertNull(missing.usedPercentage)
        val salary = record("salario", 100000, Movement.INCOME, Category.SALARY)
        val data = ForecastDataset(rules = listOf(forecast("referencia", 300000, Category.SALARY)))
        val summary = SalarySummaries.month(listOf(expense, salary), data, month, zone, at)
        assertEquals("120.00", summary.usedPercentage.toString())
        assertEquals(-20000L, summary.remainingCents)
    }
}
