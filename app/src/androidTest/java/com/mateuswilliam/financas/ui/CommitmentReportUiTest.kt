package com.mateuswilliam.financas.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.Appearance
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CommitmentReportUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val repository get() = app.repository
    private val month = YearMonth.now()

    @Before fun reset() {
        runBlocking { repository.clear() }
        app.settings.finishIntro()
        rule.waitForIdle()
    }

    @Test fun selectedMonthGroupsSmallBillsAndUpdatesAfterAParcelIsLinked() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 100000, 4000))
        repository.saveMonthlyPlan(MonthlyIncomePlan(month.plusMonths(1), 200000, 4000))
        val rent = RecurringPlan("aluguel-ficticio", "Aluguel fictício", 20000, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, "Conta fictícia", month.atDay(8).toString(), 8)
        repository.saveRecurring(rent)
        repository.setFixedRecurring(rent.id, month)
        purchase("curso", "Curso fictício", 10000)
        purchase("livro", "Livro fictício", 2000)
        purchase("item", "Item fictício", 1000)
        repository.save(payment("despesa-avulsa", 90000, month), null)
        rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
        openPlanning()
        assertChart("33%", "Aluguel fictício")
        rule.onNodeWithText("Atenção").assertExists()
        capture("comprometimento-claro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle()
        capture("comprometimento-escuro")

        scrollToTag("commitment-legend-other")
        val others = rule.onNodeWithTag("commitment-legend-other")
        others.assertTextContains("Outros").assertTextContains(Money.format(3000))
        rule.onNodeWithTag("commitment-other-details").performScrollTo().performClick()
        scrollToTag("commitment-entry-installment:livro:1")
        rule.onNodeWithTag("commitment-entry-installment:livro:1")
            .assertTextContains("Livro fictício · 1/1").assertTextContains(Money.format(2000))

        assertTrue(repository.confirmInstallment("installment:curso:1", payment("curso-pago", 13000, month.plusMonths(1))))
        scrollToTag("commitment-card")
        assertChart("36%", "Aluguel fictício")
        rule.onNodeWithTag("planning-list").performScrollToNode(hasContentDescription("Próximo mês"))
        rule.onNodeWithContentDescription("Próximo mês").performClick()
        scrollToTag("commitment-card")
        assertChart("10%", "Aluguel fictício")
        rule.onNodeWithText("Confortável").assertExists()
    }

    @Test fun aboveIncomeShowsTheActualPercentageAndNoFreePortion() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 100000, 3000))
        purchase("compra-maior", "Compra fictícia", 125000)
        openPlanning()
        assertChart("125%", "Compra fictícia · 1/1")
        rule.onNodeWithText("Acima do limite").assertExists()
        rule.onNodeWithText("Valor acima da renda").assertExists()
        rule.onNodeWithText(Money.format(25000)).assertExists()
        scrollToTag("commitment-legend-expense:installment:compra-maior:1")
        rule.onNodeWithTag("commitment-legend-free").assertDoesNotExist()
    }

    @Test fun hiddenValuesRemoveNumbersGeometryAndTheLimitStatusFromTheReport() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, 100000, 3000))
        purchase("compra-maior", "Compra fictícia", 125000)
        openPlanning()
        assertChart("125%", "Compra fictícia · 1/1")
        rule.runOnIdle { app.settings.hideAmounts(true) }
        rule.onNodeWithText("Renda e limite ocultos").assertIsDisplayed()
        rule.onNodeWithTag("commitment-donut").assertDoesNotExist()
        rule.onNodeWithText("Acima do limite").assertDoesNotExist()
        rule.onNodeWithTag("commitment-legend-expense:installment:compra-maior:1").assertDoesNotExist()
        rule.onAllNodes(hasText("125%", substring = true) or hasContentDescription("125%", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasText(Money.format(125000), substring = true) or
            hasContentDescription(Money.format(125000), substring = true)).assertCountEquals(0)
    }

    @Test fun absentIncomeKeepsPlannedExpensesAndOffersTheIncomeEditor() = runBlocking<Unit> {
        purchase("sem-renda", "Conta fictícia", 9500)
        openPlanning()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Renda ainda não informada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("committed-value").assertTextEquals(Money.format(9500))
        rule.onNodeWithTag("commitment-donut").assertDoesNotExist()
        rule.onNodeWithTag("edit-monthly-income").performScrollTo().performClick()
        rule.onNodeWithText("Renda e limite ·", substring = true).assertIsDisplayed()
    }

    @Test fun enlargedFontKeepsTheExactExcessReadable() = runBlocking<Unit> {
        repository.saveMonthlyPlan(MonthlyIncomePlan(month, Money.MAX_CENTS, 3000))
        purchase("renda-inteira", "Compra fictícia", Money.MAX_CENTS)
        purchase("um-centavo", "Ajuste fictício", 1)
        val original = shell("settings get system font_scale").trim().toFloatOrNull()
        try {
            shell("settings put system font_scale 1.6")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.5f }
            openPlanning()
            assertChart("100,0000000001%", "Compra fictícia · 1/1")
            scrollToTag("commitment-percentage")
            rule.onNodeWithTag("commitment-percentage").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(action(layouts))
                    assertFalse(layouts.single().hasVisualOverflow)
                }
            capture("comprometimento-fonte-ampliada")
        } finally {
            if (original != null) shell("settings put system font_scale $original")
            else shell("settings delete system font_scale")
            rule.waitUntil(10000) { kotlin.math.abs(rule.activity.resources.configuration.fontScale - (original ?: 1f)) < 0.1f }
        }
    }

    private suspend fun purchase(id: String, title: String, cents: Long) {
        assertTrue(repository.createPurchase(InstallmentPurchase(id, title, "Conta fictícia", Category.OTHER,
            cents, 1, month.atDay(12))))
    }

    private fun payment(id: String, cents: Long, paymentMonth: YearMonth) = Record(id, cents, Movement.EXPENSE,
        PaymentMethod.CARD, Category.OTHER, "Conta fictícia", "Pagamento fictício",
        paymentMonth.atDay(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

    private fun openPlanning() {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasText("Planejar limites e recorrências"))
        rule.onNodeWithText("Planejar limites e recorrências").performClick()
        scrollToTag("commitment-card")
    }

    private fun scrollToTag(tag: String) {
        rule.onNodeWithTag("planning-list").performScrollToNode(hasTestTag(tag))
    }

    private fun assertChart(percentage: String, largest: String) {
        val description = "$percentage da renda comprometidos; maior gasto: $largest."
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("commitment-donut").assertContentDescriptionEquals(description)
    }

    private fun capture(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
