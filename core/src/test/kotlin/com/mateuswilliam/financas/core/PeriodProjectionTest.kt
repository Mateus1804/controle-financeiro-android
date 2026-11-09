package com.mateuswilliam.financas.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PeriodProjectionTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val month = YearMonth.of(2026, 11)
    private fun record(id: String, value: Long, movement: Movement) = Record(id, value, movement,
        PaymentMethod.PIX, Category.OTHER, "Conta", id,
        LocalDate.of(2026, 11, 10).atStartOfDay(zone).toInstant().toEpochMilli(), "Destino")

    @Test fun realizedAndPendingShareResultWithoutCountingPaidTwice() {
        val paid = record("paid", 1000, Movement.EXPENSE)
        val pending = record("next", 2000, Movement.EXPENSE)
        val projection = PeriodProjections.month(listOf(record("salary", 10000, Movement.INCOME), paid),
            listOf(paid, pending, pending), month, zone, Long.MAX_VALUE)
        assertEquals(9000L, projection.realized.result)
        assertEquals(7000L, projection.projectedResult)
        assertEquals(1, projection.pendingCount)
    }

    @Test fun forecastsWithoutRealizedMovementsAreStillVisibleAndTransfersHaveNoResult() {
        val result = PeriodProjections.month(listOf(record("transfer", 200, Movement.TRANSFER)),
            listOf(record("income", 10000, Movement.INCOME), record("rent", 3000, Movement.EXPENSE)), month, zone, Long.MAX_VALUE)
        assertEquals(0L, result.realized.result)
        assertEquals(7000L, result.projectedResult)
        assertEquals(2, result.pendingCount)
    }
    @Test fun futureStoredMovementIsProjectedUntilItsDateWithoutDoubleCounting() {
        val income = record("future", 10000, Movement.INCOME)
        val before = PeriodProjections.month(listOf(income), listOf(income), month, zone, income.occurredAt - 1)
        assertEquals(0L, before.realized.result)
        assertEquals(10000L, before.projectedResult)
        assertEquals(1, before.pendingCount)
        val after = PeriodProjections.month(listOf(income), emptyList(), month, zone, income.occurredAt)
        assertEquals(10000L, after.realized.result)
        assertEquals(10000L, after.projectedResult)
        assertEquals(0, after.pendingCount)
    }

}
