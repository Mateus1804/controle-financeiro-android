package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class RecurringMonthsTest {
    private val plan = RecurringPlan("rent", "Aluguel", 60000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Carteira", "2026-12-31", 31)

    @Test fun advancingTheLegacyCursorDoesNotRemoveAnEarlierPendingCompetence() {
        val item = CommitmentPlanning.forecast(plan, YearMonth.of(2026, 11), YearMonth.of(2026, 11))
        assertNotNull("November remains a pending obligation after the cursor advances", item)
        assertEquals(LocalDate.of(2026, 11, 30), item!!.dueDate)
        assertEquals(60000L, item.amountCents)
    }

    @Test fun originalDaySurvivesShortMonthsAndTheYearBoundary() {
        val cases = listOf(
            Triple("2027-01-28", 28, "2027-02-28"),
            Triple("2027-01-29", 29, "2027-02-28"),
            Triple("2027-01-30", 30, "2027-02-28"),
            Triple("2028-01-31", 31, "2028-02-29"),
            Triple("2028-02-29", 31, "2028-03-31"),
            Triple("2026-12-31", 31, "2027-01-31"),
        )
        cases.forEach { (date, day, expected) -> assertEquals(expected, Recurrences.followingDate(date, day)) }
    }
}
