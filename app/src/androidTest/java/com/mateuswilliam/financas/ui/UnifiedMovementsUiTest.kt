package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.FinanceViewModel
import androidx.lifecycle.ViewModelProvider
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnifiedMovementsUiTest {
    @get:Rule val rule = createLegacyFinanceRule()
    private val app get() = rule.activity.application as FinanceApplication
    private val month = YearMonth.now()
    @Before fun reset() {
        runBlocking { app.repository.clear(); app.repository.addAccount("Carteira") }
        app.settings.finishIntro()
        val model = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]
        rule.waitUntil(10000) { model.state.value.accounts.any { it.label == "Carteira" } }
        rule.waitForIdle()
    }
    private fun click(text: String) {
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    private fun scroll(tag: String) {
        rule.waitUntil(5000) {
            try { rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag(tag)); true }
            catch (_: AssertionError) { false }
        }
    }
    private fun actions(id: String) {
        val tag = "actions-recurring-$id"
        scroll(tag)
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    @Test fun recurringExpenseIsCreatedAndPaidOnceFromTheMainScreen() = createAndConfirm(Movement.EXPENSE)
    @Test fun recurringIncomeIsCreatedAndReceivedOnceFromTheMainScreen() = createAndConfirm(Movement.INCOME)
    private fun createAndConfirm(movement: Movement) {
        rule.onNodeWithTag("quick-${movement.name.lowercase()}").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        click("Única"); click("Recorrente")
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("5000")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Conta unificada")
        rule.onNodeWithText("Descrição").performImeAction()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Salvar recorrência")
        rule.waitUntil(5000) { runBlocking { app.repository.recurring.first().size == 1 } }
        val id = runBlocking { app.repository.recurring.first().single().id }
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
        actions(id); click(if (movement == Movement.INCOME) "Registrar recebimento" else "Registrar pagamento")
        click("Confirmar")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val record = runBlocking { app.repository.exportRecords().single() }
        scroll("movement-record:${record.id}")
        rule.onNodeWithTag("movement-record:${record.id}").assertIsDisplayed()
        rule.onNodeWithTag("movement-recurring:$id:$month").assertDoesNotExist()
        val totals = Ledger.month(listOf(record), month, ZoneId.systemDefault())
        assertEquals(5000L, if (movement == Movement.INCOME) totals.income else totals.expenses)
        rule.activityRule.scenario.recreate()
        assertEquals(1, runBlocking { app.repository.exportRecords().size })
    }
    @Test fun oneMonthCanBeEditedAndIgnoredWithoutChangingTheNext() = runBlocking<Unit> {
        val plan = RecurringPlan("unified", "Conta mensal", 5000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Carteira", month.atDay(12).toString(), 12)
        app.repository.saveRecurring(plan); app.repository.setFixedRecurring(plan.id, month)
        actions(plan.id); click("Editar só este mês")
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("7500")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Salvar só este mês")
        rule.waitUntil(5000) { runBlocking { app.repository.commitmentReport(month).committedCents == 7500L } }
        assertEquals(5000L, app.repository.commitmentReport(month.plusMonths(1)).committedCents)
        actions(plan.id); click("Ignorar este mês"); click("Confirmar")
        rule.waitUntil(5000) { runBlocking { app.repository.observeRecurringMonths(month).first().single().status == RecurringMonthStatus.SKIPPED } }
        assertEquals(0L, app.repository.commitmentReport(month).committedCents)
        assertEquals(RecurringMonthStatus.PENDING, app.repository.observeRecurringMonths(month.plusMonths(1)).first().single().status)
        assertTrue(app.repository.exportRecords().isEmpty())
    }
    @Test fun crossMonthPaymentIsAClosedReferenceInsteadOfASecondRealizedMovement() {
        val due = month.atDay(12)
        val plan = RecurringPlan("cross-month", "Conta", 5000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Carteira", due.toString(), 12)
        val payment = plan.proposedRecord("payment", ZoneId.systemDefault()).copy(occurredAt =
            month.plusMonths(1).atDay(2).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val occurrence = RecurringMonth(plan.id, month, plan, RecurringMonthStatus.RECORDED, payment.id, true, 1)
        val before = monthlyMovements(listOf(payment), month, listOf(occurrence), emptyList(), asOf = Long.MAX_VALUE)
        assertEquals(1, before.size)
        assertFalse(before.single().realized)
        assertTrue(before.single().status.startsWith("Quitada em"))
        val after = monthlyMovements(listOf(payment), month.plusMonths(1), emptyList(), emptyList(), asOf = Long.MAX_VALUE)
        assertEquals(1, after.size)
        assertTrue(after.single().realized)
        assertEquals(0L, Ledger.month(listOf(payment), month, ZoneId.systemDefault()).expenses)
        assertEquals(5000L, Ledger.month(listOf(payment), month.plusMonths(1), ZoneId.systemDefault()).expenses)
    }
    @Test fun paidInstallmentOffersItsPaymentWithoutAnUnavailablePurchaseAction() = runBlocking<Unit> {
        app.repository.createPurchase(InstallmentPurchase("unified-purchase", "Compra", "Carteira", Category.OTHER,
            10000, 2, month.atDay(12)))
        val due = app.repository.commitmentSetup(month).first().installments.first()
        val record = Record("installment-payment", due.amountCents, Movement.EXPENSE, due.method, due.category,
            due.bank, due.title, month.atDay(12).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertTrue(app.repository.confirmInstallment(due.id, record, due.purchaseRevision))
        scroll("actions-${due.id}")
        rule.onNodeWithTag("actions-${due.id}").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.onNodeWithText("Editar lançamento").assertIsDisplayed()
        rule.onNodeWithTag("edit-${due.id}").assertDoesNotExist()
        assertEquals(1, app.repository.exportRecords().size)
    }
    @Test fun planningKeepsAnalysisWithoutDuplicatedMovementManagement() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("open-planning"))
        rule.onNodeWithTag("open-planning").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.onNodeWithTag("planning-list").assertIsDisplayed()
        rule.onNodeWithText("Adicionar recorrência").assertDoesNotExist()
        rule.onNodeWithText("Contas e receitas recorrentes").assertDoesNotExist()
        rule.scrollAnalysisTo(hasText("Orçamentos por categoria"))
        rule.onNodeWithText("Orçamentos por categoria").assertIsDisplayed()
    }
}
