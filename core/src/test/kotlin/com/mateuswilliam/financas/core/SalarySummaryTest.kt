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

    private fun forecast(id: String, cents: Long, category: Category, source: String? = null) =
        ForecastRule(id, day, id, if (category == Category.SALARY) ForecastKind.INCOME else ForecastKind.EXPENSE,
            cents, category, ForecastFrequency.ONCE, day, legacySource = source)

    @Test fun oneMonthSalaryOverridesRecurringAndLegacyInBothReports() {
        val next = month.plusMonths(1)
        val data = ForecastDataset(
            rules = listOf(
                forecast("previous-salary", 290000, Category.SALARY),
                SalaryForecasts.rule(month, 325000, repeat = true),
                SalaryForecasts.rule(month, 350000, repeat = false),
            ),
            monthlySalaryReferences = mapOf(month to 300000L),
        )
        val currentPeriod = BudgetPeriod(month.atDay(1), next.atDay(1))
        val nextPeriod = BudgetPeriod(next.atDay(1), next.plusMonths(1).atDay(1))
        assertEquals(350000L, ForecastCalculator.report(data, currentPeriod).incomeCents)
        assertEquals(350000L, SalarySummaries.month(emptyList(), data, month, zone, at).salaryCents)
        assertEquals(325000L, ForecastCalculator.report(data, nextPeriod).incomeCents)
        val legacy = data.copy(rules = listOf(forecast("previous-salary", 290000, Category.SALARY)))
        assertEquals(300000L, ForecastCalculator.report(legacy, currentPeriod).incomeCents)
    }

    @Test fun salaryAndRemainingExpensesUseDifferentSources() {
        val records = listOf(record("gasto", 120000, Movement.EXPENSE, Category.FOOD),
            record("pix", 900000, Movement.INCOME, Category.OTHER_INCOME))
        val data = ForecastDataset(rules = listOf(forecast("salario", 300000, Category.SALARY),
            forecast("conta", 60000, Category.HOME), forecast("importado", 120000, Category.FOOD, "record:gasto"),
            forecast("vinculado", 120000, Category.FOOD)),
            recordLinks = mapOf(("vinculado" to "once") to setOf("gasto")))
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
