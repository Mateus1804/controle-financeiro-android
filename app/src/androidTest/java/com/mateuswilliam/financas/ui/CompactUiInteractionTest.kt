package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compact interactions belong to the same planner that MainActivity shows to users. */
class CompactUiInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val model get() = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
    private val today get() = LocalDate.now()
    private fun current() = runBlocking { app.forecasts.dataset.first() }
    private fun period() = CycleCalendar.periodContaining(today, current().cycleRules)

    @Before fun reset() {
        runBlocking { app.maintenanceReady.await(); app.repository.clear(); app.forecasts.setCycleDay(5, today) }
        app.settings.finishIntro(); app.settings.dismissNamePrompt(); model.refresh()
        rule.waitUntil(10000) {
            model.dataset.value?.let { it.rules.isEmpty() && it.cycleRules.lastOrNull()?.day == 5 } == true
        }
        rule.openRendlyPlanner()
        rule.waitForIdle()
    }

    @Test fun installmentSecondaryActionsOpenFromAnAccessibleContextMenu() = runBlocking<Unit> {
        val cycle = period()
        app.forecasts.saveRule(ForecastRule("compacta", cycle.start, "Compra compacta", ForecastKind.EXPENSE,
            20000, Category.OTHER, ForecastFrequency.INSTALLMENTS, cycle.start, installmentCount = 2))
        rule.waitUntil(10000) { model.dataset.value?.rules?.size == 1 }
        val before = current()
        val line = ForecastCalculator.report(before, cycle).lines.single()
        val trigger = actions(line)
        scroll(trigger)
        rule.onNodeWithText("Interromper futuras").assertDoesNotExist()
        rule.onNodeWithTag(trigger).assertContentDescriptionEquals("Mais ações de Compra compacta")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        clickTag(trigger)
        rule.onNodeWithText("Pular este mês").assertIsDisplayed()
        rule.onNodeWithText("Interromper futuras").assertIsDisplayed()
        clickText("Alterar só este mês")
        rule.onNodeWithText("Editar previsão").assertIsDisplayed()
        rule.onNodeWithText("Alterar só este mês").assertExists()
        clickText("Cancelar")
        assertEquals(before, current())
        assertEquals(10000L, ForecastCalculator.report(current(), cycle).totalExpensesCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun dismissingMenuPreservesForecastAndStoppingFutureKeepsHistory() = runBlocking<Unit> {
        val cycle = period()
        val previous = CycleCalendar.periodContaining(cycle.start.minusDays(1), current().cycleRules)
        val next = CycleCalendar.periodContaining(cycle.endExclusive, current().cycleRules)
        app.forecasts.saveRule(ForecastRule("casa", previous.start, "Casa prevista", ForecastKind.EXPENSE,
            25000, Category.HOME, ForecastFrequency.MONTHLY, previous.start))
        rule.waitUntil(10000) { model.dataset.value?.rules?.size == 1 }
        val before = current()
        val line = ForecastCalculator.report(before, cycle).lines.single()
        val trigger = actions(line)
        scroll(trigger); clickTag(trigger)
        rule.onNodeWithText("Interromper futuras").assertIsDisplayed()
        Espresso.pressBack(); rule.waitForIdle()
        rule.onNodeWithText("Interromper futuras").assertDoesNotExist()
        assertEquals(before, current())
        assertEquals(25000L, ForecastCalculator.report(current(), cycle).totalExpensesCents)

        // The current planner stops from the selected occurrence directly from this menu.
        scroll(trigger); clickTag(trigger); clickText("Interromper futuras")
        rule.waitUntil(10000) { model.dataset.value?.rules?.any { !it.active && it.effectiveFrom == line.date } == true }
        assertEquals(25000L, ForecastCalculator.report(current(), previous).totalExpensesCents)
        assertEquals(0L, ForecastCalculator.report(current(), cycle).totalExpensesCents)
        assertEquals(0L, ForecastCalculator.report(current(), next).totalExpensesCents)
        val stopped = current()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { model.dataset.value == stopped }
        rule.onNodeWithTag("records-list").assertIsDisplayed()
        assertEquals(stopped, current())
        assertEquals(25000L, ForecastCalculator.report(current(), previous).totalExpensesCents)
        assertEquals(0L, ForecastCalculator.report(current(), next).totalExpensesCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    private fun actions(line: ForecastLine) = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
    private fun scroll(tag: String) { rule.openRendlyMonth(); rule.onNodeWithTag("records-list").performScrollToNode(hasTestTag(tag)) }
    private fun clickTag(tag: String) = rule.onNodeWithTag(tag)
        .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    private fun clickText(text: String) = rule.onNode(hasText(text) and hasClickAction())
        .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
}
