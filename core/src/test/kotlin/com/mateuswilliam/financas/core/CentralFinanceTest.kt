package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CentralFinanceTest {
    private val zone = ZoneOffset.UTC
    private val month = YearMonth.of(2026, 11)
    private val period = BudgetPeriod(month.atDay(5), month.plusMonths(1).atDay(5))
    private fun time(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun row(id: String, amount: Long, movement: Movement, date: String = "2026-11-10", account: String = "Principal", destination: String = "") =
        Record(id, amount, movement, PaymentMethod.PIX, if (movement == Movement.INCOME) Category.SALARY else Category.HOME,
            account, id, time(date), destination)
    private fun rule(id: String, kind: ForecastKind, amount: Long) = ForecastRule(id, period.start, id, kind, amount,
        if (kind == ForecastKind.INCOME) Category.SALARY else Category.HOME, ForecastFrequency.MONTHLY, period.start, dayOfMonth = 10)

    @Test fun `result and transfer preserve wealth and previous savings`() {
        val accounts = listOf(AccountOption("principal", "Principal", 0), AccountOption(AccountFunds.RESERVE_KEY, AccountFunds.RESERVE_NAME, 0))
        val references = accounts.mapIndexed { index, a -> AccountBalanceReference(index.toLong(), a.key, 0, time("2026-11-01"), index.toLong(), "Inicial") }
        val records = listOf(row("salary",400000,Movement.INCOME),row("expenses",250000,Movement.EXPENSE),row("save",100000,Movement.TRANSFER,destination=AccountFunds.RESERVE_NAME))
        val summary = CentralFinance.summarize(records, ForecastDataset(), period, zone, time("2026-12-05"))
        assertEquals(150000L, summary.recorded.result)
        assertEquals(100000L, summary.reserveTransfersInCents)
        val balances = AccountBalances.calculate(accounts,emptySet(),references,records,time("2026-12-04"))
        val funds = AccountFunds.of(balances)
        assertEquals(50000L,funds.operating.cents)
        assertEquals(100000L,funds.reserve.cents)
        assertEquals(150000L,funds.wealth.cents)
        assertEquals(funds, AccountFunds.of(AccountBalances.calculate(accounts,emptySet(),references,records,time("2027-01-04"))))
    }
    @Test fun `configured financial cycles handle year short months and comparison`() {
        val rules = listOf(CycleRule(LocalDate.of(2026,1,1),5))
        assertEquals(period,FinancialPeriods.month(month,rules))
        assertEquals(BudgetPeriod(LocalDate.of(2026,12,5),LocalDate.of(2027,1,5)),FinancialPeriods.month(YearMonth.of(2026,12),rules))
        val last = listOf(CycleRule(LocalDate.of(2026,1,1),31))
        assertEquals(BudgetPeriod(LocalDate.of(2027,2,28),LocalDate.of(2027,3,31)),FinancialPeriods.month(YearMonth.of(2027,2),last))
        assertEquals(BudgetPeriod(LocalDate.of(2028,2,29),LocalDate.of(2028,3,31)),FinancialPeriods.month(YearMonth.of(2028,2),last))
        val rows = listOf(row("before",10,Movement.EXPENSE,"2026-11-04"),row("first",20,Movement.EXPENSE,"2026-11-05"),row("last",30,Movement.EXPENSE,"2026-12-04"),row("after",40,Movement.EXPENSE,"2026-12-05"))
        assertEquals(50L,Ledger.period(rows,period,zone).expenses)
        val comparison = MonthComparisons.compare(rows,period,FinancialPeriods.previous(period,rules),LocalDate.of(2026,12,10),zone)
        assertEquals(50L,comparison.currentTotalCents)
        assertEquals(10L,comparison.previousTotalCents)
    }
    @Test fun `recorded income replaces forecast without disappearing from projections`() {
        val data = ForecastDataset(rules=listOf(rule("salary",ForecastKind.INCOME,400000),rule("rent",ForecastKind.EXPENSE,250000)))
        val salary = row("received",390000,Movement.INCOME).copy(forecastItemId="salary",forecastOccurrenceKey="month:2026-11")
        val summary = CentralFinance.summarize(listOf(salary),data,period,zone,time("2026-11-15"))
        assertEquals(0L,summary.forecast.incomeCents)
        assertEquals(390000L,summary.projectedReport.incomeCents)
        assertEquals(140000L,summary.projectedReport.marginCents)
        assertEquals(250000L,summary.projectedReport.commitmentsCents)
    }
    @Test fun `scheduled records replace linked forecasts and remain separate from actual`() {
        val data = ForecastDataset(rules=listOf(rule("salary",ForecastKind.INCOME,400000)))
        val salary = row("scheduled",390000,Movement.INCOME).copy(forecastItemId="salary",forecastOccurrenceKey="month:2026-11")
        val summary = CentralFinance.summarize(listOf(salary),data,period,zone,time("2026-11-05"))
        assertEquals(0L,summary.recorded.income)
        assertEquals(390000L,summary.scheduled.income)
        assertEquals(390000L,summary.projectedReport.incomeCents)
    }
    @Test fun `ignore override negative periods refunds and legacy reserve do not invent money`() {
        val rent = rule("rent",ForecastKind.EXPENSE,250000)
        val data = ForecastDataset(rules=listOf(rent),overrides=listOf(ForecastOverride("rent","month:2026-11",ignored=true)),
            reserveEvents=listOf(ReserveEvent(1,ReserveEventKind.SET,999999,time("2026-11-01"),"Histórico")))
        val rows = listOf(row("expense",10000,Movement.EXPENSE),row("refund",2000,Movement.REFUND))
        val summary = CentralFinance.summarize(rows,data,period,zone,time("2026-12-05"))
        assertEquals(-8000L,summary.recorded.result)
        assertEquals(-8000L,summary.projectedReport.marginCents)
        assertNull(summary.projectedReport.reserveBalanceCents)
        assertEquals(0L,summary.projectedReport.allocatedCents)
        assertEquals(0L,CentralFinance.summarize(emptyList(),ForecastDataset(),period,zone,time("2026-12-05")).recorded.result)
    }
    @Test fun `unknown accounts stay partial and archived money stays in wealth`() {
        val unknown = AccountBalance(AccountOption("unknown","Sem saldo",0),false,null,null)
        val archived = AccountBalance(AccountOption("old","Antiga",0),true,10000,null)
        val funds = AccountFunds.of(listOf(unknown,archived))
        assertTrue(funds.operating.partial)
        assertTrue(funds.wealth.partial)
        assertEquals(10000L,funds.wealth.cents)
        assertEquals(0L,funds.operating.cents)
    }
    @Test fun `cycle changes keep transition days and anchored occurrences exactly once`() {
        val old = listOf(CycleRule(LocalDate.of(2026,1,1),5))
        val changed = old + CycleRule(LocalDate.of(2026,12,5),20)
        val december = FinancialPeriods.month(YearMonth.of(2026,12),changed)
        assertEquals(BudgetPeriod(LocalDate.of(2026,12,5),LocalDate.of(2027,1,20)),december)
        val january = FinancialPeriods.month(YearMonth.of(2027,1),changed)
        assertEquals(december.endExclusive,january.start)
        val recurring = rule("cycle",ForecastKind.EXPENSE,1000).copy(cycleAnchored=true)
        val summary = CentralFinance.summarize(emptyList(),ForecastDataset(rules=listOf(recurring),cycleRules=changed),december,zone,time("2027-02-01"))
        assertEquals(2,summary.forecast.lines.size)
        assertEquals(setOf("cycle:2026-12-05","cycle:2026-12-20"),summary.forecast.lines.map { it.occurrenceKey }.toSet())
        assertEquals(2000L,summary.projectedExpensesCents)
    }
    @Test fun `editing one occurrence and future versions preserves earlier projected history`() {
        val original = rule("salary",ForecastKind.INCOME,400000)
        val future = original.copy(effectiveFrom=LocalDate.of(2026,12,5),amountCents=450000)
        val data = ForecastDataset(rules=listOf(original,future),overrides=listOf(ForecastOverride("salary","month:2026-11",amountCents=390000)))
        assertEquals(390000L,CentralFinance.summarize(emptyList(),data,period,zone,time("2026-11-05")).projectedIncomeCents)
        val december = BudgetPeriod(LocalDate.of(2026,12,5),LocalDate.of(2027,1,5))
        assertEquals(450000L,CentralFinance.summarize(emptyList(),data,december,zone,time("2026-11-05")).projectedIncomeCents)
    }
    @Test fun `partial cycle comparison uses equal elapsed days and graph uses same bounds`() {
        val before = BudgetPeriod(LocalDate.of(2026,10,5),LocalDate.of(2026,11,5))
        val rows = listOf(row("previous-first",100,Movement.EXPENSE,"2026-10-05"),row("previous-third",900,Movement.EXPENSE,"2026-10-07"),
            row("current-first",200,Movement.EXPENSE,"2026-11-05"),row("current-third",800,Movement.EXPENSE,"2026-11-07"),row("outside",500,Movement.EXPENSE,"2026-12-05"),
            row("transfer",10000,Movement.TRANSFER,destination=AccountFunds.RESERVE_NAME))
        val comparison=MonthComparisons.compare(rows,period,before,LocalDate.of(2026,11,6),zone)
        assertEquals(2,comparison.throughDay)
        assertEquals(200L,comparison.currentTotalCents)
        assertEquals(100L,comparison.previousTotalCents)
        assertEquals(1000L,ExpenseReports.period(rows,period,zone,ExpenseDimension.CATEGORY).total)
    }
    @Test fun `reserve withdrawal changes location only and initial balances are not income`() {
        val accounts=listOf(AccountOption("principal","Principal",0),AccountOption(AccountFunds.RESERVE_KEY,AccountFunds.RESERVE_NAME,0))
        val refs=listOf(AccountBalanceReference(1,"principal",50000,time("2026-11-01"),1,"Inicial"),
            AccountBalanceReference(2,AccountFunds.RESERVE_KEY,100000,time("2026-11-01"),2,"Inicial"))
        val rows=listOf(row("withdraw",30000,Movement.TRANSFER,account=AccountFunds.RESERVE_NAME,destination="Principal"))
        val summary=CentralFinance.summarize(rows,ForecastDataset(),period,zone,time("2026-12-05"))
        assertEquals(0L,summary.recorded.result)
        assertEquals(30000L,summary.reserveTransfersOutCents)
        assertEquals(0L,summary.projectedReport.unallocatedCents)
        val funds=AccountFunds.of(AccountBalances.calculate(accounts,emptySet(),refs,rows,time("2026-12-04")))
        assertEquals(80000L,funds.operating.cents)
        assertEquals(70000L,funds.reserve.cents)
        assertEquals(150000L,funds.wealth.cents)
    }

}
