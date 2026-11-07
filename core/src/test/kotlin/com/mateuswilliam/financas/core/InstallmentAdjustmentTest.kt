package com.mateuswilliam.financas.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstallmentAdjustmentTest {
    private val due = LocalDate.of(2027, 2, 28)

    @Test fun keepsAnExplicitCivilDateAndExactCents() {
        val adjustment = InstallmentAdjustment(12345, due)
        assertEquals(12345L, adjustment.amountCents)
        assertEquals(LocalDate.of(2027, 2, 28), adjustment.dueDate)
    }

    @Test fun cannotMoveAnInstallmentOutsideTheIndexedYearRange() {
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentAdjustment(12345, LocalDate.of(0, 12, 31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentAdjustment(12345, LocalDate.of(10000, 1, 1))
        }
    }

    @Test fun zeroNegativeAndExcessiveAdjustmentsAreRejected() {
        for (value in listOf(0L, -1L, Money.MAX_CENTS + 1)) {
            assertThrows(IllegalArgumentException::class.java) { InstallmentAdjustment(value, due) }
        }
    }
}
