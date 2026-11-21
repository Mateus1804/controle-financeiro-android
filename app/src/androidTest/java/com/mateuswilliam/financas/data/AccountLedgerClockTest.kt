package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.CycleCalendar
import com.mateuswilliam.financas.core.CycleRule
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.PaymentMethod
import com.mateuswilliam.financas.core.Record
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLedgerClockTest {
    @Test fun scheduledRecordAffectsBalanceAtItsEffectiveTime() = runTest {
        val zone = ZoneId.of("America/Sao_Paulo")
        val start = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val effectiveAt = start + 1000
        val scheduled = Record("agendado", 2000, Movement.INCOME, PaymentMethod.CARD,
            Category.SALARY, "Carteira", "Receita agendada", effectiveAt)
        val history = AccountLedgerHistory(
            listOf(AccountEntity("carteira", "Carteira", start)),
            listOf(RecordEntity.from(scheduled, null)),
            listOf(AccountBalanceEventEntity(1, "carteira", 5000, start, start, "Saldo inicial")),
        )
        val snapshots = mutableListOf<AccountLedger>()
        backgroundScope.launch { history.observe({ start + testScheduler.currentTime }, { zone }).toList(snapshots) }

        runCurrent()
        assertEquals(5000L, snapshots.single().balances.single().balanceCents)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, snapshots.size)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(start, effectiveAt), snapshots.map { it.asOf })
        assertEquals(7000L, snapshots.last().balances.single().balanceCents)
        assertEquals(snapshots.first().records, snapshots.last().records)
    }

    @Test fun localMidnightStartsTheNewCycleAcrossDaylightSavingTime() = runTest {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val history = AccountLedgerHistory(emptyList(), emptyList(), emptyList())
        val snapshots = mutableListOf<AccountLedger>()
        val rules = listOf(CycleRule(LocalDate.of(2026, 2, 9), 9))
        backgroundScope.launch { history.observe({ start + testScheduler.currentTime }, { zone }).toList(snapshots) }

        runCurrent()
        advanceTimeBy(23 * 60 * 60 * 1000L - 1)
        runCurrent()
        assertEquals(1, snapshots.size)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(start, nextMidnight), snapshots.map { it.asOf })
        assertEquals(listOf(LocalDate.of(2026, 2, 9), day.plusDays(1)), snapshots.map { snapshot ->
            val date = Instant.ofEpochMilli(snapshot.asOf).atZone(zone).toLocalDate()
            CycleCalendar.periodContaining(date, rules).start
        })
    }
}
