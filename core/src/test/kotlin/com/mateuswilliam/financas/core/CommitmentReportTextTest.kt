package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitmentReportTextTest {
    private val month = YearMonth.of(2026, 11)
    private fun item(id: String, cents: Long) = FixedCommitment(id, id, cents, month.atDay(1))

    @Test fun percentageUsesBrazilianDecimalsAndDoesNotCapExcess() {
        assertEquals("33,13%", CommitmentReportText.percentage(BigDecimal("33.125")))
        assertEquals("125,5%", CommitmentReportText.percentage(BigDecimal("125.5")))
        assertEquals("0%", CommitmentReportText.percentage(BigDecimal.ZERO))
    }

    @Test fun aPositiveAmountBelowDisplayPrecisionDoesNotReadAsZero() {
        val report = CommitmentReports.month(listOf(item("conta", 1)), month, Money.MAX_CENTS)
        assertEquals("< 0,01%", CommitmentReportText.percentage(report.incomePercentage!!))
    }

    @Test fun aCentAboveIncomeDoesNotReadAsExactlyOneHundredPercent() {
        val report = CommitmentReports.month(listOf(item("conta", Money.MAX_CENTS), item("ajuste", 1)), month, Money.MAX_CENTS)
        assertEquals("100,0000000001%", CommitmentReportText.percentage(report.incomePercentage!!))
        val below = CommitmentReports.month(listOf(item("conta", 9999999)), month, 10000000)
        assertEquals("99,99999%", CommitmentReportText.percentage(below.incomePercentage!!))
    }

    @Test fun roundedPercentagePreservesWhichSideOfTheSelectedLimitItIsOn() {
        assertEquals("30,0001%", CommitmentReportText.percentage(BigDecimal("30.0001"), 3000))
        assertEquals("29,9999%", CommitmentReportText.percentage(BigDecimal("29.9999"), 3000))
        assertEquals("30%", CommitmentReportText.percentage(BigDecimal("30.0"), 3000))
        assertEquals("25,2501%", CommitmentReportText.percentage(BigDecimal("25.2501"), 2525))
    }
}
