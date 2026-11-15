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
            app.forecasts.saveRule(ForecastRule("salary", now, "Salário", ForecastKind.INCOME, 300000, Category.SALARY, ForecastFrequency.MONTHLY, now))
            app.repository.save(Record("haircut", 120000, Movement.EXPENSE, PaymentMethod.PIX, Category.SERVICES,
                "Carteira", "Despesa fictícia", System.currentTimeMillis()), null)
            app.forecasts.saveRule(ForecastRule("bill", now, "Internet", ForecastKind.EXPENSE, 60000,
                Category.HOME, ForecastFrequency.ONCE, now))
        }
        rule.openRendlyMonth()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("month-expenses", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("month-expenses") and hasText("1.200,00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("month-expenses", useUnmergedTree = true).assertTextContains("1.200,00", substring = true)
        rule.onNodeWithTag("month-income", useUnmergedTree = true).assertTextContains("0,00", substring = true)
        assertEquals(1, runBlocking { app.repository.exportRecords().size })
        rule.newRendlyRecord(com.mateuswilliam.financas.core.Movement.EXPENSE)
        rule.onNodeWithText("Registrar despesa").assertExists()
        rule.onNodeWithText("Confirmar pagamento").assertDoesNotExist()
    }

    @Test fun capturedExpenseEntersTheLedgerOnlyAfterExplicitConfirmation() {
        val captured = runBlocking {
            app.repository.capture(NotificationInput("test.bank", "test-purchase", "Banco de teste",
                "Compra aprovada", "Compra aprovada no cartão de R$ 35,90", System.currentTimeMillis()), simulation = true)
        }!!
        rule.openRendlyReview()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("review-${captured.id}").fetchSemanticsNodes().isNotEmpty() }
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
        rule.openRendlyReview()
        rule.onNodeWithText("Caixa de revisão").assertExists()
        rule.onNodeWithText("Captura desativada").assertExists()
        rule.onNodeWithText("Configurar captura").performClick()
        rule.onNodeWithText("Capturar notificações").assertExists()
    }
}
