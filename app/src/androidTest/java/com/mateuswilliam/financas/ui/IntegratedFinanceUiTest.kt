package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class IntegratedFinanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() {
        runBlocking { app.repository.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }
    private fun clickTag(tag: String) = rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    private fun scrollDashboard(tag: String) = rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag(tag))

    @Test fun forecastsUseTheSameResultOnDashboardAndAnalysisWithoutTurningReferenceIncomeIntoRevenue() = runBlocking<Unit> {
        val month = YearMonth.now()
        app.repository.saveMonthlyPlan(MonthlyIncomePlan(month, 999999, 3000))
        app.repository.saveRecurring(RecurringPlan("salary", "Salário", 10000, Movement.INCOME, PaymentMethod.PIX,
            Category.SALARY, "Minha conta", month.atDay(20).toString(), 20))
        app.repository.saveRecurring(RecurringPlan("rent", "Aluguel", 5000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Minha conta", month.atDay(20).toString(), 20))
        rule.waitUntil(10000) {
            try { scrollDashboard("projected-result"); rule.onNodeWithTag("projected-result").assertTextContains("50,00", substring = true); true }
            catch (_: AssertionError) { false }
        }
        assertTrue(app.repository.exportRecords().isEmpty())
        scrollDashboard("open-planning"); clickTag("open-planning")
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag("projected-result"))
        rule.onNodeWithTag("projected-result").assertTextContains("50,00", substring = true)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun initialZeroBalanceIsExplicitAndAccountStatementCanBeOpened() {
        scrollDashboard("open-accounts"); clickTag("open-accounts")
        clickTag("new-account-balance")
        rule.onNodeWithTag("balance-account-name").performTextInput("Carteira pessoal")
        rule.onNodeWithTag("account-balance-input").performScrollTo().performTextInput("0")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        clickTag("save-account-balance")
        rule.waitUntil(10000) { runBlocking { app.repository.observeAccountBalances().first().any { it.account.label == "Carteira pessoal" && it.balanceCents == 0L } } }
        rule.onNodeWithText("Carteira pessoal").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.onNodeWithTag("records-list").assertExists()
        rule.onNodeWithText("Conta: Carteira pessoal · Limpar filtro").assertExists()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }
}
