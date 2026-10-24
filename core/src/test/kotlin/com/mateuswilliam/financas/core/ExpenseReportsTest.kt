package com.mateuswilliam.financas.core

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ExpenseReportsTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val month = YearMonth.of(2026, 10)
    private val time = month.atDay(10).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun record(id: String, amount: Long = 100, movement: Movement = Movement.EXPENSE,
                       category: Category = Category.FOOD, bank: String = "Conta", at: Long = time) =
        Record(id, amount, movement, PaymentMethod.PIX, category, bank, "Teste", at)

    @Test fun `distribui despesas brutas e separa estornos sem mudar o resultado liquido`() {
        val records = listOf(record("gasto", 10000), record("estorno", 12000, Movement.REFUND),
            record("receita", 20000, Movement.INCOME), record("transferencia", 9000, Movement.TRANSFER))
        val report = ExpenseReports.month(records, month, zone, ExpenseDimension.CATEGORY)
        assertEquals(10000L, report.total); assertEquals(12000L, report.refunds)
        assertEquals(10000, report.groups.single().basisPoints)
        assertEquals(-2000L, Ledger.month(records, month, zone).expenses)
    }
    @Test fun `restos de arredondamento somam cem por cento`() {
        val records = listOf(record("a", category = Category.FOOD), record("b", category = Category.HOME), record("c", category = Category.HEALTH))
        val report = ExpenseReports.month(records, month, zone, ExpenseDimension.CATEGORY)
        assertEquals(10000, report.groups.sumOf { it.basisPoints })
        assertEquals(listOf(3334, 3333, 3333), report.groups.map { it.basisPoints }.sortedDescending())
        assertEquals(report.total, report.groups.sumOf { it.cents })
    }
    @Test fun `agrupa bancos normalizados e filtra os mesmos registros`() {
        val records = listOf(record("a", bank = " Inter  pessoal "), record("b", bank = "INTER pessoal"), record("c", bank = "Inter trabalho"))
        val report = ExpenseReports.month(records, month, zone, ExpenseDimension.BANK)
        assertEquals(2, report.groups.size)
        val first = report.groups.first()
        val filter = ExpenseFilter(ExpenseDimension.BANK, first.key, first.label)
        assertEquals(first.cents, records.filter(filter::matches).sumOf { it.amountCents })
    }
    @Test fun `periodo vazio e receita isolada nao criam fatias`() {
        assertTrue(ExpenseReports.month(listOf(record("a", movement = Movement.INCOME)), month, zone, ExpenseDimension.METHOD).groups.isEmpty())
        assertEquals(0L, ExpenseReports.month(listOf(record("a")), month.plusMonths(1), zone, ExpenseDimension.CATEGORY).total)
    }
    @Test fun `usa o fuso do periodo e metodos diferentes`() {
        val boundary = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val report = ExpenseReports.month(listOf(record("a", at = boundary - 1), record("b", at = boundary)), month, zone, ExpenseDimension.METHOD)
        assertEquals(100L, report.total); assertEquals("Pix", report.groups.single().label)
    }
    @Test fun `valores altos e muitos grupos mantem centavos e percentuais exatos`() {
        val records = (1..1000).map { record(it.toString(), Money.MAX_CENTS, bank = "Conta $it") }
        val report = ExpenseReports.month(records, month, zone, ExpenseDimension.BANK)
        assertEquals(Money.MAX_CENTS * 1000, report.total)
        assertEquals(10000, report.groups.sumOf { it.basisPoints })
        assertTrue(report.groups.all { it.cents > 0 && it.basisPoints >= 0 })
    }
}
