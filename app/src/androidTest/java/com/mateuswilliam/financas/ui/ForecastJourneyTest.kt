package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** These journeys launch the real primary planner, without the retained ledger regression host. */
class ForecastJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val model get() = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
    private val today get() = LocalDate.now()
    private fun current() = runBlocking { app.forecasts.dataset.first() }
    private fun period() = CycleCalendar.periodContaining(today, current().cycleRules)
    private fun clickTag(tag: String) = rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    private fun clickText(text: String) = rule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    private fun scroll(tag: String) = rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag(tag))
    private fun waitRules(count: Int) = rule.waitUntil(10000) { model.dataset.value?.rules?.size == count }

    @Before fun reset() {
        runBlocking { app.repository.clear(); app.forecasts.initialize(); app.forecasts.setCycleDay(5, today) }
        app.settings.finishIntro(); model.refresh()
        rule.waitUntil(10000) { model.dataset.value?.let { it.rules.isEmpty() && it.cycleRules.lastOrNull()?.day == 5 } == true }
        rule.waitForIdle()
    }
    private suspend fun seed(includeSalary: Boolean = true) {
        val date = period().start
        if (includeSalary) app.forecasts.saveRule(ForecastRule("salary", date, "Salário", ForecastKind.INCOME, 300000,
            Category.SALARY, ForecastFrequency.MONTHLY, date))
        app.forecasts.saveRule(ForecastRule("bills", date, "Contas mensais", ForecastKind.EXPENSE, 120000,
            Category.HOME, ForecastFrequency.MONTHLY, date))
        app.forecasts.saveRule(ForecastRule("purchase", date, "Compra em duas parcelas", ForecastKind.EXPENSE, 80000,
            Category.OTHER, ForecastFrequency.INSTALLMENTS, date, installmentCount = 2))
        app.forecasts.saveRule(ForecastRule("food", date, "Consumo estimado", ForecastKind.EXPENSE, 60000,
            Category.FOOD, ForecastFrequency.MONTHLY, date, cycleAnchored = true, group = ForecastGroup.VARIABLE, estimated = true))
    }

    @Test fun salaryRegisteredOnceFeedsAllExpensesAndOneCycleEditPreservesFuture() {
        clickTag("forecast-add-income")
        rule.onNodeWithTag("forecast-title").performTextInput("Salário")
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextInput("300000")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        waitRules(1); rule.dismissFeedback("Previsão salva")
        val salary = current().rules.single()
        assertEquals(ForecastFrequency.MONTHLY, salary.frequency)
        assertEquals("", salary.account)
        runBlocking { seed(includeSalary = false) }; waitRules(4)
        rule.waitUntil(10000) {
            try { scroll("forecast-summary"); rule.onNodeWithText("73,33% da renda em gastos").assertExists(); true }
            catch (_: AssertionError) { false }
        }
        val base = ForecastCalculator.report(current(), period())
        assertEquals(220000L, base.totalExpensesCents); assertEquals(80000L, base.marginCents)
        val line = base.lines.single { it.kind == ForecastKind.INCOME }
        val actions = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
        scroll(actions); clickTag(actions); clickText("Editar")
        rule.onNodeWithText("Só neste ciclo").assertExists()
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("285000")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        rule.waitUntil(10000) { model.dataset.value?.overrides?.any { it.amountCents == 285000L } == true }
        val changed = ForecastCalculator.report(current(), period())
        assertEquals(285000L, changed.incomeCents); assertEquals(65000L, changed.marginCents)
        val next = CycleCalendar.periodContaining(period().endExclusive, current().cycleRules)
        assertEquals(300000L, ForecastCalculator.report(current(), next).incomeCents)
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
        assertTrue(runBlocking { app.database.financeDao().allMonthlyPlans().isEmpty() })
        rule.onNodeWithText("Registrar recebimento").assertDoesNotExist()
        rule.onNodeWithText("Marcar como pago").assertDoesNotExist()
    }

    @Test fun closedSurplusAllocationIsExplicitPersistsAndDoesNotChangeExpenseRatio() = runBlocking<Unit> {
        val old = CycleCalendar.periodContaining(period().start.minusDays(1), current().cycleRules)
        app.forecasts.saveRule(ForecastRule("old-income", old.start, "Renda anterior", ForecastKind.INCOME, 300000, Category.SALARY, ForecastFrequency.ONCE, old.start))
        app.forecasts.saveRule(ForecastRule("old-cost", old.start, "Gastos anteriores", ForecastKind.EXPENSE, 220000, Category.OTHER, ForecastFrequency.ONCE, old.start))
        app.forecasts.addReserveEvent(ReserveEventKind.SET, 200000, "Reserva inicial")
        waitRules(2); clickTag("forecast-cycle-previous")
        scroll("forecast-allocate-surplus"); clickTag("forecast-allocate-surplus")
        rule.onNodeWithTag("reserve-amount").performScrollTo().performTextInput("50000")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("reserve-save")
        rule.waitUntil(10000) { model.dataset.value?.reserveEvents?.size == 2 }
        val report = ForecastCalculator.report(current(), old)
        assertEquals(250000L, report.reserveBalanceCents)
        assertEquals(50000L, report.allocatedCents); assertEquals(30000L, report.unallocatedCents)
        assertEquals(220000L, report.totalExpensesCents); assertEquals("73.33", report.expensePercentage?.toPlainString())
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
        assertEquals(report, ForecastCalculator.report(current(), old))
        clickText("Reserva")
        rule.onNodeWithTag("reserve-balance").assertTextContains("2.500,00", substring = true)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun simulationHasNoWritesUntilAdoptedAndInstallmentsEnd() = runBlocking<Unit> {
        seed(); waitRules(4)
        val before = current()
        clickText("Projeções"); clickTag("forecast-simulate")
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextInput("60000")
        rule.onNodeWithTag("forecast-installment-count").performScrollTo().performTextInput("3")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        rule.onNodeWithText("Simulação · ainda fora do orçamento").assertExists()
        assertEquals(before, current())
        clickTag("forecast-adopt-simulation"); waitRules(5)
        val report = ForecastCalculator.report(current(), period())
        assertEquals(240000L, report.totalExpensesCents)
        assertTrue(app.repository.exportRecords().isEmpty())
        var future = period()
        repeat(3) { future = CycleCalendar.periodContaining(future.endExclusive, current().cycleRules) }
        assertEquals(180000L, ForecastCalculator.report(current(), future).totalExpensesCents)
    }

    @Test fun cycleChangePreviewAndPersistedTransitionsUseTheSameBoundaries() {
        val previous = period()
        clickTag("forecast-cycle-configure")
        clickText("Dia 5"); rule.onNodeWithText("Dia 10").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        clickTag("cycle-save")
        rule.waitUntil(10000) { model.dataset.value?.cycleRules?.lastOrNull()?.day == 10 }
        assertEquals(previous, period())
        clickTag("forecast-cycle-next")
        val transition = CycleCalendar.periodContaining(previous.endExclusive, current().cycleRules)
        rule.onNodeWithTag("forecast-cycle-dates").assertTextContains(transition.start.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), substring = true)
        assertEquals(previous.endExclusive, transition.start)
        assertEquals(10, transition.endExclusive.dayOfMonth)
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }
}
