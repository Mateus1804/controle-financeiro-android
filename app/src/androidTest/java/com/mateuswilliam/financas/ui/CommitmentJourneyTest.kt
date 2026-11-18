package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Current forecasts, independent actual records, occurrence edits and encrypted restore. */
class CommitmentJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { ForecastReportTestDriver(rule) }
    private val app get() = ui.app

    @Before fun reset() = ui.reset()

    @Test fun forecastToIndependentExpenseOccurrenceAdjustmentStopAndEncryptedRestore() = runBlocking<Unit> {
        ui.startForecast(Movement.INCOME, "Salário fictício", "100000")
        ui.saveForecast()
        ui.waitRules(1)
        ui.startForecast(Movement.EXPENSE, "Compra prevista fictícia", "60001", ForecastFrequency.INSTALLMENTS)
        ui.type("forecast-installment-count", "3")
        ui.saveForecast()
        ui.waitRules(2)
        val cycle = ui.period()
        val next = CycleCalendar.periodContaining(cycle.endExclusive, ui.current().cycleRules)
        val third = CycleCalendar.periodContaining(next.endExclusive, ui.current().cycleRules)
        val firstPart = ui.report().lines.single { it.kind == ForecastKind.EXPENSE }
        assertEquals(20001L, firstPart.amountCents)
        assertEquals(20000L, ui.report(next).totalExpensesCents)
        assertEquals(20000L, ui.report(third).totalExpensesCents)
        assertTrue(app.repository.exportRecords().isEmpty())

        // Registering an actual expense never confirms or consumes a forecast occurrence.
        ui.clickTag("add-realized-expense")
        ui.type("amount-input", "22000")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Despesa realizada fictícia")
        rule.onNodeWithText("Descrição").performImeAction()
        ui.clickText("Registrar despesa")
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val actual = app.repository.exportRecords().single()
        assertEquals(22000L, actual.amountCents)
        assertEquals(Movement.EXPENSE, actual.movement)
        assertEquals(20001L, ui.report().totalExpensesCents)
        assertEquals(79999L, ui.report().marginCents)

        ui.edit(firstPart)
        rule.onNodeWithText("Alterar só este mês").assertExists()
        ui.type("forecast-amount", "22500")
        ui.saveForecast()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 22500L }
        assertEquals(22500L, ui.report().totalExpensesCents)
        assertEquals(77500L, ui.report().marginCents)
        assertEquals("22.50", ui.report().expensePercentage?.toPlainString())
        assertEquals(20000L, ui.report(next).totalExpensesCents)
        assertEquals(listOf(actual), app.repository.exportRecords())
        ui.openProjections()
        ui.assertProjection(cycle, "44,50", 55500)
        rule.runOnIdle { app.settings.hideAmounts(true) }
        rule.onAllNodesWithText("Percentual oculto").onFirst().assertExists()
        rule.onAllNodes(hasText("44,50%", substring = true) or hasContentDescription("44,50%", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasText(Money.format(55500), substring = true) or
            hasContentDescription(Money.format(55500), substring = true)).assertCountEquals(0)
        rule.runOnIdle { app.settings.hideAmounts(false) }

        rule.openRendlyPlanner()
        ui.clickTag("forecast-cycle-next", dashboard = true)
        val nextPart = ui.report(next).lines.single { it.kind == ForecastKind.EXPENSE }
        ui.openActions(nextPart)
        ui.clickText("Interromper futuras")
        ui.waitData { it.rules.any { value -> value.itemId == firstPart.itemId && !value.active } }
        assertEquals(22500L, ui.report(cycle).totalExpensesCents)
        assertEquals(0L, ui.report(next).totalExpensesCents)
        assertEquals(0L, ui.report(third).totalExpensesCents)
        assertEquals(listOf(actual), app.repository.exportRecords())

        // The document picker is covered by BackupDocumentFlowTest; this checks encrypted data.
        val before = app.repository.snapshot()
        val encrypted = app.backups.create(testPassword())
        app.forecasts.saveOverride(ForecastOverride(firstPart.itemId, firstPart.occurrenceKey, 23000))
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 23000L }
        assertEquals(23000L, ui.report(cycle).totalExpensesCents)
        val preview = app.backups.stage(ByteArrayInputStream(encrypted), testPassword())
        assertEquals(23000L, ui.report(cycle).totalExpensesCents)
        app.backups.restore(preview)
        ui.model.refresh()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 22500L }
        val restored = app.repository.snapshot()
        assertEquals(before.forecastRules, restored.forecastRules)
        assertEquals(before.forecastOverrides, restored.forecastOverrides)
        assertEquals(before.cycleRules, restored.cycleRules)
        assertEquals(before.records, restored.records)
        assertEquals(listOf(actual), app.repository.exportRecords())
        assertEquals(22500L, ui.report(cycle).totalExpensesCents)
        assertEquals(0L, ui.report(next).totalExpensesCents)
        assertEquals(0L, ui.report(third).totalExpensesCents)
        rule.activityRule.scenario.recreate()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 22500L }
        ui.openProjections()
        ui.assertProjection(next, "0,00", 100000)
        assertEquals(listOf(actual), app.repository.exportRecords())
    }

    private fun testPassword() = "Senha fictícia de teste 2026!".toCharArray()
}
