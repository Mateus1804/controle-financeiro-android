package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CommitmentEntryUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val dao get() = app.database.financeDao()

    @Before fun reset() {
        runBlocking { app.repository.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun ongoingPurchaseCreatesOnlyFourOpenMonthsAndRegistersOneWhenConfirmed() = runBlocking<Unit> {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.chooseInstallments()
        type("installment-count", "6")
        type("installments-paid", "2")
        type("purchase-total", "60001")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Notebook fictício")
        rule.onNodeWithText("Descrição").performImeAction()
        waitForKeyboard()
        assertTrue(dao.allCommitments().isEmpty())
        rule.onNodeWithText("Salvar compra parcelada").performClick()
        rule.waitUntil(5000) { runBlocking { dao.allCommitments().size == 4 } }

        val obligations = dao.allCommitments()
        assertEquals(listOf(3, 4, 5, 6), obligations.map { it.installmentNumber })
        assertEquals(listOf(10000L, 10000L, 10000L, 10000L), obligations.map { it.amountCents })
        assertEquals(today.toString(), obligations.first().dueDate)
        assertEquals(month.plusMonths(3), YearMonth.from(LocalDate.parse(obligations.last().dueDate)))
        assertTrue(app.repository.exportRecords().isEmpty())

        rule.dismissFeedback("Compra salva.")
        openPlanning()
        val first = obligations.first().id
        scrollPlanningTo("movement-installment:$first")
        rule.onNodeWithTag("movement-installment:$first").assertIsDisplayed()
        rule.performPlanningAction("confirm-$first")
        rule.onNodeWithText("Conferir parcela").assertExists()
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 1 } }
        assertEquals(10000L, app.repository.exportRecords().single().amountCents)
        assertEquals(10000L, app.repository.commitmentReport(month).committedCents)
        assertEquals(1, app.repository.commitmentReport(month).entries.size)
        assertNotNull(dao.commitmentById(first)?.recordId)
    }

    @Test fun notificationTotalCanBeRecalculatedFromEachPartWithoutBypassingRevision() = runBlocking<Unit> {
        val timestamp = System.currentTimeMillis()
        val draft = DraftEntity("parcelada-ficticia", "fixture:parcelada", "fixture.local", "Banco fictício",
            "EXEMPLO FICTÍCIO", "Compra aprovada no cartão de R$ 129,90.", 12990, Movement.EXPENSE,
            PaymentMethod.CARD, Category.OTHER, "Dados fictícios", timestamp, timestamp - 60_000)
        dao.insertDraft(draft)
        rule.onNodeWithText("Revisar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("review-${draft.id}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("review-${draft.id}").performClick()
        rule.chooseInstallments()
        type("installment-count", "3")
        rule.onNodeWithTag("purchase-preview-total").assertTextEquals("Total da compra: ${Money.format(12990)}")
        rule.onNodeWithTag("installment-basis-part").performScrollTo().performClick()
        type("purchase-installment", "4500")
        rule.onNodeWithTag("purchase-installment").performImeAction()
        waitForKeyboard()
        rule.onNodeWithTag("purchase-preview-total").assertTextEquals("Total da compra: ${Money.format(13500)}")
        rule.onNodeWithTag("installment-basis-total").performScrollTo().performClick()
        rule.onNodeWithTag("purchase-preview-total").assertTextEquals("Total da compra: ${Money.format(12990)}")
        rule.onNodeWithTag("installment-basis-part").performScrollTo().performClick()
        rule.onNodeWithTag("purchase-preview-total").assertTextEquals("Total da compra: ${Money.format(13500)}")

        app.repository.capture(NotificationInput(draft.sourcePackage, draft.sourceKey, draft.bank, "EXEMPLO atualizado",
            "Compra aprovada no cartão de R$ 139,90.", timestamp), simulation = true)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar esta versão e manter a edição").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Salvar compra parcelada").assertIsNotEnabled()
        assertTrue(dao.allPurchases().isEmpty())
        rule.onNodeWithText("Usar esta versão e manter a edição").performScrollTo().performClick()
        rule.onNodeWithText("Salvar compra parcelada").performClick()

        rule.waitUntil(5000) { runBlocking { dao.allPurchases().size == 1 } }
        val saved = dao.allPurchases().single()
        assertEquals(13500L, saved.totalCents)
        assertEquals(draft.id, saved.sourceDraftId)
        assertEquals(listOf(4500L, 4500L, 4500L), dao.allCommitments().map { it.amountCents })
        assertEquals("SAVED", dao.draftById(draft.id)?.status)
        assertEquals("", dao.draftById(draft.id)?.sourceBody)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun plannedIncomeAndFractionalLimitBelongToTheSelectedMonthOnly() = runBlocking<Unit> {
        val month = YearMonth.now()
        openPlanning()
        openIncomeEditor()
        type("monthly-income", "325019")
        type("monthly-commitment-limit", "25,75")
        rule.onNodeWithTag("monthly-commitment-limit").performImeAction()
        waitForKeyboard()
        rule.onNodeWithText("Salvar renda e limite").performClick()
        rule.waitUntil(5000) { runBlocking { dao.monthlyPlan(month.toString()) != null } }
        assertEquals(325019L, dao.monthlyPlan(month.toString())?.incomeCents)
        assertEquals(2575, dao.monthlyPlan(month.toString())?.healthyLimitBasisPoints)
        assertNull(dao.monthlyPlan(month.plusMonths(1).toString()))
        assertTrue(app.repository.exportRecords().isEmpty())

        rule.scrollAnalysisTo(hasContentDescription("Próximo mês"))
        rule.onNodeWithContentDescription("Próximo mês").performClick()
        scrollPlanningTo("edit-monthly-income")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Renda ainda não informada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Renda ainda não informada").assertExists()
        rule.onAllNodesWithText(Money.format(325019), substring = true).assertCountEquals(0)
    }

    @Test fun onlyExpenseRecurrencesCanBeIncludedAndTheChoiceCanBeRemoved() = runBlocking<Unit> {
        val month = YearMonth.now()
        val due = month.atEndOfMonth()
        val expense = RecurringPlan("aluguel-teste", "Aluguel fictício", 65000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Conta fictícia", due.toString(), due.dayOfMonth)
        app.repository.saveRecurring(expense)
        app.repository.saveRecurring(expense.copy(id = "salario-teste", title = "Salário fictício", movement = Movement.INCOME))
        openPlanning()
        rule.performPlanningAction("fixed-${expense.id}")
        rule.waitUntil(5000) { runBlocking { dao.fixedRecurringById(expense.id) != null } }
        assertEquals(month.toString(), dao.fixedRecurringById(expense.id)?.fromMonth)
        assertEquals(65000L, app.repository.commitmentReport(month).committedCents)
        rule.openPlanningActions("recurring-salario-teste")
        rule.onNodeWithTag("fixed-salario-teste").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        rule.performPlanningAction("fixed-${expense.id}")
        rule.waitUntil(5000) { runBlocking { dao.fixedRecurringById(expense.id) == null } }
        assertEquals(0L, app.repository.commitmentReport(month).committedCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun plannedIncomeStaysHiddenUntilRevealedInItsEditor() = runBlocking<Unit> {
        val month = YearMonth.now()
        app.repository.saveMonthlyPlan(MonthlyIncomePlan(month, 325019, 2575))
        rule.runOnIdle { app.settings.hideAmounts(true) }
        openPlanning()
        scrollPlanningTo("edit-monthly-income")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Renda e limite ocultos").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Renda e limite ocultos").assertExists()
        rule.onAllNodesWithText(Money.format(325019), substring = true).assertCountEquals(0)
        rule.onNodeWithTag("edit-monthly-income").performClick()
        rule.onNodeWithTag("monthly-income").assertDoesNotExist()
        rule.onNodeWithTag("monthly-commitment-limit").assertDoesNotExist()
        rule.onNodeWithText("Mostrar renda e limite para edição").performClick()
        rule.onNodeWithTag("monthly-income").assertExists()
        rule.onNodeWithTag("monthly-commitment-limit").assertExists()
    }

    private fun openPlanning() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejamento e análises"))
        rule.onNodeWithText("Planejamento e análises").performClick()
    }

    private fun scrollPlanningTo(tag: String) {
        rule.scrollPlanningToTag(tag)
    }

    private fun openIncomeEditor() {
        scrollPlanningTo("edit-monthly-income")
        rule.waitUntil(5000) {
            rule.onAllNodes(hasTestTag("edit-monthly-income") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("edit-monthly-income").performClick()
    }

    private fun type(tag: String, value: String) {
        rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun waitForKeyboard() {
        rule.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }
}
