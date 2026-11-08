package com.mateuswilliam.financas.core

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RefundNatureTest {
    private val zone = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 11)
    private fun record(id: String, movement: Movement, cents: Long, category: Category = Category.SALARY) =
        Record(id, cents, movement, PaymentMethod.PIX, category, "Conta", "Descrição",
            month.atDay(3).atStartOfDay(zone).toInstant().toEpochMilli())

    @Test fun `estorno de receita reduz receita sem atingir despesas ou orcamentos`() {
        val income = record("salario", Movement.INCOME, 10000)
        val refund = Refunds.applyOriginal(record("devolucao", Movement.REFUND, 2000), income)
        val expense = record("compra", Movement.EXPENSE, 3000, Category.FOOD)
        val records = listOf(income, refund, expense)
        val summary = Ledger.month(records, month, zone)
        assertEquals(8000L, summary.income)
        assertEquals(3000L, summary.expenses)
        assertEquals(5000L, records.sumOf { it.cashContribution() })
        assertEquals(mapOf(Category.FOOD to 3000L), summary.categories)
        assertEquals(0L, ExpenseReports.month(records, month, zone, ExpenseDimension.CATEGORY).refunds)
        val usage = Budgets.usage(records, listOf(MonthlyBudget(month.toString(), Category.SALARY, 9000)), month, zone)
        assertEquals(0L, usage.single().spentCents)
        assertEquals(Category.SALARY, refund.category)
        assertEquals("salario", refund.originalRecordId)
        // The snapshot still works after the original is removed.
        assertEquals(-2000L, Ledger.month(listOf(refund), month, zone).income)
    }

    @Test fun `estorno legado continua reduzindo despesas e orcamento`() {
        val records = listOf(record("compra", Movement.EXPENSE, 3000, Category.FOOD),
            record("estorno", Movement.REFUND, 1000, Category.FOOD))
        assertEquals(2000L, Ledger.month(records, month, zone).expenses)
        assertEquals(1000L, ExpenseReports.month(records, month, zone, ExpenseDimension.CATEGORY).refunds)
        assertEquals(2000L, Budgets.usage(records, listOf(MonthlyBudget(month.toString(), Category.FOOD, 5000)), month, zone).single().spentCents)
    }

    @Test fun `categorias oferecidas respeitam natureza mas legado continua valido`() {
        assertTrue(Category.SALARY in categoriesFor(Movement.INCOME))
        assertFalse(Category.FOOD in categoriesFor(Movement.INCOME))
        assertTrue(Category.SERVICES in categoriesFor(Movement.EXPENSE))
        assertEquals(categoriesFor(Movement.INCOME), categoriesFor(Movement.REFUND, Movement.INCOME))
        assertTrue(categoriesFor(Movement.TRANSFER).isEmpty())
        assertNull(RecordValidation.error(record("legado", Movement.INCOME, 100, Category.FOOD)))
    }

    @Test fun `valida vinculo e impede encadear ou estornar a si mesmo`() {
        val refund = record("estorno", Movement.REFUND, 100)
        assertNotNull(RecordValidation.error(refund.copy(originalRecordId = refund.id)))
        assertNotNull(RecordValidation.error(refund.copy(originalRecordId = "")))
        assertNotNull(RecordValidation.error(refund.copy(reversalMovement = Movement.TRANSFER)))
        assertNotNull(RecordValidation.error(record("receita", Movement.INCOME, 100).copy(originalRecordId = "origem")))
        assertTrue(runCatching { Refunds.applyOriginal(refund, refund) }.isFailure)
        assertTrue(runCatching { Refunds.applyOriginal(refund, record("estorno", Movement.INCOME, 100)) }.isFailure)
    }

    @Test fun `csv exporta natureza vinculo e transferencia sem categoria`() {
        val refund = Refunds.applyOriginal(record("r", Movement.REFUND, 100), record("origem", Movement.INCOME, 100))
        val csv = Ledger.csv(listOf(refund, record("t", Movement.TRANSFER, 100, Category.FOOD)), zone)
        assertTrue(csv.contains("Natureza do estorno"))
        assertTrue(csv.contains("\"Receita\";\"origem\""))
        assertTrue(csv.contains("\"Transferência\";\"Pix\";\"\""))
    }
}
