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
}
