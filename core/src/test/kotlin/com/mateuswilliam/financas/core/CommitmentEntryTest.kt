package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CommitmentEntryTest {
    private val february = LocalDate.of(2027, 2, 28)
    private val month = YearMonth.of(2027, 2)

    @Test fun totalForAnOngoingPurchaseKeepsTheOriginalCentsAndBillingDay() {
        val schedule = CommitmentForms.schedule(60001, PurchaseAmountBasis.TOTAL, 6, 2, february, 31)
        assertEquals(60001L, schedule.totalCents)
        assertEquals(listOf(3, 4, 5, 6), schedule.pending.map { it.number })
        assertEquals(listOf(10000L, 10000L, 10000L, 10000L), schedule.pending.map { it.amountCents })
        assertEquals(listOf("2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31"),
            schedule.pending.map { it.dueDate.toString() })
        assertEquals(40000L, schedule.remainingCents)
    }

    @Test fun enteringAnInstallmentValueCalculatesTheOriginalTotalIncludingPaidParts() {
        val schedule = CommitmentForms.schedule(1250, PurchaseAmountBasis.INSTALLMENT, 6, 2, february, 28)
        assertEquals(7500L, schedule.totalCents)
        assertEquals(5000L, schedule.remainingCents)
        assertEquals(listOf(1250L, 1250L, 1250L, 1250L), schedule.pending.map { it.amountCents })
    }

    @Test fun incompleteAmountsAndCountsDoNotBecomeZeroOrDefaultInstallments() {
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentForms.schedule(null, PurchaseAmountBasis.TOTAL, 6, 0, february, 28)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentForms.schedule(600, PurchaseAmountBasis.TOTAL, null, 0, february, 28)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentForms.schedule(600, PurchaseAmountBasis.TOTAL, 6, null, february, 28)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentForms.schedule(600, PurchaseAmountBasis.TOTAL, 6, 6, february, 28)
        }
    }

    @Test fun previewRejectsADueDateOutsideThePersistedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentForms.schedule(600, PurchaseAmountBasis.TOTAL, 2, 0, LocalDate.of(9999, 12, 31), 31)
        }
    }

    @Test fun incomeFormStoresTheExactLimitInBasisPoints() {
        val plan = CommitmentForms.monthlyPlan(month, 325019L, "25,75")
        assertEquals(MonthlyIncomePlan(month, 325019L, 2575), plan)
        assertEquals("25,75", CommitmentForms.formatPercent(plan.healthyLimitBasisPoints))
        assertEquals("0,05", CommitmentForms.formatPercent(5))
        assertEquals("30", CommitmentForms.formatPercent(3000))
    }

    @Test fun zeroIncomeIsExplicitAndInvalidLimitsAreRejected() {
        assertEquals(MonthlyIncomePlan(month, 0, 10000), CommitmentForms.monthlyPlan(month, 0, "100.00"))
        listOf("100,01", "-1", "1,001", "25,", "5e1", "").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                CommitmentForms.monthlyPlan(month, 100, invalid)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { CommitmentForms.monthlyPlan(month, null, "30") }
    }
}
