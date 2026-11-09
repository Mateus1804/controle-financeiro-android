package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.Movement
import com.mateuswilliam.financas.core.PaymentMethod
import com.mateuswilliam.financas.data.DraftEntity
import java.io.ByteArrayInputStream
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CommitmentJourneyTest {
    @get:Rule val rule = createLegacyFinanceRule()
    private val app get() = rule.activity.application as FinanceApplication
    private val repository get() = app.repository
    private val dao get() = app.database.financeDao()

    @Test fun incomeToPaymentAdjustmentCancellationAndEncryptedRestore() = runBlocking<Unit> {
        repository.clear()
        app.settings.finishIntro()
        rule.waitForIdle()
        val month = YearMonth.now()
        val next = month.plusMonths(1)

        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        setInstallments(count = "4", alreadyPaid = "1")
        type("purchase-total", "60000")
        describePurchase("Compra em andamento")
        rule.onNodeWithText("Salvar compra parcelada").performClick()
        rule.waitUntil(5000) { runBlocking { dao.allPurchases().size == 1 } }
        val ongoing = dao.allPurchases().single()
        assertEquals(listOf(2, 3, 4), dao.installmentsForPurchase(ongoing.id).map { it.installmentNumber })
        assertTrue(repository.exportRecords().isEmpty())
        waitForMessageToClose("Compra salva.")

        val draftId = "jornada-ficticia"
        val now = System.currentTimeMillis()
        dao.insertDraft(DraftEntity(draftId, "fixture:jornada", "fixture.local", "Banco fictício",
            "EXEMPLO FICTÍCIO", "Compra aprovada no cartão de R$ 600,00.", 60000, Movement.EXPENSE,
            PaymentMethod.CARD, Category.OTHER, "Dados fictícios", now, now - 60_000))
        rule.onNodeWithText("Revisar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("review-$draftId").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("review-$draftId").performClick()
        setInstallments(count = "3")
        rule.onNodeWithTag("purchase-preview-total").assertTextEquals("Total da compra: ${Money.format(60000)}")
        describePurchase("Compra revisada")
        rule.onNodeWithText("Salvar compra parcelada").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Ver pendências").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ver pendências").performClick()
        waitForMessageToClose("Compra salva.")
        val reviewed = dao.allPurchases().single { it.sourceDraftId == draftId }
        assertEquals("SAVED", dao.draftById(draftId)?.status)
        assertTrue(repository.exportRecords().isEmpty())

        rule.onNodeWithText("Resumo").performClick()
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejamento e análises"))
        rule.onNodeWithText("Planejamento e análises").performClick()
        saveIncome(month, "36")
        assertChart("35%", "Compra revisada · 1/3", 65000)
        rule.onNodeWithText("Atenção").assertExists()
        assertTrue(repository.exportRecords().isEmpty())

        val paidId = "installment:${reviewed.id}:1"
        tapPlanning("confirm-$paidId")
        rule.onNodeWithText("Conferir parcela").assertExists()
        typeAndCloseKeyboard("amount-input", "22000")
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitUntil(5000) { runBlocking { repository.exportRecords().size == 1 } }
        val payment = repository.exportRecords().single()
        assertEquals(22000L, payment.amountCents)
        assertEquals(payment.id, dao.commitmentById(paidId)?.recordId)
        assertChart("37%", "Compra revisada · 1/3", 63000)
        rule.onNodeWithText("Acima do limite").assertExists()

        rule.runOnIdle { app.settings.hideAmounts(true) }
        rule.onNodeWithText("Renda e limite ocultos").assertExists()
        rule.onNodeWithTag("commitment-donut").assertDoesNotExist()
        rule.onAllNodes(hasText("37%", substring = true) or hasContentDescription("37%", substring = true)).assertCountEquals(0)
        rule.runOnIdle { app.settings.hideAmounts(false) }

        rule.scrollAnalysisTo(hasContentDescription("Próximo mês"))
        rule.onNodeWithContentDescription("Próximo mês").performClick()
        scrollTo("commitment-card")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Renda ainda não informada").fetchSemanticsNodes().isNotEmpty() }
        saveIncome(next, "30")
        assertChart("35%", "Compra revisada · 2/3", 65000)

        val openId = "installment:${reviewed.id}:2"
        tapPlanning("edit-$openId")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Editar parcela em aberto").fetchSemanticsNodes().isNotEmpty() }
        typeAndCloseKeyboard("adjust-installment-amount", "22500")
        rule.onNodeWithText("Salvar parcela").performClick()
        assertChart("37,5%", "Compra revisada · 2/3", 62500)
        waitForMessageToClose("Parcela corrigida nas movimentações.")

        tapPlanning("cancel-$openId")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Cancelar parcelas em aberto?").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("2 parcelas sairão das movimentações, incluindo as de outros meses.").assertExists()
        rule.onNodeWithText("Cancelar parcelas").performClick()
        assertChart("15%", "Compra em andamento · 3/4", 85000)
        rule.onNodeWithText("Confortável").assertExists()
        assertEquals(15000L, repository.commitmentReport(next.plusMonths(1)).committedCents)
        assertEquals(listOf(payment), repository.exportRecords())

        // O seletor e a confirmação visual são cobertos em BackupDocumentFlowTest.
        val before = repository.snapshot()
        val encrypted = app.backups.create(testPassword())
        repository.saveMonthlyPlan(before.monthlyPlans.single { it.month == next.toString() }.toPlan().copy(incomeCents = 200000))
        val preview = app.backups.stage(ByteArrayInputStream(encrypted), testPassword())
        assertEquals(2, preview.purchases)
        assertEquals(200000L, repository.commitmentReport(next).incomeCents)
        assertChart("7,5%", "Compra em andamento · 3/4", 185000)
        app.backups.restore(preview)
        assertChart("15%", "Compra em andamento · 3/4", 85000)
        val restored = repository.snapshot()
        assertEquals(before.monthlyPlans, restored.monthlyPlans)
        assertEquals(before.purchases, restored.purchases)
        assertEquals(before.commitments, restored.commitments)
        assertEquals(listOf(payment), repository.exportRecords())
        assertEquals(37000L, repository.commitmentReport(month).committedCents)
        assertEquals(15000L, repository.commitmentReport(next.plusMonths(1)).committedCents)
    }

    private fun setInstallments(count: String, alreadyPaid: String? = null) {
        rule.chooseInstallments()
        type("installment-count", count)
        alreadyPaid?.let { type("installments-paid", it) }
    }

    private fun describePurchase(description: String) {
        val field = rule.onNodeWithText("Descrição").performScrollTo()
        field.performTextReplacement(description)
        field.performImeAction()
        waitForKeyboard()
    }

    private fun saveIncome(month: YearMonth, limit: String) {
        scrollTo("edit-monthly-income")
        rule.waitUntil(5000) {
            rule.onAllNodes(hasTestTag("edit-monthly-income") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("edit-monthly-income").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("monthly-income").fetchSemanticsNodes().isNotEmpty() }
        type("monthly-income", "100000")
        typeAndCloseKeyboard("monthly-commitment-limit", limit)
        rule.onNodeWithText("Salvar renda e limite").performClick()
        rule.waitUntil(5000) { runBlocking { dao.monthlyPlan(month.toString())?.incomeCents == 100000L } }
        waitForMessageToClose("Renda prevista e limite salvos")
    }

    private fun assertChart(percentage: String, largest: String, freeCents: Long) {
        scrollTo("commitment-card")
        val description = "$percentage da renda comprometidos; maior gasto: $largest."
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("commitment-donut").assertContentDescriptionEquals(description)
        scrollTo("commitment-legend-free")
        rule.onNodeWithTag("commitment-legend-free").assertTextContains(Money.format(freeCents))
        scrollTo("commitment-card")
    }

    private fun tapPlanning(tag: String) {
        rule.performPlanningAction(tag)
    }

    private fun scrollTo(tag: String) {
        rule.scrollPlanningToTag(tag)
    }

    private fun type(tag: String, value: String) {
        rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun typeAndCloseKeyboard(tag: String, value: String) {
        type(tag, value)
        rule.onNodeWithTag(tag).performImeAction()
        waitForKeyboard()
    }

    private fun waitForKeyboard() {
        rule.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }

    private fun waitForMessageToClose(prefix: String) {
        rule.dismissFeedback(prefix)
    }

    private fun testPassword() = "Senha fictícia de teste 2026!".toCharArray()
}
