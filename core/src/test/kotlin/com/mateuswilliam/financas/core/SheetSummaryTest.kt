package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetSummaryTest {
    private val zone = ZoneOffset.UTC
    private val main = "Carteira"
    private val reserve = AccountFunds.RESERVE_NAME
    private val accounts = listOf(main, reserve).map { AccountOption(AccountNames.key(it), it, 0) }
    private val period = BudgetPeriod(LocalDate.parse("2026-12-05"), LocalDate.parse("2027-01-05"))
    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun entry(id: String, movement: Movement, cents: Long, date: String, origin: String = main, destination: String = "") =
        Record(id, cents, movement, PaymentMethod.UNKNOWN, if (movement == Movement.INCOME) Category.SALARY else Category.HOME,
            origin, id, at(date), destination)

    @Test fun transferPreservesTheResultAndCombinedWealth() {
        val records = listOf(
            entry("salário", Movement.INCOME, 400000, "2026-12-05"),
            entry("moradia", Movement.EXPENSE, 250000, "2026-12-06"),
            entry("guardar", Movement.TRANSFER, 100000, "2026-12-07", destination = reserve),
        )
        val summary = SheetSummary.calculate(records, accounts, emptySet(), emptyList(), period, zone, at("2027-01-05"))
        assertEquals(400000L, summary.ledger.income)
        assertEquals(250000L, summary.ledger.expenses)
        assertEquals(150000L, summary.ledger.result)
        assertEquals(50000L, summary.funds.operating.cents)
        assertEquals(100000L, summary.funds.reserve.cents)
        assertEquals(150000L, summary.funds.wealth.cents)
        assertEquals(100000L, summary.savedToReserve)
        assertEquals(250000L, summary.spending.groups.single().cents)
        assertEquals(10000, summary.spending.groups.single().basisPoints)
    }

    @Test fun resultUsesTheChosenCycleWhileBalancesCarryOverUpToTheCutoff() {
        val records = listOf(
            entry("anterior", Movement.INCOME, 60000, "2026-12-04"),
            entry("transfere", Movement.TRANSFER, 20000, "2026-12-06", destination = reserve),
            entry("resgata", Movement.TRANSFER, 3000, "2026-12-07", reserve, main),
            entry("na virada", Movement.EXPENSE, 10000, "2027-01-03"),
            entry("próximo ciclo", Movement.INCOME, 90000, "2027-01-05"),
        )
        val summary = SheetSummary.calculate(records, accounts, emptySet(), emptyList(), period, zone, at("2027-01-06"))
        assertEquals(-10000L, summary.ledger.result)
        assertEquals(33000L, summary.funds.operating.cents)
        assertEquals(17000L, summary.funds.reserve.cents)
        assertEquals(50000L, summary.funds.wealth.cents)
        assertEquals(3000L, summary.withdrawnFromReserve)
        assertEquals(at("2027-01-05") - 1, summary.balanceAsOf)

        val current = SheetSummary.calculate(records, accounts, emptySet(), emptyList(), period, zone, at("2026-12-08"))
        assertEquals(0L, current.ledger.result)
        assertEquals(60000L, current.funds.wealth.cents)
        assertEquals(emptyList<ExpenseGroup>(), current.spending.groups)
    }
}
