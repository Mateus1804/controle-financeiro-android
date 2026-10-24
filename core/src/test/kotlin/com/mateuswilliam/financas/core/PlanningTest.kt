package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PlanningTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val month = YearMonth.of(2026, 10)
    private fun record(movement: Movement, cents: Long, category: Category = Category.FOOD) = Record("$movement", cents,
        movement, PaymentMethod.PIX, category, "Conta fictícia", "Exemplo", LocalDate.of(2026, 10, 10).atStartOfDay(zone).toInstant().toEpochMilli())
    @Test fun budgetUsesConfirmedNetExpensesAndIgnoresIncomeAndTransfers() {
        val records = listOf(record(Movement.EXPENSE, 8000), record(Movement.REFUND, 1000), record(Movement.INCOME, 50000), record(Movement.TRANSFER, 9000))
        val budget = MonthlyBudget("2026-10", Category.FOOD, 6000)
        val usage = Budgets.usage(records, listOf(budget), month, zone).single()
        assertEquals(7000L, usage.spentCents)
        assertEquals(-1000L, usage.remainingCents)
    }
    @Test fun refundsDoNotCreateNegativeProgressAndEmptyMonthsRemainZero() {
        val budget = MonthlyBudget("2026-10", Category.FOOD, 6000)
        assertEquals(0L, Budgets.usage(listOf(record(Movement.REFUND, 10000)), listOf(budget), month, zone).single().spentCents)
        assertEquals(0L, Budgets.usage(emptyList(), listOf(budget), month, zone).single().spentCents)
        assertTrue(Budgets.usage(emptyList(), listOf(budget), month.plusMonths(1), zone).isEmpty())
    }
    @Test fun monthUsesSelectedTimeZone() {
        val boundary = record(Movement.EXPENSE, 100).copy(occurredAt = java.time.Instant.parse("2026-11-01T01:00:00Z").toEpochMilli())
        assertEquals(100L, Budgets.usage(listOf(boundary), listOf(MonthlyBudget("2026-10", Category.FOOD, 200)), month, zone).single().spentCents)
    }
    @Test fun invalidLimitsAndMonthsAreRejected() {
        assertNotNull(Budgets.error(MonthlyBudget("2026-10", Category.FOOD, 0)))
        assertNotNull(Budgets.error(MonthlyBudget("outubro", Category.FOOD, 1)))
        assertNotNull(Budgets.error(MonthlyBudget("2026-10", Category.FOOD, Money.MAX_CENTS + 1)))
    }
    @Test fun monthEndAndLeapYearKeepTheOriginalDay() {
        assertEquals("2027-02-28", Recurrences.followingDate("2027-01-31", 31))
        assertEquals("2027-03-31", Recurrences.followingDate("2027-02-28", 31))
        assertEquals("2028-02-29", Recurrences.followingDate("2028-01-31", 31))
        assertEquals("2027-01-15", Recurrences.followingDate("2026-12-15", 15))
    }
    @Test fun forecastIsNotPartOfLedgerAndUnsupportedMovementIsRejected() {
        val plan = RecurringPlan("previsao", "Aluguel fictício", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME, "Conta fictícia", "2026-10-31", 31)
        assertNull(Recurrences.error(plan))
        assertNotNull(Recurrences.error(plan.copy(movement = Movement.TRANSFER)))
        assertNotNull(Recurrences.error(plan.copy(nextDate = "2026-02-30")))
        assertEquals(0L, Ledger.month(emptyList(), month, zone).expenses)
        assertEquals(60000L, Ledger.month(listOf(plan.proposedRecord("confirmado", zone)), month, zone).expenses)
    }
}
