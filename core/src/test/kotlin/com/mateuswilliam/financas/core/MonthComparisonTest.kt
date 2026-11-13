package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MonthComparisonTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun rec(id: String, cents: Long, category: Category, date: LocalDate, movement: Movement = Movement.EXPENSE) =
        Record(id, cents, movement, PaymentMethod.PIX, category, "Conta", "Teste", at(date))
    private val nov = YearMonth.of(2026, 11)

    @Test fun `mes fechado compara o mes inteiro e ordena pela maior diferenca em reais`() {
        val records = listOf(rec("a", 30000, Category.TRANSPORT, LocalDate.of(2026, 11, 25)), rec("b", 18000, Category.TRANSPORT, LocalDate.of(2026, 10, 3)),
            rec("c", 10000, Category.FOOD, LocalDate.of(2026, 11, 2)), rec("d", 50000, Category.FOOD, LocalDate.of(2026, 10, 30)))
        val result = MonthComparisons.compare(records, nov, LocalDate.of(2027, 1, 5), zone)
        assertNull(result.throughDay)
        assertEquals(listOf(Category.FOOD, Category.TRANSPORT), result.differences.map { it.category })
        assertEquals(-40000L, result.differences[0].deltaCents)
        assertEquals(12000L, result.differences[1].deltaCents)
    }
    @Test fun `mes em andamento compara periodos equivalentes`() {
        val records = listOf(rec("a", 10000, Category.FOOD, LocalDate.of(2026, 11, 5)), rec("b", 7000, Category.FOOD, LocalDate.of(2026, 10, 4)),
            rec("c", 90000, Category.FOOD, LocalDate.of(2026, 10, 20)), rec("d", 5000, Category.FOOD, LocalDate.of(2026, 11, 20)))
        val result = MonthComparisons.compare(records, nov, LocalDate.of(2026, 11, 10), zone)
        assertEquals(10, result.throughDay)
        assertEquals(10000L, result.differences.single().currentCents)
        assertEquals(7000L, result.differences.single().previousCents)
    }
    @Test fun `estorno reduz o gasto liquido e categoria sem movimento some`() {
        val records = listOf(rec("a", 10000, Category.HOME, LocalDate.of(2026, 11, 2)), rec("e", 4000, Category.HOME, LocalDate.of(2026, 11, 3), Movement.REFUND),
            rec("r", 9000, Category.SALARY, LocalDate.of(2026, 11, 3), Movement.INCOME))
        val result = MonthComparisons.compare(records, nov, LocalDate.of(2027, 1, 1), zone)
        assertEquals(6000L, result.differences.single().currentCents)
    }
    @Test fun `mes anterior mais curto limita o dia equivalente`() {
        val records = listOf(rec("a", 100, Category.FOOD, LocalDate.of(2026, 2, 28)))
        val result = MonthComparisons.compare(records, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31), zone)
        assertEquals(100L, result.previousTotalCents)
    }
    @Test fun `formata diferenca com sinal`() {
        assertTrue(MonthComparisons.signed(12000).startsWith("+"))
        assertTrue(MonthComparisons.signed(-12000).startsWith("−"))
    }
}
