package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ForecastCalculatorTest {
    private fun date(value: String) = LocalDate.parse(value)
    private val november = BudgetPeriod(date("2026-11-05"), date("2026-12-05"))
    private fun rule(id: String, amount: Long, kind: ForecastKind = ForecastKind.EXPENSE,
        frequency: ForecastFrequency = ForecastFrequency.MONTHLY, start: String = "2026-11-05") =
        ForecastRule(id, date("2026-01-01"), id, kind, amount, Category.OTHER, frequency, date(start))

    @Test fun allExpensesAndSalaryOverrideUseOneSource() {
        val data = ForecastDataset(rules = listOf(rule("salary",300000,ForecastKind.INCOME),
            rule("bills",120000), rule("purchase",120000,frequency=ForecastFrequency.INSTALLMENTS).copy(installmentCount=3),
            rule("consumption",60000).copy(cycleAnchored=true,group=ForecastGroup.VARIABLE)))
        val report = ForecastCalculator.report(data,november)
        assertEquals(220000L,report.totalExpensesCents)
        assertEquals(BigDecimal("73.33"),report.expensePercentage)
        assertEquals(80000L,report.marginCents)
        val adjusted = data.copy(overrides=listOf(ForecastOverride("salary","month:2026-11",285000)))
        assertEquals(65000L,ForecastCalculator.report(adjusted,november).marginCents)
        val next = BudgetPeriod(november.endExclusive,date("2027-01-05"))
        assertEquals(300000L,ForecastCalculator.report(adjusted,next).incomeCents)
        assertEquals(0L,ForecastCalculator.report(data,BudgetPeriod(date("2027-02-05"),date("2027-03-05"))).lines.filter { it.itemId == "purchase" }.sumOf { it.amountCents })
    }

    @Test fun installmentsKeepEveryCentAndHistoricalVersionsStayClosed() {
        val original=rule("purchase",10000,frequency=ForecastFrequency.INSTALLMENTS).copy(installmentCount=3)
        val amounts=(0L..2L).map { offset ->
            val start=november.start.plusMonths(offset)
            ForecastCalculator.report(ForecastDataset(rules=listOf(original)),BudgetPeriod(start,start.plusMonths(1))).totalExpensesCents
        }
        assertEquals(listOf(3334L,3333L,3333L),amounts)
        val old=rule("salary",300000,ForecastKind.INCOME)
        val updated=old.copy(effectiveFrom=date("2026-12-05"),startDate=date("2026-12-05"),amountCents=350000)
        val data=ForecastDataset(rules=listOf(old,updated))
        assertEquals(300000L,ForecastCalculator.report(data,november).incomeCents)
        assertEquals(350000L,ForecastCalculator.report(data,BudgetPeriod(date("2026-12-05"),date("2027-01-05"))).incomeCents)
    }

    @Test fun explicitStatementDeduplicatesOnlyItsOwnComponents() {
        val data=ForecastDataset(rules=listOf(rule("total",10000).copy(statementKey="card",statementTotal=true),
            rule("component",6000).copy(statementKey="card"),rule("pix",2000)))
        val report=ForecastCalculator.report(data,november)
        assertEquals(12000L,report.totalExpensesCents)
        assertFalse(report.lines.single { it.itemId=="component" }.included)
        val ignored=data.copy(overrides=listOf(ForecastOverride("total","month:2026-11",ignored=true)))
        assertEquals(8000L,ForecastCalculator.report(ignored,november).totalExpensesCents)
    }

    @Test fun reserveIsSeparateAndDeficitsNeverWithdrawAutomatically() {
        assertNull(ForecastCalculator.reserveBalance(emptyList()))
        val events=listOf(ReserveEvent(1,ReserveEventKind.SET,200000,1,"initial"),
            ReserveEvent(2,ReserveEventKind.ADD,50000,2,"saved",november.start))
        val data=ForecastDataset(rules=listOf(rule("salary",300000,ForecastKind.INCOME),rule("expenses",220000)),reserveEvents=events)
        val report=ForecastCalculator.report(data,november)
        assertEquals(250000L,report.reserveBalanceCents)
        assertEquals(30000L,report.unallocatedCents)
        assertEquals(BigDecimal("73.33"),report.expensePercentage)
        val deficit=ForecastCalculator.report(data.copy(rules=listOf(rule("expenses",220000))),november)
        assertEquals(-220000L,deficit.marginCents)
        assertEquals(0L,deficit.unallocatedCents)
        assertEquals(250000L,deficit.reserveBalanceCents)
        assertNull(deficit.expensePercentage)
        assertNotNull(ForecastValidation.reserveEventError(ReserveEventKind.WITHDRAW,250001,250000))
        assertNotNull(ForecastValidation.reserveEventError(ReserveEventKind.ADD,30001,250000,30000))
    }

    @Test fun importedOverridePreservesOriginalOccurrenceEvenWhenTemplateAdvanced() {
        val advanced=rule("legacy",15000,start="2026-12-05")
        val data=ForecastDataset(rules=listOf(advanced),overrides=listOf(ForecastOverride("legacy","month:2026-11",12000,title="Original")))
        val line=ForecastCalculator.report(data,november).lines.single()
        assertEquals(12000L,line.amountCents)
        assertEquals("Original",line.title)
    }

    @Test fun refundsRetainNatureAndOverOneHundredPercentageIsUnclamped() {
        val refund=rule("refund",-1000,frequency=ForecastFrequency.ONCE).copy(legacySource="record:1")
        assertNull(ForecastValidation.error(refund))
        assertNotNull(ForecastValidation.error(refund.copy(legacySource=null)))
        val report=ForecastCalculator.report(ForecastDataset(rules=listOf(rule("salary",10000,ForecastKind.INCOME),rule("expenses",16000),refund)),november)
        assertEquals(BigDecimal("150.00"),report.expensePercentage)
        assertEquals(-5000L,report.marginCents)
    }

    @Test fun movedOccurrenceUsesItsOriginalStableKey() {
        val data=ForecastDataset(rules=listOf(rule("bill",10000,start="2026-10-05")),
            overrides=listOf(ForecastOverride("bill","month:2026-10",date=date("2026-11-10"))))
        assertEquals(20000L,ForecastCalculator.report(data,november).totalExpensesCents)
    }
    @Test fun dueDayChangesPreserveClosedCyclesInBothDirections() {
        val closed=BudgetPeriod(date("2026-10-05"),date("2026-11-05"))
        val old=rule("bill",10000,start="2026-10-02")
        val later=old.copy(effectiveFrom=date("2026-11-05"),dayOfMonth=10,amountCents=20000)
        val data=ForecastDataset(rules=listOf(old,later))
        assertEquals(10000L,ForecastCalculator.report(data,closed).totalExpensesCents)
        assertEquals(0L,ForecastCalculator.report(data,november).totalExpensesCents)
        val next=BudgetPeriod(date("2026-12-05"),date("2027-01-05"))
        assertEquals(20000L,ForecastCalculator.report(data,next).totalExpensesCents)
        val reverseOld=old.copy(startDate=date("2026-10-10"),dayOfMonth=10)
        val earlier=reverseOld.copy(effectiveFrom=date("2026-11-05"),dayOfMonth=2,amountCents=20000)
        val reverse=ForecastDataset(rules=listOf(reverseOld,earlier))
        assertEquals(10000L,ForecastCalculator.report(reverse,closed).totalExpensesCents)
        val transition=ForecastCalculator.report(reverse,november).lines.single { it.occurrenceKey == "month:2026-11" }
        assertEquals("month:2026-11",transition.occurrenceKey)
        assertEquals(date("2026-11-05"),transition.date)
        assertEquals(20000L,transition.amountCents)
    }

    @Test fun dueDayChangeClampsShortMonthsAndInstallmentsWithoutChangingNumbering() {
        val old=rule("purchase",10000,frequency=ForecastFrequency.INSTALLMENTS,start="2027-01-31")
            .copy(installmentCount=3)
        val changed=old.copy(effectiveFrom=date("2027-02-05"),dayOfMonth=2)
        assertNull(ForecastValidation.error(changed))
        val data=ForecastDataset(rules=listOf(old,changed))
        val january=ForecastCalculator.report(data,BudgetPeriod(date("2027-01-01"),date("2027-02-01"))).lines.single()
        assertEquals(date("2027-01-31"),january.date)
        assertEquals(3334L,january.amountCents)
        val february=ForecastCalculator.report(data,BudgetPeriod(date("2027-02-01"),date("2027-03-01"))).lines.single()
        assertEquals(date("2027-02-05"),february.date)
        assertEquals("installment:2",february.occurrenceKey)
        assertEquals(3333L,february.amountCents)
        val monthly=rule("monthly",10000,start="2027-01-29")
        val monthlyChanged=monthly.copy(effectiveFrom=date("2027-02-05"),dayOfMonth=31)
        assertEquals(date("2027-02-28"),ForecastCalculator.report(ForecastDataset(rules=listOf(monthly,monthlyChanged)),
            BudgetPeriod(date("2027-02-01"),date("2027-03-01"))).lines.single().date)
    }

    @Test fun legacyIdentifiersAndTitlesArePreservedWithinImportBounds() {
        val legacy=rule("legacy:"+"x".repeat(256),100).copy(title="x".repeat(256),legacySource="commitment:original")
        assertNull(ForecastValidation.error(legacy))
        assertNotNull(ForecastValidation.error(legacy.copy(legacySource=null)))
        assertNotNull(ForecastValidation.error(legacy.copy(title="x".repeat(257))))
    }

    @Test fun acceptedStatementAndLegacyLinksFitBackupTextBounds() {
        val valid=rule("bill",100).copy(statementKey="s".repeat(200),statementTotal=true,
            legacySource="l".repeat(512))
        assertNull(ForecastValidation.error(valid))
        assertNotNull(ForecastValidation.error(valid.copy(statementKey="s".repeat(201))))
        assertNotNull(ForecastValidation.error(valid.copy(statementKey="bad\u0000link")))
        assertNotNull(ForecastValidation.error(valid.copy(legacySource="l".repeat(513))))
        assertNotNull(ForecastValidation.error(valid.copy(legacySource="bad\u0000source")))
    }

}
