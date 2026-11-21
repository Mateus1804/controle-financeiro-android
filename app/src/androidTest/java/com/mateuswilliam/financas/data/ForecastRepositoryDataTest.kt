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

    @Test fun changingFrequencyKeepsTheLinkedCycleAndAllowsTheNextCycle() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val records = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val month = LocalDate.of(2026, 11, 1)
            val due = month.plusDays(9)
            val next = month.plusMonths(1)
            val monthly = ForecastRule("rent", due, "Aluguel", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.MONTHLY, due)
            forecasts.saveRule(monthly)
            val record = Record("rent-paid", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
                "Carteira", "Aluguel lançado", due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = monthly.itemId, forecastOccurrenceKey = "month:2026-11")
            assertTrue(records.save(record, null))

            val converted = monthly.copy(effectiveFrom = month, frequency = ForecastFrequency.ONCE)
            assertTrue(runCatching { forecasts.saveRule(converted) }.exceptionOrNull() is IllegalArgumentException)
            val summary = CentralFinance.summarize(records.exportRecords(), forecasts.dataset.first(),
                BudgetPeriod(month, next), ZoneOffset.UTC, Long.MAX_VALUE)
            assertEquals(60000L, summary.projectedExpensesCents)

            forecasts.saveRule(converted.copy(effectiveFrom = next, startDate = due.plusMonths(1)))
            assertEquals(record, records.exportRecords().single())
            val future = ForecastCalculator.report(forecasts.dataset.first(), BudgetPeriod(next, next.plusMonths(1)))
            assertEquals("once", future.lines.single().occurrenceKey)
        } finally { db.close() }
    }

    @Test fun overwritingABackdatedRuleCannotDropItsLinkedOccurrence() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val records = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val effective = LocalDate.of(2026, 1, 15)
            val date = LocalDate.of(2025, 12, 20)
            val once = ForecastRule("rent", effective, "Aluguel", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.ONCE, date)
            forecasts.saveRule(once)
            val record = Record("rent-paid", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
                "Carteira", "Aluguel lançado", date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = once.itemId, forecastOccurrenceKey = "once")
            assertTrue(records.save(record, null))

            val monthly = once.copy(frequency = ForecastFrequency.MONTHLY, startDate = effective)
            assertTrue(runCatching { forecasts.saveRule(monthly) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(once, forecasts.dataset.first().rules.single())
            assertEquals(record, records.exportRecords().single())
        } finally { db.close() }
    }

    @Test fun aNewLinkCannotUseAnOccurrenceRemovedSinceTheEditorOpened() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val records = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val date = LocalDate.of(2026, 11, 10)
            val monthly = ForecastRule("rent", date, "Aluguel", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.MONTHLY, date)
            forecasts.saveRule(monthly)
            val record = Record("rent-paid", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
                "Carteira", "Aluguel lançado", date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = monthly.itemId, forecastOccurrenceKey = "month:2026-11")
            forecasts.saveRule(monthly.copy(frequency = ForecastFrequency.ONCE))

            assertTrue(runCatching { records.save(record, null) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(records.exportRecords().isEmpty())
            assertTrue(records.save(record.copy(forecastOccurrenceKey = "once"), null))
        } finally { db.close() }
    }

    @Test fun choosingACycleDayForTheFirstTimeDoesNotRepeatAnAlreadyRecordedExpense() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val records = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val month = LocalDate.of(2026, 11, 1)
            val today = month.plusDays(12)
            val market = ForecastRule("market", month, "Mercado", ForecastKind.EXPENSE, 60000,
                Category.FOOD, ForecastFrequency.MONTHLY, month, cycleAnchored = true)
            forecasts.saveRule(market)
            val record = Record("market-paid", 60000, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
                "Carteira", "Mercado lançado", today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = market.itemId, forecastOccurrenceKey = "cycle:$month")
            assertTrue(records.save(record, null))

            forecasts.setCycleDay(5, today)
            val data = forecasts.dataset.first()
            val period = CycleCalendar.periodContaining(today, data.cycleRules)
            val summary = CentralFinance.summarize(records.exportRecords(), data, period, ZoneOffset.UTC, Long.MAX_VALUE)
            assertEquals(month, period.start)
            assertEquals(60000L, summary.projectedExpensesCents)
            assertTrue(summary.forecast.lines.isEmpty())
        } finally { db.close() }
    }

    @Test fun restartingInstallmentsEarlierCannotReplaceAnAlreadyLinkedFutureSeries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val records = FinanceRepository(db, CapturePreferences(context))
            val forecasts = ForecastRepository(db)
            val january = LocalDate.of(2026, 1, 1)
            val february = january.plusMonths(1)
            val march = january.plusMonths(2)
            val monthly = ForecastRule("rent", january, "Aluguel", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.MONTHLY, january.plusDays(9))
            forecasts.saveRule(monthly)
            val installments = monthly.copy(effectiveFrom = february, startDate = february.plusDays(9),
                frequency = ForecastFrequency.INSTALLMENTS, installmentCount = 3)
            forecasts.saveRule(installments)
            val record = Record("rent-paid", 20000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
                "Carteira", "Aluguel lançado", march.plusDays(9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = monthly.itemId, forecastOccurrenceKey = "installment:2")
            assertTrue(records.save(record, null))

            val restart = installments.copy(effectiveFrom = january, startDate = monthly.startDate)
            assertTrue(runCatching { forecasts.saveRule(restart) }.exceptionOrNull() is IllegalArgumentException)
            val summary = CentralFinance.summarize(records.exportRecords(), forecasts.dataset.first(),
                BudgetPeriod(march, march.plusMonths(1)), ZoneOffset.UTC, Long.MAX_VALUE)
            assertEquals(20000L, summary.projectedExpensesCents)
        } finally { db.close() }
    }

    @Test fun anOccurrenceRejectsANewLinkAndKeepsEditableHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db, CapturePreferences(context))
            val start = LocalDate.of(2026, 11, 5)
            ForecastRepository(db).saveRule(ForecastRule("rent", start, "Aluguel", ForecastKind.EXPENSE,
                10000, Category.HOME, ForecastFrequency.MONTHLY, start))
            val record = Record("first", 10000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
                "Carteira", "Aluguel", start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                forecastItemId = "rent", forecastOccurrenceKey = "month:2026-11")
            assertTrue(repo.save(record, null))
            val duplicate = record.copy(id = "duplicate")
            assertTrue(runCatching { repo.save(duplicate, null) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.save(record.copy(id = "next", forecastOccurrenceKey = "month:2026-12"), null))

            // Old installations could have several links for the same occurrence.
            db.financeDao().insertRecord(RecordEntity.from(duplicate, null))
            assertTrue(repo.save(record.copy(description = "Aluguel da casa"), null, editing = true))
            assertEquals(setOf("first", "next", "duplicate"), repo.exportRecords().map { it.id }.toSet())
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

    @Test fun editingTheFirstMonthlyOccurrenceKeepsItsKeyAndSavesTheEarlierDueDate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        try {
            val forecasts = ForecastRepository(db)
            val cycleStart = LocalDate.of(2026, 11, 5)
            val due = cycleStart.withDayOfMonth(20)
            val cycle = BudgetPeriod(cycleStart, cycleStart.plusMonths(1))
            val first = ForecastRule("aluguel", due, "Aluguel", ForecastKind.EXPENSE, 10000,
                Category.HOME, ForecastFrequency.MONTHLY, due)
            val key = "month:2026-11"
            forecasts.saveRule(first)
            forecasts.saveRule(first.copy(effectiveFrom = cycleStart, dayOfMonth = 10), replaceOccurrenceKey = key)
            assertEquals(cycleStart.withDayOfMonth(10), ForecastCalculator.report(forecasts.dataset.first(), cycle).lines.single().date)

            forecasts.saveRule(first.copy(effectiveFrom = cycleStart, dayOfMonth = 2), replaceOccurrenceKey = key)
            val line = ForecastCalculator.report(forecasts.dataset.first(), cycle).lines.single { it.occurrenceKey == key }
            assertEquals(cycleStart, line.date)
            assertEquals(10000L, line.amountCents)
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
