package com.mateuswilliam.financas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.io.ByteArrayInputStream

class ForecastRepositoryDataTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun newFormatRestoresMonthlyRulesOverridesAccountsAndReserve() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        val settings=CapturePreferences(context)
        try {
            val repo=FinanceRepository(db,settings); val forecasts=ForecastRepository(db)
            val day=LocalDate.parse("2026-11-05")
            forecasts.saveRule(ForecastRule("aluguel",day,"Aluguel",ForecastKind.EXPENSE,30000,Category.HOME,ForecastFrequency.MONTHLY,day))
            forecasts.saveOverride(ForecastOverride("aluguel","month:2026-12",35000))
            db.financeDao().insertReserveEvent(ReserveEventEntity(kind = ReserveEventKind.SET.name,
                amountCents = 50000, createdAt = System.currentTimeMillis(), reason = "Reserva informada", cycleStart = null))
            repo.configureAccountBalance("Carteira",100000,day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),"Saldo inicial")
            repo.save(Record("gasto",30000,Movement.EXPENSE,PaymentMethod.PIX,Category.HOME,"Carteira","Aluguel",day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId="aluguel",forecastOccurrenceKey="month:2026-11"),null)
            val expected=repo.snapshot(); val store=BackupStore(context,repo)
            val bytes=store.create("Backup novo fictício!".toCharArray())
            repo.clear()
            store.restore(store.stage(ByteArrayInputStream(bytes),"Backup novo fictício!".toCharArray()))
            val restored=repo.snapshot()
            assertEquals(expected.records,restored.records)
            assertEquals(expected.forecastRules,restored.forecastRules)
            assertEquals(expected.forecastOverrides,restored.forecastOverrides)
            assertEquals(expected.accountBalanceEvents,restored.accountBalanceEvents)
            assertEquals(expected.reserveEvents,restored.reserveEvents)
            val month = YearMonth.of(2026, 11)
            val period = BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))
            val summary = CentralFinance.summarize(restored.records.map { it.toRecord() }, forecasts.dataset.first(),
                period, ZoneOffset.UTC, Long.MAX_VALUE)
            assertEquals(0L, summary.forecast.totalExpensesCents)
            assertEquals(30000L, summary.recorded.expenses)
        } finally { settings.reset(); db.close() }
    }
    @Test fun linkedForecastCannotChangeItsNatureAndBackupRemainsValid() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db,CapturePreferences(context)); val forecasts=ForecastRepository(db)
            val day=LocalDate.parse("2026-11-01")
            val rule=ForecastRule("conta",day,"Conta",ForecastKind.EXPENSE,10000,Category.OTHER,ForecastFrequency.MONTHLY,day)
            forecasts.saveRule(rule)
            repo.save(Record("gasto",10000,Movement.EXPENSE,PaymentMethod.PIX,Category.OTHER,"Carteira","Conta",1,
                forecastItemId="conta",forecastOccurrenceKey="month:2026-11"),null)
            assertTrue(runCatching { forecasts.saveRule(rule.copy(kind=ForecastKind.INCOME,category=Category.OTHER_INCOME)) }.isFailure)
            assertEquals(ForecastKind.EXPENSE,forecasts.dataset.first().rules.single().kind)
            BackupSnapshot.validate(repo.snapshot())
        } finally { db.close() }
    }
    @Test fun oldPayloadIsRejectedBeforeReplacingLocalData() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db,CapturePreferences(context)); val store=BackupStore(context,repo)
            val old="{\"format\":\"controle-financeiro\",\"version\":7}".toByteArray()
            val bytes=BackupCrypto.encrypt(old,"Backup novo fictício!".toCharArray())
            assertTrue(runCatching { store.stage(ByteArrayInputStream(bytes),"Backup novo fictício!".toCharArray()) }.isFailure)
            assertTrue(repo.exportRecords().isEmpty())
        } finally { db.close() }
    }

    @Test fun overridingTheSeriesUpdatesTheSelectedMonthAndKeepsOtherExceptions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val forecasts = ForecastRepository(db)
            val start = LocalDate.of(2026, 1, 1)
            val rule = ForecastRule("aluguel", start, "Aluguel", ForecastKind.EXPENSE, 30000,
                Category.HOME, ForecastFrequency.MONTHLY, start.plusDays(4))
            forecasts.saveRule(rule)
            forecasts.saveOverride(ForecastOverride(rule.itemId, "month:2026-01", amountCents = 31000))
            val movedDate = start.plusMonths(1).plusDays(10)
            forecasts.saveOverride(ForecastOverride(rule.itemId, "month:2026-02", amountCents = 35000, date = movedDate))
            forecasts.saveOverride(ForecastOverride(rule.itemId, "month:2026-03", amountCents = 40000))
            forecasts.saveRule(rule.copy(effectiveFrom = start.plusMonths(1), amountCents = 50000),
                replaceOccurrenceKey = "month:2026-02")
            val data = forecasts.dataset.first()
            val amounts = (0L..3L).map { offset ->
                val from = start.plusMonths(offset)
                ForecastCalculator.report(data, BudgetPeriod(from, from.plusMonths(1))).totalExpensesCents
            }
            assertEquals(listOf(31000L, 50000L, 40000L, 50000L), amounts)
            val february = BudgetPeriod(start.plusMonths(1), start.plusMonths(2))
            assertEquals(movedDate, ForecastCalculator.report(data, february).lines.single().date)

            forecasts.saveRule(rule.copy(effectiveFrom = february.start, amountCents = 50000, dayOfMonth = 20),
                replaceOccurrenceKey = "month:2026-02")
            val revised = ForecastCalculator.report(forecasts.dataset.first(), february).lines.single()
            assertEquals(february.start.withDayOfMonth(20), revised.date)
            assertEquals(50000L, revised.amountCents)
        } finally { db.close() }
    }

    @Test fun restartingInstallmentsKeepsOldLinksAndOverridesWithTheirOriginalPurchase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repository = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val january = LocalDate.of(2026, 1, 1)
            val february = january.plusMonths(1)
            val march = february.plusMonths(1)
            val first = ForecastRule("compra", january, "Compra", ForecastKind.EXPENSE, 30000,
                Category.OTHER, ForecastFrequency.INSTALLMENTS, january.plusDays(4), installmentCount = 3)
            forecasts.saveRule(first)
            forecasts.saveOverride(ForecastOverride(first.itemId, "installment:1", amountCents = 12000))
            val purchase = Record("paga", 12000, Movement.EXPENSE, PaymentMethod.PIX, Category.OTHER,
                "Conta", "Primeira parcela", first.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = first.itemId, forecastOccurrenceKey = "installment:1")
            repository.save(purchase, null)
            forecasts.saveRule(first.copy(effectiveFrom = february, startDate = february.plusDays(4), frequency = ForecastFrequency.MONTHLY))
            forecasts.saveRule(first.copy(effectiveFrom = march, startDate = march.plusDays(4)))
            val data = forecasts.dataset.first()
            val firstCycle = BudgetPeriod(january, february)
            val restartCycle = BudgetPeriod(march, march.plusMonths(1))
            assertEquals(12000L, ForecastCalculator.report(data, firstCycle).totalExpensesCents)
            assertTrue(PendingForecasts(data, listOf(purchase)).lines(firstCycle).isEmpty())
            val restarted = PendingForecasts(data, listOf(purchase)).lines(restartCycle).single()
            assertNotEquals(first.itemId, restarted.itemId)
            assertEquals(10000L, restarted.amountCents)
            BackupSnapshot.validate(repository.snapshot())
        } finally { db.close() }
    }

    @Test fun anOldOverrideSurvivesASeriesReplacementWithoutMovingTheNewInstallment() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val forecasts = ForecastRepository(db)
            val january = LocalDate.of(2026, 1, 1)
            val february = january.plusMonths(1)
            val due = january.plusDays(4)
            val purchase = ForecastRule("compra", due, "Compra", ForecastKind.EXPENSE, 30000,
                Category.OTHER, ForecastFrequency.INSTALLMENTS, due, installmentCount = 3)
            forecasts.saveRule(purchase)
            forecasts.saveOverride(ForecastOverride(purchase.itemId, "installment:1", date = due.plusDays(1)))
            forecasts.saveRule(purchase.copy(effectiveFrom = january, frequency = ForecastFrequency.MONTHLY))
            forecasts.saveRule(purchase.copy(effectiveFrom = february, startDate = due.plusMonths(1)))
            val data = forecasts.dataset.first()
            assertEquals(due.plusDays(1), data.overrides.single().date)
            val pending = ForecastCalculator.report(data, BudgetPeriod(february, february.plusMonths(1))).lines.single()
            assertEquals(due.plusMonths(1), pending.date)
            assertEquals(10000L, pending.amountCents)
        } finally { db.close() }
    }
}
