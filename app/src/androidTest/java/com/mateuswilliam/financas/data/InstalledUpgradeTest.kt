package com.mateuswilliam.financas.data

import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InstalledUpgradeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FinanceApplication
    private val date = LocalDate.of(2025, 1, 5)
    private val at get() = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val account = "Conta fictícia"
    private val name = "Instalação de teste"
    private val forecast = ForecastRule("upgrade-rent", date, "Aluguel fictício", ForecastKind.EXPENSE,
        25000, Category.HOME, ForecastFrequency.MONTHLY, date, account = account)
    private val adjustment = ForecastOverride(forecast.itemId, "month:2025-02", amountCents = 32000)
    private val entry get() = Record("upgrade-entry", 25000, Movement.EXPENSE, PaymentMethod.PIX,
        Category.HOME, account, "Despesa vinculada", at, forecastItemId = forecast.itemId, forecastOccurrenceKey = "month:2025-01")

    @Test fun seedRendlyV1() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installedUpgrade") == "seed")
        app.maintenanceReady.await()
        assertEquals(1, app.database.openHelper.writableDatabase.version)
        assertTrue(app.repository.exportRecords().isEmpty())
        app.repository.configureAccountBalance(account, 100000, at, "Saldo inicial")
        app.forecasts.saveRule(forecast)
        app.forecasts.saveOverride(adjustment)
        assertTrue(app.repository.save(entry, null))
        app.settings.setAppearance(Appearance.DARK)
        app.settings.hideAmounts(true)
        app.settings.acceptDisclosure()
        app.settings.selectPackages(setOf("fixture.bank"))
        // A última gravação síncrona confirma as preferências antes de encerrar o processo.
        app.settings.setDisplayName(name)
    }

    @Test fun preservesRendlyV1Data() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installedUpgrade") == "verify")
        app.maintenanceReady.await()
        assertEquals(1, app.database.openHelper.writableDatabase.version)
        assertEquals(listOf(entry), app.repository.exportRecords())
        val forecasts = app.forecasts.dataset.first()
        assertEquals(listOf(forecast), forecasts.rules)
        assertEquals(listOf(adjustment), forecasts.overrides)
        val balance = app.repository.observeAccountBalances(at).first().single()
        assertEquals(account, balance.account.label)
        assertEquals(75000L, balance.balanceCents)
        assertEquals(100000L, balance.reference?.balanceCents)
        val settings = app.settings.snapshot()
        assertEquals(name, settings.displayName)
        assertEquals(Appearance.DARK, settings.appearance)
        assertTrue(settings.hideAmounts)
        assertTrue(settings.enabled)
        assertEquals(setOf("fixture.bank"), settings.packages)
    }
}
