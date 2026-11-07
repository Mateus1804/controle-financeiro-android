package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PurchaseManagementUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val repository get() = app.repository
    private val dao get() = app.database.financeDao()
    private val month = YearMonth.now()
    private fun id(number: Int) = "installment:gestao-ficticia:$number"

    @Before fun reset() {
        runBlocking { repository.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun unconfirmedPurchaseCanBeCorrectedFromPlanning() = runBlocking<Unit> {
        createPurchase(20000, 2)
        openPlanning()
        tapRowAction("edit-${id(1)}")
        awaitDialog("Editar compra parcelada")
        rule.onNodeWithTag("purchase-edit-title").performScrollTo().performTextReplacement("Compra corrigida")
        rule.onNodeWithTag("installment-count").performScrollTo().performTextReplacement("3")
        replaceMoney("purchase-total", "21000")
        rule.onNodeWithText("Salvar alterações da compra").performClick()
        rule.waitUntil(5000) { runBlocking { dao.purchaseById("gestao-ficticia")?.installmentCount == 3 } }
        assertEquals("Compra corrigida", dao.allPurchases().single().title)
        assertEquals(listOf(7000L, 7000L, 7000L), dao.allCommitments().map { it.amountCents })
        assertEquals(7000L, repository.commitmentReport(month).committedCents)
        assertTrue(repository.exportRecords().isEmpty())
    }

    @Test fun recordedHistorySurvivesEditingAndCancellingTheRemainingForecasts() = runBlocking<Unit> {
        createPurchase(60000, 3)
        val payment = Record("pagamento-ficticio", 19000, Movement.EXPENSE, PaymentMethod.CARD, Category.OTHER,
            "Carteira", "Pagamento fictício", System.currentTimeMillis())
        assertTrue(repository.confirmInstallment(id(1), payment))
        repository.saveMonthlyPlan(MonthlyIncomePlan(month.plusMonths(1), 100000))
        openPlanning()
        rule.onNodeWithTag("planning-list").performScrollToNode(hasContentDescription("Próximo mês"))
        rule.onNodeWithContentDescription("Próximo mês").performClick()
        // Espera o mês novo emitir a programação antes de procurar a parcela na lista.
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag("commitment-card"))
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription("20% da renda comprometidos; maior gasto: Compra fictícia · 2/3.").fetchSemanticsNodes().isNotEmpty() }
        tapRowAction("edit-${id(2)}")
        awaitDialog("Editar parcela em aberto")
        replaceMoney("adjust-installment-amount", "22001")
        rule.onNodeWithText("Salvar parcela").performClick()
        rule.waitUntil(5000) { runBlocking { dao.commitmentById(id(2))?.amountCents == 22001L } }
        rule.waitUntil(8000) { rule.onAllNodesWithText("Parcela corrigida no Planejamento.").fetchSemanticsNodes().isEmpty() }

        tapRowAction("cancel-${id(2)}")
        awaitDialog("Cancelar parcelas em aberto?")
        rule.onNodeWithText("2 parcelas sairão do Planejamento, incluindo as de outros meses.").assertExists()
        rule.onNodeWithTag("keep-open-installments").performClick()
        assertEquals(22001L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        tapRowAction("cancel-${id(2)}")
        awaitDialog("Cancelar parcelas em aberto?")
        rule.onNodeWithText("Cancelar parcelas").performClick()
        rule.waitUntil(5000) { runBlocking { dao.purchaseById("gestao-ficticia")?.openCancelled == true } }

        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag("commitment-card"))
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription("0% da renda comprometidos; nenhum compromisso.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(19000L, repository.commitmentReport(month).committedCents)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(1)).committedCents)
        assertEquals(0L, repository.commitmentReport(month.plusMonths(2)).committedCents)
        assertEquals(listOf(payment), repository.exportRecords())
        assertEquals(payment.id, dao.commitmentById(id(1))!!.recordId)
    }

    @Test fun purchaseEditorRequiresExplicitRevealWhenValuesAreHidden() = runBlocking<Unit> {
        createPurchase(60000, 3)
        rule.runOnIdle { app.settings.hideAmounts(true) }
        openPlanning()
        tapRowAction("edit-${id(1)}")
        awaitDialog("Editar compra parcelada")
        rule.onNodeWithTag("purchase-total").assertDoesNotExist()
        rule.onAllNodesWithText(Money.format(60000), substring = true).assertCountEquals(0)
        rule.onNodeWithText("Mostrar valores da compra para edição").performScrollTo().performClick()
        rule.onNodeWithTag("purchase-total").assertExists()
    }

    private suspend fun createPurchase(cents: Long, count: Int) {
        assertTrue(repository.createPurchase(InstallmentPurchase("gestao-ficticia", "Compra fictícia", "Carteira",
            Category.OTHER, cents, count, month.atDay(12))))
    }

    private fun openPlanning() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejar limites e recorrências"))
        rule.onNodeWithText("Planejar limites e recorrências").performClick()
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag("edit-monthly-income"))
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTag("edit-monthly-income") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tapRowAction(tag: String) {
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun awaitDialog(title: String) {
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun replaceMoney(tag: String, cents: String) {
        val field = rule.onNodeWithTag(tag).performScrollTo()
        field.performTextReplacement(cents)
        field.performImeAction()
        rule.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }
}
