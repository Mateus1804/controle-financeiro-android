package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ForecastReviewTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 5)
    private val period = BudgetPeriod(day.withDayOfMonth(1), day.plusMonths(1).withDayOfMonth(1))
    private val rent = ForecastRule("rent", day, "Aluguel", ForecastKind.EXPENSE, 30000,
        Category.HOME, ForecastFrequency.MONTHLY, day)
    private fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun changingFrequencyPreservesHistoryAndReplacesTheOldSchedule() {
        val start = day.plusMonths(1)
        val revised = rent.copy(effectiveFrom = period.endExclusive, startDate = start,
            frequency = ForecastFrequency.INSTALLMENTS, installmentCount = 3)
        val data = ForecastDataset(rules = listOf(rent, revised))
        assertEquals(30000L, ForecastCalculator.report(data, period).totalExpensesCents)
        for (month in 1L..3L) {
            val startOfMonth = period.start.plusMonths(month)
            val rows = ForecastCalculator.report(data, BudgetPeriod(startOfMonth, startOfMonth.plusMonths(1))).lines
            assertEquals("installment:$month", rows.single().occurrenceKey)
            assertEquals(10000L, rows.single().amountCents)
        }
        val after = period.start.plusMonths(4)
        assertTrue(ForecastCalculator.report(data, BudgetPeriod(after, after.plusMonths(1))).lines.isEmpty())
    }

    @Test fun interruptionIncludesTheMovedOccurrence() {
        val moved = day.plusDays(15)
        val data = ForecastDataset(rules = listOf(rent, rent.copy(effectiveFrom = moved, active = false)),
            overrides = listOf(ForecastOverride(rent.itemId, "month:2026-11", date = moved)))
        assertTrue(ForecastCalculator.report(data, period).lines.isEmpty())
        val future = BudgetPeriod(period.endExclusive, period.endExclusive.plusMonths(1))
        assertTrue(ForecastCalculator.report(data, future).lines.isEmpty())
    }

    @Test fun olderRuleVersionsDoNotMaskInstallmentsFromARestartedSchedule() {
        val first = rent.copy(effectiveFrom = period.start, frequency = ForecastFrequency.INSTALLMENTS, installmentCount = 3)
        val nextMonth = period.endExclusive
        val monthly = rent.copy(effectiveFrom = nextMonth, startDate = day.plusMonths(1))
        val restarted = first.copy(effectiveFrom = nextMonth.plusMonths(1), startDate = day.plusMonths(2))
        val data = ForecastDataset(rules = listOf(first, monthly, restarted),
            overrides = listOf(ForecastOverride(first.itemId, "installment:1", amountCents = 12000, date = day.plusDays(1))))
        val december = BudgetPeriod(nextMonth, nextMonth.plusMonths(1))
        val january = BudgetPeriod(december.endExclusive, december.endExclusive.plusMonths(1))
        assertEquals(12000L, ForecastCalculator.report(data, period).totalExpensesCents)
        assertEquals(30000L, ForecastCalculator.report(data, december).totalExpensesCents)
        assertEquals(10000L, ForecastCalculator.report(data, january).totalExpensesCents)
        val paid = Record("paid", 12000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Conta", "Parcela antiga", at(day), forecastItemId = first.itemId, forecastOccurrenceKey = "installment:1")
        val oldSecond = paid.copy(id = "old-second", forecastOccurrenceKey = "installment:2", occurredAt = at(day.plusMonths(1)))
        val pending = PendingForecasts(data, listOf(paid, oldSecond))
        assertTrue(pending.lines(period).isEmpty())
        assertEquals(10000L, pending.lines(january).single().amountCents)
        val february = BudgetPeriod(january.endExclusive, january.endExclusive.plusMonths(1))
        assertEquals(10000L, pending.lines(february).single().amountCents)

        val stopped = restarted.copy(effectiveFrom = restarted.startDate, active = false)
        val interrupted = data.copy(rules = data.rules + stopped)
        assertTrue(ForecastCalculator.report(interrupted, january).lines.isEmpty())
    }

    @Test fun differentScheduledRecordsReplaceTheSameForecastWithoutCollapsingIntoOneExpense() {
        val first = Record("first", 10000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Conta", "Parte A", at(day), forecastItemId = rent.itemId, forecastOccurrenceKey = "month:2026-11")
        val second = first.copy(id = "second", description = "Parte B", amountCents = 20000)
        val data = ForecastDataset(rules = listOf(rent))
        val records = listOf(first, second)
        val today = day.minusDays(1)
        val upcoming = PaydayBudgets.upcomingLines(data, records, zone, at(today))
        assertEquals(30000L, PaydayBudgets.pendingCommitments(upcoming, today, day.plusDays(1)))
        assertEquals(30000L, CentralFinance.summarize(records, data, period, zone, at(today)).projectedExpensesCents)
    }

    @Test fun seriesChangeUsesTheCycleOfAnOccurrenceWithAnAdjustedDueDate() {
        val changedCycle = BudgetPeriod(day.plusDays(10), day.plusDays(10).plusMonths(1))
        val moved = day.plusDays(15)
        val updated = rent.copy(effectiveFrom = changedCycle.start, amountCents = 50000)
        val data = ForecastDataset(rules = listOf(rent, updated),
            overrides = listOf(ForecastOverride(rent.itemId, "month:2026-11", date = moved)))
        val selected = ForecastCalculator.report(data, changedCycle).lines.single { it.occurrenceKey == "month:2026-11" }
        assertEquals(moved, selected.date)
        assertEquals(50000L, selected.amountCents)
    }

    @Test fun invoiceForecastOnlyCountsTheAmountStillPendingAfterAComponentIsRecorded() {
        val due = day.plusDays(15)
        val bill = rent.copy(itemId = "bill", amountCents = 100000, startDate = due, dayOfMonth = due.dayOfMonth,
            statementTotal = true, statementKey = "Cartão")
        val component = rent.copy(statementKey = "Cartão")
        val record = Record("bill-part", 30000, Movement.EXPENSE, PaymentMethod.CARD, Category.HOME, "Conta",
            "Parte da fatura", at(day), forecastItemId = rent.itemId, forecastOccurrenceKey = "month:2026-11")
        val data = ForecastDataset(rules = listOf(bill, component))
        val asOf = at(day.plusDays(1))
        val summary = CentralFinance.summarize(listOf(record), data, period, zone, asOf)
        assertEquals(30000L, summary.recorded.expenses)
        assertEquals(70000L, summary.forecast.totalExpensesCents)
        assertEquals(100000L, summary.projectedExpensesCents)
        assertEquals(100000L, summary.expensesByCategory.values.sum())
        val upcoming = PaydayBudgets.upcomingLines(data, listOf(record), zone, asOf)
        assertEquals(70000L, PaydayBudgets.pendingCommitments(upcoming, day.plusDays(1), due.plusDays(1)))
    }

    @Test fun projectedResultUsesAllIncomeAndOnlyCalculatesPercentageWhenThereIsIncome() {
        val expense = Record("expense", 120000, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta", "Alimentação", at(day))
        val asOf = at(day.plusDays(1))
        val noIncome = CentralFinance.summarize(listOf(expense), ForecastDataset(), period, zone, asOf)
        assertNull(noIncome.projectedReport.expensePercentage)
        assertEquals(-120000L, noIncome.projectedResultCents)
        val salary = rent.copy(itemId = "salary", kind = ForecastKind.INCOME, category = Category.SALARY)
        val extra = expense.copy(id = "extra", movement = Movement.INCOME, amountCents = 70000,
            category = Category.EXTRA_INCOME)
        val projected = CentralFinance.summarize(listOf(expense, extra), ForecastDataset(rules = listOf(salary)),
            period, zone, asOf)
        assertEquals(100000L, projected.projectedIncomeCents)
        assertEquals(-20000L, projected.projectedResultCents)
        assertEquals("120.00", projected.projectedReport.expensePercentage.toString())
    }
}
