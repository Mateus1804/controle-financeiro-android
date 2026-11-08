package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ForecastRemovalUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val repo get() = app.repository
    private val month = YearMonth.now()

    @Before fun reset() {
        runBlocking { repo.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun excludingAnInstallmentUpdatesOnlyTheSelectedForecast() = runBlocking<Unit> {
        repo.saveMonthlyPlan(MonthlyIncomePlan(month, 100000))
        assertTrue(repo.createPurchase(InstallmentPurchase("compra-ficticia", "Compra fictícia", "Carteira",
            Category.OTHER, 20000, 2, month.atDay(12))))
        openPlanning()
        assertReport("10% da renda comprometidos; maior gasto: Compra fictícia · 1/2.")
        val action = "exclude-installment:compra-ficticia:1"
        tap(action)
        awaitText("Excluir esta parcela prevista?")
        rule.onNodeWithTag("keep-installment-forecast").performClick()
        assertEquals(10000L, repo.commitmentReport(month).committedCents)
        tap(action)
        awaitText("Excluir esta parcela prevista?")
        rule.onNodeWithText("Excluir parcela", substring = false).performClick()
        assertReport("0% da renda comprometidos; nenhum compromisso.")
        awaitPlanningText("Sem parcelas neste mês")
        rule.onNodeWithTag(action).assertDoesNotExist()
        assertEquals(10000L, repo.commitmentReport(month.plusMonths(1)).committedCents)
        assertTrue(repo.exportRecords().isEmpty())
    }

    @Test fun excludingARecurringForecastRemovesItsCardAndItsAmountFromPlanning() = runBlocking<Unit> {
        val plan = RecurringPlan("conta-ficticia", "Conta fictícia", 5000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Carteira", month.atDay(8).toString(), 8)
        repo.saveRecurring(plan)
        repo.setFixedRecurring(plan.id, month)
        repo.saveMonthlyPlan(MonthlyIncomePlan(month, 100000))
        openPlanning()
        assertReport("5% da renda comprometidos; maior gasto: Conta fictícia.")
        tap("exclude-recurring-${plan.id}")
        awaitText("Excluir previsão mensal?")
        rule.onNodeWithTag("confirm-exclude-recurring").performClick()
        assertReport("0% da renda comprometidos; nenhum compromisso.")
        awaitPlanningText("Nenhuma recorrência cadastrada")
        assertTrue(repo.exportRecords().isEmpty())
    }

    private fun openPlanning() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejar limites e recorrências"))
        rule.onNodeWithText("Planejar limites e recorrências").performClick()
    }

    private fun tap(tag: String) {
        rule.performPlanningAction(tag)
    }

    private fun awaitText(text: String) {
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitPlanningText(text: String) {
        // O relatório e a lista recebem atualizações independentes depois da exclusão.
        rule.waitUntil(5000) {
            try {
                rule.onNodeWithTag("planning-list").performScrollToNode(hasText(text))
                true
            } catch (_: AssertionError) {
                false
            }
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertReport(description: String) {
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag("commitment-card"))
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("commitment-donut").assertContentDescriptionEquals(description)
    }
}
