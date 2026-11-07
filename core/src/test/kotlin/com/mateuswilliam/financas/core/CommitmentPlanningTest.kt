package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CommitmentPlanningTest {
    private val month = YearMonth.of(2026, 11)
    private fun purchase() = InstallmentPurchase("compra", "Notebook fictício", "Conta fictícia", Category.EDUCATION,
        10000, 3, month.atDay(30), paidInstallments = 1, dayOfMonth = 31)
    private fun recurring() = RecurringPlan("aluguel", "Aluguel fictício", 60000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Conta fictícia", "2026-11-30", 31)

    @Test fun purchaseMetadataProducesTheOriginalRemainingSchedule() {
        val schedule = CommitmentPlanning.schedule(purchase())
        assertEquals(listOf(2, 3), schedule.pending.map { it.number })
        assertEquals(listOf(3333L, 3333L), schedule.pending.map { it.amountCents })
        assertEquals(LocalDate.of(2026, 12, 31), schedule.pending.last().dueDate)
    }
    @Test fun invalidPurchaseMetadataCannotBePersisted() {
        for (p in listOf(purchase().copy(id = " "), purchase().copy(id = "x".repeat(129)),
            purchase().copy(title = " "), purchase().copy(title = "x".repeat(201)), purchase().copy(bank = " "),
            purchase().copy(bank = "x".repeat(81)))) {
            assertThrows(IllegalArgumentException::class.java) { CommitmentPlanning.schedule(p) }
        }
    }
    @Test fun monthlyIncomeUsesTheSameLimitsAsTheCommitmentReport() {
        CommitmentPlanning.validate(MonthlyIncomePlan(month, 0, 0))
        CommitmentPlanning.validate(MonthlyIncomePlan(month, Money.MAX_CENTS, 10000))
        assertThrows(IllegalArgumentException::class.java) { CommitmentPlanning.validate(MonthlyIncomePlan(month, -1)) }
        assertThrows(IllegalArgumentException::class.java) { CommitmentPlanning.validate(MonthlyIncomePlan(month, 10000, 10001)) }
    }
    @Test fun recurringProjectionRespectsItsStartAndTheOriginalDueDay() {
        val projected = CommitmentPlanning.forecast(recurring(), month, YearMonth.of(2027, 2))!!
        assertEquals(LocalDate.of(2027, 2, 28), projected.dueDate)
        assertEquals("recurring:aluguel:2027-02-28", projected.id)
        assertEquals(60000L, projected.amountCents)
        assertNull(CommitmentPlanning.forecast(recurring(), month, month.minusMonths(1)))
        assertNull(CommitmentPlanning.forecast(recurring(), month.plusMonths(1), month))
    }
    @Test fun currentLegacyDateIsPreservedBeforeUsingTheConfiguredDayInFollowingMonths() {
        val plan = recurring().copy(dayOfMonth = 15)
        assertEquals(LocalDate.of(2026, 11, 30), CommitmentPlanning.forecast(plan, month, month)?.dueDate)
        assertEquals(LocalDate.of(2026, 12, 15), CommitmentPlanning.forecast(plan, month, month.plusMonths(1))?.dueDate)
    }
    @Test fun disabledAndIncomePlansDoNotBecomeFixedExpenses() {
        assertNull(CommitmentPlanning.forecast(recurring().copy(active = false), month, month))
        assertNull(CommitmentPlanning.forecast(recurring().copy(movement = Movement.INCOME), month, month))
    }
    @Test fun confirmedAmountKeepsTheObligationMonthAndIdentity() {
        val expected = CommitmentPlanning.forecast(recurring(), month, month)!!
        val record = Record("pago", 61000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Conta fictícia", "Pagamento antecipado", 1_790_000_000_000L)
        val actual = CommitmentPlanning.resolve(expected, record)
        assertEquals(61000L, actual.amountCents)
        assertEquals(expected.dueDate, actual.dueDate)
        assertEquals(expected.id, actual.id)
        assertEquals(expected, CommitmentPlanning.resolve(expected, null))
        assertThrows(IllegalArgumentException::class.java) { CommitmentPlanning.resolve(expected, record.copy(movement = Movement.INCOME)) }
    }
}
