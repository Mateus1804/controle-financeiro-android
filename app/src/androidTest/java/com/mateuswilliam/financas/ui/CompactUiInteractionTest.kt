package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CompactUiInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val month = YearMonth.now()

    @Before fun reset() {
        runBlocking { app.repository.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun installmentSecondaryActionsOpenFromAnAccessibleContextMenu() = runBlocking<Unit> {
        app.repository.createPurchase(InstallmentPurchase("compacta", "Compra compacta", "Carteira",
            Category.OTHER, 20000, 2, month.atDay(12)))
        openPlanning()
        val trigger = "actions-installment:compacta:1"
        rule.scrollPlanningToTag(trigger)
        rule.onNodeWithTag("exclude-installment:compacta:1").assertDoesNotExist()
        rule.onNodeWithTag(trigger).assertContentDescriptionEquals("Mais ações de Compra compacta")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("exclude-installment:compacta:1").assertIsDisplayed()
        rule.onNodeWithTag("edit-installment:compacta:1").performClick()
        rule.onNodeWithText("Editar compra parcelada").assertIsDisplayed()
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun removingABudgetRequiresConfirmationAndCancelKeepsTheLimit() = runBlocking<Unit> {
        app.repository.saveBudget(MonthlyBudget(month.toString(), Category.HOME, 25000))
        openPlanning()
        openBudgetMenu()
        rule.onNodeWithText("Remover limite").performClick()
        rule.onNodeWithText("Remover limite mensal?").assertIsDisplayed()
        rule.onNodeWithTag("keep-budget").performClick()
        assertEquals(25000L, app.repository.budgets.first().single().limitCents)
        openBudgetMenu()
        rule.onNodeWithText("Remover limite").performClick()
        rule.onNodeWithTag("confirm-remove-budget").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.budgets.first().isEmpty() } }
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    private fun openPlanning() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejamento e análises"))
        rule.onNodeWithText("Planejamento e análises").performClick()
    }

    private fun openBudgetMenu() {
        val tag = "actions-budget-${Category.HOME.name}"
        rule.scrollAnalysisTo(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
}
