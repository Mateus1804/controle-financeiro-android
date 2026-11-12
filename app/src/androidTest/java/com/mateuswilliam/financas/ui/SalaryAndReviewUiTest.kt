package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SalaryAndReviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Before fun clear() {
        runBlocking { app.repository.clear() }
        app.settings.finishIntro()
        app.settings.dismissNamePrompt()
    }

    @Test fun actualExpenseAndForecastDoNotShareTheLedger() {
        runBlocking {
            val now = LocalDate.now()
            app.repository.saveMonthlyPlan(MonthlyIncomePlan(YearMonth.from(now), 300000))
            app.repository.save(Record("haircut", 120000, Movement.EXPENSE, PaymentMethod.PIX, Category.SERVICES,
                "Carteira", "Despesa fictícia", System.currentTimeMillis()), null)
            app.forecasts.saveRule(ForecastRule("bill", now, "Internet", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.ONCE, now))
        }
        rule.onNodeWithTag("forecast-settings").performClick()
        rule.onNodeWithTag("forecast-settings").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Salário utilizado: 40,00%").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Resultado do mês").assertExists()
        rule.onNodeWithTag("salary-remaining", useUnmergedTree = true).assertTextContains("1.800,00", substring = true)
        rule.onNodeWithTag("salary-projected", useUnmergedTree = true).assertTextContains("1.200,00", substring = true)
        assertEquals(1, runBlocking { app.repository.exportRecords().size })
        rule.onNodeWithTag("add-realized-expense").performClick()
        rule.onNodeWithText("Registrar despesa").assertExists()
        rule.onNodeWithText("Confirmar pagamento").assertDoesNotExist()
    }

    @Test fun capturedExpenseEntersTheLedgerOnlyAfterExplicitConfirmation() {
        val captured = runBlocking {
            app.repository.capture(NotificationInput("test.bank", "test-purchase", "Banco de teste",
                "Compra aprovada", "Compra aprovada no cartão de R$ 35,90", System.currentTimeMillis()), simulation = true)
        }!!
        rule.waitUntil(10000) { rule.onAllNodesWithText("Revisar notificações (1)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Revisar notificações (1)").performClick()
        rule.onNodeWithTag("review-${captured.id}").performClick()
        rule.onNodeWithText("Conferir movimentação").assertExists()
        assertEquals(0, runBlocking { app.repository.exportRecords().size })
        rule.onNodeWithText("Confirmar lançamento").performClick()
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val saved = runBlocking { app.repository.exportRecords().single() }
        assertEquals(3590L, saved.amountCents)
        assertEquals(Movement.EXPENSE, saved.movement)
    }

    @Test fun reviewIsReachableWithCaptureOff() {
        rule.onNodeWithTag("dashboard-notification-review").performClick()
        rule.onNodeWithText("Caixa de revisão").assertExists()
        rule.onNodeWithText("Captura desativada").assertExists()
        rule.onNodeWithText("Configurar captura").performClick()
        rule.onNodeWithText("Capturar notificações").assertExists()
    }
}
