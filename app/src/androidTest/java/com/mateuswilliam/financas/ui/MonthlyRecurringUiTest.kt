package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

class MonthlyRecurringUiTest {
    @get:Rule val rule = createLegacyFinanceRule()
    private val app get() = rule.activity.application as FinanceApplication
    private val month = YearMonth.now()
    private val plan get() = RecurringPlan("escopo-ui", "Conta mensal", 5000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Carteira", month.atDay(12).toString(), 12)

    @Before fun reset() {
        runBlocking {
            app.repository.clear()
            app.repository.saveRecurring(plan)
            app.repository.setFixedRecurring(plan.id, month)
        }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun editingThisMonthSurvivesRecreationAndKeepsTheFollowingMonth() = runBlocking<Unit> {
        openPlanning()
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("edit-month-${plan.id}").performClick()
        rule.onNodeWithText("Alterar só este mês").assertIsDisplayed()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("7500")
        rule.onNodeWithTag("recurring-title").performScrollTo().performTextReplacement("Conta ajustada só neste mês")
        rule.onNodeWithTag("recurring-title").performImeAction()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("recurring-title").assertTextContains("Conta ajustada só neste mês")
        rule.onNodeWithText("Salvar só este mês").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.commitmentReport(month).committedCents == 7500L } }
        assertEquals(5000L, app.repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(5000L, app.repository.recurring.first().single().amountCents)
        assertTrue(app.repository.exportRecords().isEmpty())
        changeMonth("Próximo mês")
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Conta mensal"))
        rule.onNodeWithText("Conta ajustada só neste mês").assertDoesNotExist()
        changeMonth("Mês anterior")
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Conta ajustada só neste mês"))
        rule.onNodeWithText("Conta ajustada só neste mês").assertIsDisplayed()
    }

    @Test fun recordedMonthKeepsItsStatusAndCannotBeConfirmedAgain() = runBlocking<Unit> {
        openPlanning()
        rule.performPlanningAction("confirm-recurring-${plan.id}")
        rule.onNodeWithText("Descrição").performScrollTo().performImeAction()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 1 } }
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Realizada · Recorrente mensal · Fixa"))
        rule.onNodeWithTag("confirm-recurring-${plan.id}").assertDoesNotExist()
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("edit-month-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("skip-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("active-recurring-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("edit-recurring-${plan.id}").assertIsDisplayed()
        assertEquals(1, app.repository.exportRecords().size)
    }

    @Test fun skippedMonthDoesNotOfferAnActivityToggle() = runBlocking<Unit> {
        val expected = app.repository.observeRecurringMonths(month).first().single()
        assertTrue(app.repository.skipRecurringMonth(expected))
        openPlanning()
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("active-recurring-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("edit-month-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("skip-${plan.id}").assertDoesNotExist()
        rule.onNodeWithTag("edit-recurring-${plan.id}").assertIsDisplayed()
        rule.onNodeWithTag("exclude-recurring-${plan.id}").assertIsDisplayed()
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun pausingAnEarlierMonthPreservesItsOverrideAndLaterConfiguration() = runBlocking<Unit> {
        val next = month.plusMonths(1)
        val future = plan.copy(title = "Configuração futura", amountCents = 9000, bank = "Conta futura",
            nextDate = next.atDay(23).toString(), dayOfMonth = 23, active = false)
        app.repository.saveRecurringFromMonth(future, next)
        val current = app.repository.observeRecurringMonths(month).first().single()
        assertTrue(app.repository.saveRecurringMonth(current,
            current.plan.copy(title = "Ajuste deste mês", amountCents = 6250, nextDate = month.atDay(14).toString())))
        openPlanning()
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("active-recurring-${plan.id}").assertTextEquals("Pausar desta e futuras").performClick()
        rule.waitUntil(5000) { runBlocking { !app.repository.observeRecurringMonths(month).first().single().plan.active } }
        val paused = app.repository.observeRecurringMonths(month).first().single().plan
        assertEquals("Ajuste deste mês", paused.title)
        assertEquals(6250L, paused.amountCents)
        assertEquals(month.atDay(14).toString(), paused.nextDate)
        val later = app.repository.observeRecurringMonths(next).first().single().plan
        assertEquals("Configuração futura", later.title)
        assertEquals(9000L, later.amountCents)
        assertEquals("Conta futura", later.bank)
        assertEquals(23, later.dayOfMonth)
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("active-recurring-${plan.id}").assertTextEquals("Retomar desta e futuras").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.observeRecurringMonths(month).first().single().plan.active } }
        assertEquals(6250L, app.repository.observeRecurringMonths(month).first().single().plan.amountCents)
        assertEquals(9000L, app.repository.observeRecurringMonths(next).first().single().plan.amountCents)
    }

    @Test fun configurationEditorLoadsTheSelectedRuleAndReplacesFuturePendingRules() = runBlocking<Unit> {
        val next = month.plusMonths(1)
        app.repository.saveRecurringFromMonth(plan.copy(title = "Configuração futura", amountCents = 9000,
            bank = "Conta futura", nextDate = next.atDay(23).toString(), dayOfMonth = 23), next)
        val current = app.repository.observeRecurringMonths(month).first().single()
        assertTrue(app.repository.saveRecurringMonth(current, current.plan.copy(title = "Ajuste deste mês", amountCents = 6250)))
        openPlanning()
        rule.openPlanningActions("recurring-${plan.id}")
        rule.onNodeWithTag("edit-recurring-${plan.id}").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("recurring-title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("recurring-title").assertTextContains("Conta mensal")
        val displayedAmount = rule.onNodeWithTag("amount-input").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(5000L, MoneyInput.normalize(displayedAmount)?.toLongOrNull())
        rule.onNodeWithText("Salvar recorrência").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Salvar recorrência").fetchSemanticsNodes().isEmpty() }
        val selectedRule = requireNotNull(app.repository.recurringConfiguration(plan.id, month))
        assertEquals("Conta mensal", selectedRule.title)
        assertEquals(5000L, selectedRule.amountCents)
        assertEquals(12, selectedRule.dayOfMonth)
        val laterRule = requireNotNull(app.repository.recurringConfiguration(plan.id, next))
        assertEquals("Conta mensal", laterRule.title)
        assertEquals(5000L, laterRule.amountCents)
        assertEquals(12, laterRule.dayOfMonth)
        assertEquals(5000L, app.repository.observeRecurringMonths(month).first().single().plan.amountCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    private fun openPlanning() {
        rule.showMovements()
        rule.scrollPlanningToTag("actions-recurring-${plan.id}")
    }

    private fun changeMonth(description: String) {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).performClick()
    }
}
