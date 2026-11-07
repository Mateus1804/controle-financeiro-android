package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CommitmentReportsTest {
    private val month = YearMonth.of(2026, 11)
    private fun item(id: String, cents: Long, label: String = id, due: LocalDate = month.atDay(10)) =
        FixedCommitment(id, label, cents, due)
    private fun percent(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(expected, 0, BigDecimal(expected).compareTo(actual!!))
    }

    @Test fun selectedMonthIncludesOnlyItsCommitmentsAcrossTheYearBoundary() {
        val items = listOf(item("novembro", 1000), item("dezembro", 2000, due = LocalDate.of(2026, 12, 31)),
            item("janeiro", 3000, due = LocalDate.of(2027, 1, 1)))
        val report = CommitmentReports.month(items, YearMonth.of(2026, 12), 10000)
        assertEquals(2000L, report.committedCents)
        assertEquals(listOf("dezembro"), report.entries.map { it.id })
        assertEquals(8000L, report.freeCents)
    }

    @Test fun percentagesUseExpectedIncomeAndTheDefaultLimitProducesAttention() {
        val report = CommitmentReports.month(listOf(item("aluguel", 20000), item("compra", 5000)), month, 100000)
        percent("25", report.incomePercentage)
        assertEquals(25000L, report.committedCents)
        assertEquals(75000L, report.freeCents)
        assertEquals(30000L, report.limitCents)
        assertEquals(CommitmentState.ATTENTION, report.state)
        percent("20", report.entries.first().incomePercentage)
        percent("75", report.slices.last().incomePercentage)
    }

    @Test fun changingTheChosenLimitChangesStateWithoutChangingFinancialTotals() {
        val items = listOf(item("compra", 2500))
        val low = CommitmentReports.month(items, month, 10000, healthyLimitBasisPoints = 2000)
        val high = CommitmentReports.month(items, month, 10000, healthyLimitBasisPoints = 4000)
        assertEquals(CommitmentState.ABOVE_LIMIT, low.state)
        assertEquals(CommitmentState.COMFORTABLE, high.state)
        assertEquals(2500L, low.committedCents)
        assertEquals(2500L, high.committedCents)
        percent("25", high.incomePercentage)
    }

    @Test fun attentionStartsAtEightyPercentOfTheChosenLimitAndIncludesItsBoundary() {
        fun report(cents: Long) = CommitmentReports.month(listOf(item("a", cents)), month, 10000,
            healthyLimitBasisPoints = 3000)
        assertEquals(CommitmentState.COMFORTABLE, report(2399).state)
        assertEquals(CommitmentState.ATTENTION, report(2400).state)
        assertEquals(CommitmentState.ATTENTION, report(3000).state)
        assertEquals(CommitmentState.ABOVE_LIMIT, report(3001).state)
    }

    @Test fun stateComparisonDoesNotRoundAwayACentNearTheLimit() {
        val report = CommitmentReports.month(listOf(item("a", 300_000_000_001L)), month, 1_000_000_000_000L)
        assertEquals(CommitmentState.ABOVE_LIMIT, report.state)
        percent("30.0000000001", report.incomePercentage)
    }

    @Test fun attentionComparisonDoesNotRoundUpACentBelowItsBoundary() {
        val below = CommitmentReports.month(listOf(item("a", 239_999_999_999L)), month, 1_000_000_000_000L)
        val boundary = CommitmentReports.month(listOf(item("a", 240_000_000_000L)), month, 1_000_000_000_000L)
        assertEquals(CommitmentState.COMFORTABLE, below.state)
        assertEquals(CommitmentState.ATTENTION, boundary.state)
    }

    @Test fun zeroIncomeKeepsAmountsButDoesNotInventPercentagesOrAngles() {
        val report = CommitmentReports.month(listOf(item("aluguel", 90000)), month, 0)
        assertEquals(90000L, report.committedCents)
        assertEquals(0L, report.freeCents)
        assertEquals(CommitmentState.NO_INCOME, report.state)
        assertNull(report.incomePercentage)
        assertNull(report.entries.single().incomePercentage)
        assertNull(report.limitMarkerMicroDegrees)
        assertTrue(report.slices.isEmpty())
    }

    @Test fun zeroIncomeAndNoExpensesRemainAnUnconfiguredReport() {
        val report = CommitmentReports.month(emptyList(), month, 0)
        assertEquals(CommitmentState.NO_INCOME, report.state)
        assertEquals(0L, report.committedCents)
        assertTrue(report.entries.isEmpty())
        assertTrue(report.slices.isEmpty())
    }

    @Test fun positiveIncomeWithoutExpensesCreatesOneFullFreeSlice() {
        val report = CommitmentReports.month(emptyList(), month, 10000)
        assertEquals(CommitmentState.COMFORTABLE, report.state)
        percent("0", report.incomePercentage)
        assertNull(report.largestExpense)
        val slice = report.slices.single()
        assertEquals(CommitmentSliceKind.FREE, slice.kind)
        assertEquals("Livre", slice.label)
        assertEquals(10000L, slice.amountCents)
        percent("100", slice.incomePercentage)
        assertEquals(-90_000_000, slice.startMicroDegrees)
        assertEquals(360_000_000, slice.sweepMicroDegrees)
    }

    @Test fun expensesAboveIncomeFillTheRingWithoutHidingTheTrueIncomePercentages() {
        val report = CommitmentReports.month(listOf(item("a", 10000), item("b", 5000)), month, 10000)
        assertEquals(15000L, report.committedCents)
        assertEquals(0L, report.freeCents)
        assertTrue(report.exceedsIncome)
        assertEquals(CommitmentState.ABOVE_LIMIT, report.state)
        percent("150", report.incomePercentage)
        assertFalse(report.slices.any { it.kind == CommitmentSliceKind.FREE })
        assertEquals(listOf(240_000_000, 120_000_000), report.slices.map { it.sweepMicroDegrees })
        percent("100", report.slices[0].incomePercentage)
        percent("50", report.slices[1].incomePercentage)
        assertEquals(-18_000_000, report.limitMarkerMicroDegrees)
    }

    @Test fun incomeExactlyCommittedHasNoFreeSliceAndDoesNotExceedIncome() {
        val report = CommitmentReports.month(listOf(item("a", 10000)), month, 10000,
            healthyLimitBasisPoints = 10000)
        assertFalse(report.exceedsIncome)
        assertEquals(CommitmentState.ATTENTION, report.state)
        percent("100", report.incomePercentage)
        assertEquals(CommitmentSliceKind.EXPENSE, report.slices.single().kind)
        assertEquals(360_000_000, report.slices.single().sweepMicroDegrees)
    }

    @Test fun oneCentAboveIncomeIsNotRoundedBackToOneHundredPercent() {
        val report = CommitmentReports.month(listOf(item("a", 1_000_000_000_000L), item("b", 1)),
            month, 1_000_000_000_000L, healthyLimitBasisPoints = 10000)
        assertTrue(report.exceedsIncome)
        assertEquals(CommitmentState.ABOVE_LIMIT, report.state)
        percent("100.0000000001", report.incomePercentage)
        assertFalse(report.slices.any { it.kind == CommitmentSliceKind.FREE })
    }

    @Test fun onlyIndividualItemsStrictlyBelowThreePercentAreGrouped() {
        val report = CommitmentReports.month(listOf(item("maior", 1000), item("limiar", 300),
            item("pequeno", 299), item("centavo", 1)), month, 10000)
        assertEquals(listOf("expense:maior", "expense:limiar", "other", "free"), report.slices.map { it.key })
        val other = report.slices.single { it.kind == CommitmentSliceKind.OTHER }
        assertEquals("Outros", other.label)
        assertEquals(300L, other.amountCents)
        assertEquals(listOf("pequeno", "centavo"), other.entryIds)
        percent("3", other.incomePercentage)
        assertEquals(1600L, report.committedCents)
        assertEquals(8400L, report.freeCents)
    }

    @Test fun groupingUsesUnroundedValuesAtTheThreePercentBoundary() {
        val report = CommitmentReports.month(listOf(item("abaixo", 29_999_999_999L), item("igual", 30_000_000_000L)),
            month, 1_000_000_000_000L)
        assertEquals(listOf("igual"), report.slices.single { it.kind == CommitmentSliceKind.EXPENSE }.entryIds)
        assertEquals(listOf("abaixo"), report.slices.single { it.kind == CommitmentSliceKind.OTHER }.entryIds)
    }

    @Test fun groupsAreSortedAfterAggregationButTheLargestExpenseRemainsAnIndividual() {
        val items = listOf(item("maior", 500, "Aluguel")) + (1..6).map { item("pequeno$it", 299) }
        val report = CommitmentReports.month(items, month, 10000)
        assertEquals(listOf(CommitmentSliceKind.OTHER, CommitmentSliceKind.EXPENSE, CommitmentSliceKind.FREE),
            report.slices.map { it.kind })
        assertEquals(listOf(1794L, 500L, 7706L), report.slices.map { it.amountCents })
        assertEquals("maior", report.largestExpense?.id)
        assertEquals("Aluguel", report.largestExpense?.label)
        assertEquals(7, report.entries.size)
    }

    @Test fun orderingAndAngularRemaindersAreStableRegardlessOfInputOrder() {
        val items = ('a'..'g').map { item(it.toString(), 1) }
        val report = CommitmentReports.month(items.reversed(), month, 7)
        assertEquals(items.map { "expense:${it.id}" }, report.slices.map { it.key })
        assertEquals(listOf(51_428_572, 51_428_572, 51_428_572, 51_428_571, 51_428_571, 51_428_571, 51_428_571),
            report.slices.map { it.sweepMicroDegrees })
        assertEquals(360_000_000, report.slices.sumOf { it.sweepMicroDegrees })
        assertEquals(-90_000_000, report.slices.first().startMicroDegrees)
        assertEquals(270_000_000, report.slices.last().let { it.startMicroDegrees + it.sweepMicroDegrees })
        assertEquals(report, CommitmentReports.month(items, month, 7))
    }

    @Test fun everySliceStartsWhereThePreviousOneEndsAndFreeSpaceIsIncludedInTheCircle() {
        val report = CommitmentReports.month(listOf(item("a", 1000), item("b", 301), item("c", 299)), month, 10000)
        report.slices.zipWithNext().forEach { (first, second) ->
            assertEquals(first.startMicroDegrees + first.sweepMicroDegrees, second.startMicroDegrees)
        }
        assertEquals(360_000_000, report.slices.sumOf { it.sweepMicroDegrees })
        assertEquals(10000L, report.slices.sumOf { it.amountCents })
        assertEquals(CommitmentSliceKind.FREE, report.slices.last().kind)
    }

    @Test fun fractionsSmallerThanAnAngularUnitDoNotLoseMoneyOrBreakClosure() {
        val report = CommitmentReports.month(listOf(item("centavo", 1)), month, 1_000_000_000_000L)
        assertEquals(1L, report.slices.first().amountCents)
        assertEquals(0, report.slices.first().sweepMicroDegrees)
        percent("0.0000000001", report.slices.first().incomePercentage)
        assertEquals(360_000_000, report.slices.sumOf { it.sweepMicroDegrees })
        assertEquals(1_000_000_000_000L, report.slices.sumOf { it.amountCents })
    }

    @Test fun limitMarkerUsesIncomeWhenThereIsFreeSpace() {
        val report = CommitmentReports.month(listOf(item("a", 2500)), month, 10000, healthyLimitBasisPoints = 3500)
        assertEquals(3500L, report.limitCents)
        assertEquals(36_000_000, report.limitMarkerMicroDegrees)
    }

    @Test fun zeroAndFullIncomeLimitsKeepTheMarkerAtTheCircleBoundary() {
        val zero = CommitmentReports.month(emptyList(), month, 10000, healthyLimitBasisPoints = 0)
        val full = CommitmentReports.month(emptyList(), month, 10000, healthyLimitBasisPoints = 10000)
        assertEquals(-90_000_000, zero.limitMarkerMicroDegrees)
        assertEquals(270_000_000, full.limitMarkerMicroDegrees)
        assertEquals(CommitmentState.COMFORTABLE, zero.state)
        assertEquals(CommitmentState.ABOVE_LIMIT,
            CommitmentReports.month(listOf(item("a", 1)), month, 10000, healthyLimitBasisPoints = 0).state)
    }

    @Test fun fractionalCentLimitKeepsItsTrueMarkerAndExactState() {
        val report = CommitmentReports.month(listOf(item("a", 1)), month, 1)
        assertEquals(0L, report.limitCents)
        assertEquals(18_000_000, report.limitMarkerMicroDegrees)
        assertEquals(CommitmentState.ABOVE_LIMIT, report.state)
    }

    @Test fun largeTotalsAndPercentagesDoNotOverflowLongIntermediateProducts() {
        val report = CommitmentReports.month((1..1000).map { item("a$it", 1_000_000_000_000L) }, month, 1)
        assertEquals(1_000_000_000_000_000L, report.committedCents)
        percent("100000000000000000", report.incomePercentage)
        assertEquals(1000, report.slices.size)
        assertTrue(report.slices.all { it.sweepMicroDegrees == 360000 })
        assertEquals(360_000_000, report.slices.sumOf { it.sweepMicroDegrees })
        assertEquals(CommitmentState.ABOVE_LIMIT, report.state)
    }

    @Test fun identicalNamesOrAmountsDoNotMergeDistinctCommitments() {
        val report = CommitmentReports.month(listOf(item("a", 1000, "Compra"), item("b", 1000, "Compra")), month, 10000)
        assertEquals(listOf("a", "b"), report.entries.map { it.id })
        assertEquals(2, report.slices.count { it.kind == CommitmentSliceKind.EXPENSE })
        assertEquals(2000L, report.committedCents)
    }

    @Test fun duplicateIdentityWithinTheMonthIsRejectedInsteadOfDoubleCounting() {
        assertThrows(IllegalArgumentException::class.java) {
            CommitmentReports.month(listOf(item("mesmo", 1000), item("mesmo", 2000)), month, 10000)
        }
        val report = CommitmentReports.month(listOf(item("mesmo", 1000),
            item("mesmo", 2000, due = month.plusMonths(1).atDay(10))), month, 10000)
        assertEquals(1000L, report.committedCents)
    }

    @Test fun invalidIncomeAndLimitsAreRejected() {
        for (income in listOf(-1L, 1_000_000_000_001L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { CommitmentReports.month(emptyList(), month, income) }
        }
        for (limit in listOf(-1, 10001, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                CommitmentReports.month(emptyList(), month, 10000, healthyLimitBasisPoints = limit)
            }
        }
    }

    @Test fun invalidExpenseAmountsAndEmptyIdentitiesOrLabelsAreRejected() {
        for (amount in listOf(0L, -1L, 1_000_000_000_001L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                CommitmentReports.month(listOf(item("a", amount)), month, 10000)
            }
        }
        for (invalid in listOf(item(" ", 1000), item("a", 1000, " "))) {
            assertThrows(IllegalArgumentException::class.java) { CommitmentReports.month(listOf(invalid), month, 10000) }
        }
    }

    @Test fun installmentAdapterPreservesOpenNumbersAndFeedsTheCorrectMonths() {
        val schedule = Installments.fromTotal(10000, 3, LocalDate.of(2026, 12, 31), paidInstallments = 1)
        val items = CommitmentReports.fromInstallments("compra", "Notebook", schedule)
        assertEquals(listOf("installment:compra:2", "installment:compra:3"), items.map { it.id })
        assertEquals(listOf("Notebook · 2/3", "Notebook · 3/3"), items.map { it.label })
        assertEquals(listOf(3333L, 3333L), items.map { it.amountCents })
        val december = CommitmentReports.month(items, YearMonth.of(2026, 12), 10000)
        val january = CommitmentReports.month(items, YearMonth.of(2027, 1), 10000)
        assertEquals(3333L, december.committedCents)
        assertEquals(3333L, january.committedCents)
        percent("33.33", december.incomePercentage)
        assertEquals(0L, CommitmentReports.month(items, month, 10000).committedCents)
    }

    @Test fun installmentAdapterRejectsEmptyPurchaseIdentityAndLabel() {
        val schedule = Installments.fromTotal(10000, 3, month.atDay(10))
        assertThrows(IllegalArgumentException::class.java) { CommitmentReports.fromInstallments(" ", "Compra", schedule) }
        assertThrows(IllegalArgumentException::class.java) { CommitmentReports.fromInstallments("id", " ", schedule) }
    }
}
