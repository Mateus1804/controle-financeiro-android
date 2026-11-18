package com.mateuswilliam.financas.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class IntegratedFinanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() = rule.resetReviewBackupMain()

    @Test fun projectionsUseForecastIncomeWithoutTurningMonthlyReferenceIntoRevenue() = runBlocking<Unit> {
        val date = LocalDate.now()
        val salary = ForecastRule("salary", date, "Salário fictício", ForecastKind.INCOME,
            10000, Category.SALARY, ForecastFrequency.MONTHLY, date)
        val rent = ForecastRule("rent", date, "Aluguel fictício", ForecastKind.EXPENSE,
            5000, Category.HOME, ForecastFrequency.MONTHLY, date)
        app.forecasts.saveRule(salary)
        app.forecasts.saveRule(rent)
        val model = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
        model.refresh()
        rule.waitUntil(10000) { model.dataset.value?.rules?.size == 2 }
        val data = app.forecasts.dataset.first()
        val period = CycleCalendar.periodContaining(date, data.cycleRules)
        val report = ForecastCalculator.report(data, period)
        assertEquals(10000L, report.incomeCents)
        assertEquals(5000L, report.totalExpensesCents)
        assertEquals(5000L, report.marginCents)
        assertEquals(setOf(salary.itemId, rent.itemId), data.rules.map { it.itemId }.toSet())
        assertTrue(app.repository.exportRecords().isEmpty())
        val lineTag = "forecast-actions:rent:${report.lines.single { it.itemId == rent.itemId }.occurrenceKey}"
        rule.openRendlyMonth()
        rule.onNodeWithTag("records-list").performScrollToNode(hasTestTag(lineTag))
        rule.onNodeWithText("Aluguel fictício").assertExists()
        rule.openRendlyProjections()
        val projection = "projection-margin-${period.start}"
        rule.onNodeWithTag("forecast-projections").performScrollToNode(hasTestTag(projection))
        rule.onNodeWithTag(projection).assertTextContains(Money.format(report.marginCents))
        assertTrue(app.repository.exportRecords().isEmpty())
    }
}

/** Exercita o cadastro pelo caminho oferecido na tela principal. */
class MainAccountsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() = rule.resetReviewBackupMain()

    @Test fun explicitZeroBalancePersistsWithoutRevenueAndStatementActionSelectsTheAccount() {
        rule.openRendlySalary()
        rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag("payday-accounts"))
        rule.onNodeWithTag("payday-accounts").performClick()
        rule.onNodeWithTag("new-account-balance").performClick()
        rule.onNodeWithTag("balance-account-name").performTextInput("Carteira pessoal")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("save-account-balance").performClick()
        rule.onNodeWithText("Informe o saldo, inclusive se for zero.").assertExists()
        assertTrue(runBlocking { app.repository.observeAccountBalances().first().isEmpty() })
        rule.onNodeWithTag("account-balance-input").performScrollTo().performTextInput("0")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("save-account-balance").performClick()
        rule.waitUntil(10000) { runBlocking {
            app.repository.observeAccountBalances().first().any { it.account.label == "Carteira pessoal" && it.balanceCents == 0L }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("save-account-balance").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Carteira pessoal").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("Conta: Carteira pessoal · Limpar filtro"))
        rule.onNodeWithText("Conta: Carteira pessoal · Limpar filtro").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("Conta: Carteira pessoal · Limpar filtro"))
        rule.onNodeWithText("Conta: Carteira pessoal · Limpar filtro").assertExists()
        val balance = runBlocking { app.repository.observeAccountBalances().first().single() }
        assertEquals(0L, balance.balanceCents)
        assertEquals(0L, balance.reference!!.balanceCents)
        assertEquals("Saldo inicial", balance.reference!!.reason)
        runBlocking { app.repository.setAccountArchived(balance.account.key, true) }
        rule.openRendlyMonth()
        rule.openRendlySettings()
        rule.onNodeWithTag("settings-accounts").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Carteira pessoal · Arquivada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Carteira pessoal · Arquivada").performClick()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("Conta: Carteira pessoal · Limpar filtro"))
        rule.onNodeWithText("Conta: Carteira pessoal · Limpar filtro").assertExists()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }
}
