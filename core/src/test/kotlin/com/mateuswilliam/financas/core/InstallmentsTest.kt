package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InstallmentsTest {
    private val november = LocalDate.of(2026, 11, 10)

    @Test fun exactDivisionKeepsEveryCentAndNumbersAllInstallments() {
        val schedule = Installments.fromTotal(10000, 4, november)
        assertEquals(10000L, schedule.totalCents)
        assertEquals(4, schedule.installmentCount)
        assertEquals(0, schedule.paidInstallments)
        assertEquals(listOf(1, 2, 3, 4), schedule.pending.map { it.number })
        assertEquals(listOf(2500L, 2500L, 2500L, 2500L), schedule.pending.map { it.amountCents })
        assertEquals(10000L, schedule.remainingCents)
    }

    @Test fun divisionAssignsTheRemainderToTheFirstOriginalInstallments() {
        val schedule = Installments.fromTotal(10000, 3, november)
        assertEquals(listOf(3334L, 3333L, 3333L), schedule.pending.map { it.amountCents })
        assertEquals(10000L, schedule.remainingCents)
        assertEquals(listOf(3334L, 3334L, 3333L),
            Installments.fromTotal(10001, 3, november).pending.map { it.amountCents })
    }

    @Test fun decemberContinuesIntoTheFollowingYear() {
        val schedule = Installments.fromTotal(9000, 3, LocalDate.of(2026, 12, 15))
        assertEquals(listOf(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15),
            LocalDate.of(2027, 2, 15)), schedule.pending.map { it.dueDate })
    }

    @Test fun shortMonthsDoNotPermanentlyMoveTheOriginalDueDay() {
        val schedule = Installments.fromTotal(9000, 3, LocalDate.of(2027, 1, 31))
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28),
            LocalDate.of(2027, 3, 31)), schedule.pending.map { it.dueDate })
    }

    @Test fun leapYearIncludesFebruary29AndReturnsToDay31() {
        val schedule = Installments.fromTotal(9000, 3, LocalDate.of(2028, 1, 31))
        assertEquals(listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29),
            LocalDate.of(2028, 3, 31)), schedule.pending.map { it.dueDate })
    }

    @Test fun ongoingPurchaseStartsAtTheNextUnpaidNumberAndMonth() {
        val schedule = Installments.fromTotal(100000, 10, november, paidInstallments = 4)
        assertEquals(4, schedule.paidInstallments)
        assertEquals(listOf(5, 6, 7, 8, 9, 10), schedule.pending.map { it.number })
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10),
            LocalDate.of(2027, 1, 10), LocalDate.of(2027, 2, 10), LocalDate.of(2027, 3, 10),
            LocalDate.of(2027, 4, 10)), schedule.pending.map { it.dueDate })
        assertEquals(60000L, schedule.remainingCents)
        assertEquals(100000L, schedule.totalCents)
    }

    @Test fun paidInstallmentsDoNotRedistributeTheirRemainderToOpenInstallments() {
        val schedule = Installments.fromTotal(10000, 3, november, paidInstallments = 1)
        assertEquals(listOf(2, 3), schedule.pending.map { it.number })
        assertEquals(listOf(3333L, 3333L), schedule.pending.map { it.amountCents })
        assertEquals(6666L, schedule.remainingCents)
        assertEquals(10000L, schedule.totalCents)
    }

    @Test fun remainderOnTheFirstOpenInstallmentIsRetained() {
        val schedule = Installments.fromTotal(10001, 3, november, paidInstallments = 1)
        assertEquals(listOf(3334L, 3333L), schedule.pending.map { it.amountCents })
        assertEquals(6667L, schedule.remainingCents)
    }

    @Test fun ongoingPurchaseCanPreserveDay31WhenTheNextDateIsInFebruary() {
        val schedule = Installments.fromTotal(9000, 3, LocalDate.of(2027, 2, 28),
            paidInstallments = 1, dayOfMonth = 31)
        assertEquals(31, schedule.dayOfMonth)
        assertEquals(listOf(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
            schedule.pending.map { it.dueDate })
    }

    @Test fun amountPerInstallmentCalculatesTheOriginalTotalAndRemainingAmount() {
        val schedule = Installments.fromInstallment(12999, 12, november, paidInstallments = 5)
        assertEquals(155988L, schedule.totalCents)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12), schedule.pending.map { it.number })
        assertEquals(listOf(12999L, 12999L, 12999L, 12999L, 12999L, 12999L, 12999L),
            schedule.pending.map { it.amountCents })
        assertEquals(90993L, schedule.remainingCents)
    }

    @Test fun changingTheCalculationBasisDoesNotInventFractionsOfACent() {
        val fromTotal = Installments.fromTotal(10000, 3, november)
        val fromAmount = Installments.fromInstallment(3333, 3, november)
        assertEquals(10000L, fromTotal.totalCents)
        assertEquals(9999L, fromAmount.totalCents)
        assertEquals(listOf(3333L, 3333L, 3333L), fromAmount.pending.map { it.amountCents })
    }

    @Test fun aSingleInstallmentAcceptsTheExistingMonetaryLimit() {
        val schedule = Installments.fromTotal(1_000_000_000_000L, 1, november)
        assertEquals(1_000_000_000_000L, schedule.pending.single().amountCents)
        assertEquals(1_000_000_000_000L, schedule.remainingCents)
    }

    @Test fun totalAtTheMonetaryLimitIsDividedWithoutFloatingPointLoss() {
        val schedule = Installments.fromTotal(1_000_000_000_000L, 3, november)
        assertEquals(listOf(333_333_333_334L, 333_333_333_333L, 333_333_333_333L),
            schedule.pending.map { it.amountCents })
        assertEquals(1_000_000_000_000L, schedule.remainingCents)
    }

    @Test fun minimumPositiveInstallmentsAndMaximumScheduleRemainBounded() {
        val schedule = Installments.fromTotal(600, 600, LocalDate.of(2026, 1, 15))
        assertEquals(600, schedule.pending.size)
        assertTrue(schedule.pending.all { it.amountCents == 1L })
        assertEquals(LocalDate.of(2075, 12, 15), schedule.pending.last().dueDate)
    }

    @Test fun amountsCannotCreateZeroOrNegativeInstallments() {
        for (amount in listOf(0L, -1L, 1_000_000_000_001L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { Installments.fromTotal(amount, 1, november) }
        }
        assertThrows(IllegalArgumentException::class.java) { Installments.fromTotal(2, 3, november) }
    }

    @Test fun invalidOrExcessiveCountsAreRejectedByBothCalculationBases() {
        for (count in listOf(-1, 0, 601, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { Installments.fromTotal(10000, count, november) }
            assertThrows(IllegalArgumentException::class.java) { Installments.fromInstallment(100, count, november) }
        }
    }

    @Test fun paidCountsMustLeaveAtLeastOneOpenInstallment() {
        for (paid in listOf(-1, 3, 4, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                Installments.fromTotal(10000, 3, november, paidInstallments = paid)
            }
            assertThrows(IllegalArgumentException::class.java) {
                Installments.fromInstallment(100, 3, november, paidInstallments = paid)
            }
        }
    }

    @Test fun installmentAmountRejectsInvalidMoneyAndProductsBeyondTheMonetaryLimit() {
        for (amount in listOf(0L, -1L, 1_000_000_000_001L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { Installments.fromInstallment(amount, 2, november) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Installments.fromInstallment(500_000_000_001L, 2, november)
        }
        assertEquals(1_000_000_000_000L,
            Installments.fromInstallment(500_000_000_000L, 2, november).totalCents)
    }

    @Test fun invalidDueDaysAndDatesThatContradictTheirDueDayAreRejected() {
        for (day in listOf(0, 32, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                Installments.fromTotal(10000, 3, november, dayOfMonth = day)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Installments.fromTotal(10000, 3, november, dayOfMonth = 15)
        }
    }

    @Test fun dateRangeOverflowIsReportedAsInvalidInputWithoutReturningAPartialPlan() {
        assertThrows(IllegalArgumentException::class.java) {
            Installments.fromTotal(10000, 2, LocalDate.MAX)
        }
    }
}
