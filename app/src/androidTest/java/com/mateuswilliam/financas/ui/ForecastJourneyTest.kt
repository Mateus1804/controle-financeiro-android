package com.mateuswilliam.financas.ui

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** These journeys launch the real primary planner, without the retained ledger regression host. */
class ForecastJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val model get() = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
    private val today get() = LocalDate.now()
    private fun current() = runBlocking { app.forecasts.dataset.first() }
    private fun period() = CycleCalendar.periodContaining(today, current().cycleRules)
    private fun clickTag(tag: String) {
        if (tag.startsWith("forecast-cycle-")) scroll(tag)
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    private fun clickText(text: String) = rule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    private fun scroll(tag: String) {
        if (tag.startsWith("forecast-actions:")) {
            rule.openRendlyMonth(); rule.scrollToLoadedItem("records-list", hasTestTag(tag))
        } else {
            rule.openRendlyPlanner(); rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag(tag))
        }
    }
    private fun waitRules(count: Int) = rule.waitUntil(10000) { model.dataset.value?.rules?.size == count }

    @Before fun reset() {
        runBlocking { app.repository.clear(); app.forecasts.setCycleDay(5, today) }
        app.settings.finishIntro(); app.settings.dismissNamePrompt(); model.refresh()
        rule.waitUntil(10000) { model.dataset.value?.let { it.rules.isEmpty() && it.cycleRules.lastOrNull()?.day == 5 } == true }
        rule.openRendlyPlanner()
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

    @Test fun openPlannerUpdatesTheBalanceBeforeASalaryBeyondTwoMonths() {
        val finance = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]
        val bank = "Conta principal"
        val zone = ZoneId.systemDefault()
        runBlocking {
            app.repository.configureAccountBalance(bank, 100000,
                today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(), "Saldo inicial")
            val salaryDate = today.plusDays(75)
            app.forecasts.saveRule(ForecastRule("salary", salaryDate, "Salário", ForecastKind.INCOME,
                300000, Category.SALARY, ForecastFrequency.ONCE, salaryDate))
        }
        waitRules(1)
        rule.waitUntil(10000) { finance.state.value.accountBalances.singleOrNull()?.balanceCents == 100000L }
        rule.scrollToLoadedItem("forecast-dashboard", hasTestTag("payday-total") and hasText(Money.format(100000)))
        val effectiveAt = System.currentTimeMillis() + 3000
        runBlocking {
            app.repository.save(Record("expense-after-opening", 20000, Movement.EXPENSE, PaymentMethod.PIX,
                Category.OTHER, bank, "Conta agendada", effectiveAt), null)
        }
        rule.waitUntil(10000) {
            finance.state.value.accountBalances.singleOrNull()?.balanceCents == 80000L
        }
        rule.scrollToLoadedItem("forecast-dashboard", hasTestTag("payday-total") and hasText(Money.format(80000)))
    }

    @Test fun dueDatesOnlyOpenWhenThereIsAnUpcomingExpenseWithADueDate() {
        runBlocking {
            app.forecasts.saveRule(ForecastRule("salary", today, "Salário", ForecastKind.INCOME,
                300000, Category.SALARY, ForecastFrequency.ONCE, today))
        }
        waitRules(1)
        val label = "Próximos vencimentos · consultar previsão"
        rule.scrollToLoadedItem("forecast-dashboard", hasTestTag("central-projection"))
        rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasText(label))
        rule.onNodeWithText(label).assertIsNotEnabled()
        runBlocking {
            app.forecasts.saveRule(ForecastRule("bill", today, "Conta a vencer", ForecastKind.EXPENSE,
                5000, Category.HOME, ForecastFrequency.ONCE, today))
        }
        waitRules(2)
        rule.scrollToLoadedItem("forecast-dashboard", hasText(label) and isEnabled())
        rule.onNodeWithText(label).assertIsEnabled().performClick()
        rule.onNodeWithText("Conta a vencer").assertIsDisplayed()
        rule.onNodeWithText("Salário").assertDoesNotExist()
    }

    @Test fun monthKeepsRecordedTotalsAndShowsForecastLoadFailure() {
        rule.openRendlyMonth()
        val loadError = "Não foi possível abrir o planejamento. Tente novamente."
        rule.runOnIdle {
            model.dataset.value = null
            model.loadError.value = loadError
        }
        rule.onNodeWithTag("month-net").assertExists()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText(loadError))
        rule.onNodeWithText(loadError).assertIsDisplayed()
        rule.onNodeWithText("Nenhum lançamento neste período.", substring = true).assertDoesNotExist()
    }

    @Test fun csvRequiresAReportAndKeepsThePreparedContentAcrossRecreation() {
        val report = ForecastCalculator.report(ForecastDataset(rules = listOf(
            ForecastRule("csv-bill", today, "Conta original do CSV", ForecastKind.EXPENSE, 5000,
                Category.HOME, ForecastFrequency.ONCE, today)
        )), period())
        val label = "Exportar previsões em CSV"
        rule.openRendlySettings()
        rule.scrollToLoadedItem("settings-list", hasText(label) and isEnabled())
        rule.onNodeWithText(label).assertIsEnabled()
        val loaded = model.dataset.value
        rule.runOnIdle { model.dataset.value = null }
        rule.onNodeWithText(label).assertIsNotEnabled()
        val exportKey = "csv-test-export"
        rule.runOnIdle {
            model.dataset.value = loaded
            ViewModelProvider(rule.activity)[exportKey, ForecastViewModel::class.java].prepareCsv(report)
        }
        val exporter = ViewModelProvider(rule.activity)[exportKey, ForecastViewModel::class.java]
        rule.waitUntil(10000) { exporter.csvDestination.value != null && !exporter.busy.value }
        rule.activityRule.scenario.recreate()
        val file = File.createTempFile("previsoes-teste", ".csv", app.cacheDir)
        try {
            rule.runOnIdle {
                val retained = ViewModelProvider(rule.activity)[exportKey, ForecastViewModel::class.java]
                assertEquals("previsoes-${report.period.start}.csv", retained.takeCsvDestination())
                retained.busy.value = true
                retained.writeCsv(Uri.fromFile(file))
            }
            rule.waitUntil(10000) { exporter.message.value == "Previsões do ciclo exportadas." }
            assertTrue(file.readText().contains("Conta original do CSV"))
        } finally { file.delete() }
    }

    @Test fun salaryRegisteredOnceFeedsAllExpensesAndOneCycleEditPreservesFuture() {
        rule.newRendlyForecast(Movement.INCOME, "Salário", "300000", category = Category.SALARY)
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        waitRules(1); rule.dismissFeedback("Previsão salva")
        val salary = current().rules.single()
        assertEquals(ForecastFrequency.MONTHLY, salary.frequency)
        assertEquals(Category.SALARY, salary.category)
        assertTrue(salary.account.isNotBlank())
        runBlocking { seed(includeSalary = false) }; waitRules(4)
        rule.openRendlyPlanner()
        rule.onNodeWithTag("payday-budget").assertExists()
        rule.openRendlyPlanner()
        val base = ForecastCalculator.report(current(), period())
        assertEquals(220000L, base.totalExpensesCents); assertEquals(80000L, base.marginCents)
        val line = base.lines.single { it.kind == ForecastKind.INCOME }
        val actions = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
        scroll(actions); clickTag(actions); clickText("Alterar só este mês")
        rule.onNodeWithText("Alterar só este mês").assertExists()
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("285000")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        rule.waitUntil(10000) { model.dataset.value?.overrides?.any { it.amountCents == 285000L } == true }
        val changed = ForecastCalculator.report(current(), period())
        assertEquals(285000L, changed.incomeCents); assertEquals(65000L, changed.marginCents)
        val next = CycleCalendar.periodContaining(period().endExclusive, current().cycleRules)
        assertEquals(300000L, ForecastCalculator.report(current(), next).incomeCents)
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
        rule.onNodeWithText("Registrar recebimento").assertDoesNotExist()
        rule.onNodeWithText("Marcar como pago").assertDoesNotExist()
    }

    @Test fun surplusTransferIsExplicitPersistsAndDoesNotChangePeriodResult() = runBlocking<Unit> {
        val old = CycleCalendar.periodContaining(period().start.minusDays(1), current().cycleRules)
        val at = old.start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        app.repository.configureAccountBalance("Conta principal", 0, at, "Saldo inicial")
        app.repository.configureAccountBalance("Reserva de emergência", 200000, at, "Saldo inicial")
        app.repository.save(Record("old-income", 300000, Movement.INCOME, PaymentMethod.PIX, Category.SALARY,
            "Conta principal", "Renda anterior", at), null)
        app.repository.save(Record("old-cost", 220000, Movement.EXPENSE, PaymentMethod.PIX, Category.OTHER,
            "Conta principal", "Gastos anteriores", at), null)
        rule.openRendlyReserve()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("reserve-add").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("reserve-add").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("50000")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(hasText("Salvar lançamento") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 3 } }
        val records = app.repository.exportRecords()
        val transfer = records.single { it.movement == Movement.TRANSFER }
        assertEquals("Conta principal", transfer.bank)
        assertEquals("Reserva de emergência", transfer.destination)
        assertEquals(50000L, transfer.amountCents)
        val oldResult = Ledger.period(records, old, java.time.ZoneId.systemDefault())
        assertEquals(220000L, oldResult.expenses)
        assertEquals(80000L, oldResult.result)
        val balances = app.repository.observeAccountBalances().first()
        assertEquals(250000L, balances.single { it.account.label == "Reserva de emergência" }.balanceCents)
        assertEquals(30000L, balances.single { it.account.label == "Conta principal" }.balanceCents)
        assertEquals(280000L, balances.sumOf { it.balanceCents ?: 0 })
        assertTrue(current().reserveEvents.isEmpty())
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
        rule.openRendlyReserve()
        rule.onNodeWithTag("reserve-balance").assertTextContains("2.500,00", substring = true)
        assertEquals(records, app.repository.exportRecords())
    }

    @Test fun simulationHasNoWritesUntilAdoptedAndInstallmentsEnd() = runBlocking<Unit> {
        seed(); waitRules(4)
        val before = current()
        rule.openRendlyProjections(); clickTag("forecast-simulate")
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextInput("60000")
        rule.onNodeWithTag("forecast-installment-count").performScrollTo().performTextInput("3")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); clickTag("forecast-save")
        rule.onNodeWithText("Simulação · ainda fora do orçamento").assertExists()
        assertEquals(before, current())
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
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
