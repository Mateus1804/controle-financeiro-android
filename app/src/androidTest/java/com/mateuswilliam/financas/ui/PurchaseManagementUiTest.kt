package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PurchaseManagementUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { CurrentForecastUi(rule) }
    private val month get() = ui.month
    private fun purchase(cents: Long, count: Int) = ForecastRule("compra-ficticia", month.atDay(1),
        "Compra fictícia", ForecastKind.EXPENSE, cents, Category.OTHER, ForecastFrequency.INSTALLMENTS,
        month.atDay(1), installmentCount = count)

    @Before fun reset() = ui.reset()

    @Test fun theWholeInstallmentForecastCanBeCorrectedFromTheDashboard() {
        ui.seed(purchase(20000, 2))
        ui.open(ui.report().lines.single())
        ui.clickText("Só neste ciclo"); ui.clickText("Deste ciclo em diante")
        rule.onNodeWithTag("forecast-title").performScrollTo().performTextReplacement("Compra corrigida")
        rule.onNodeWithTag("forecast-installment-count").performScrollTo().performTextReplacement("3")
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("21000")
        ui.saveEditor()
        ui.waitData { it.rules.singleOrNull()?.amountCents == 21000L }
        val corrected = ui.data().rules.single()
        assertEquals("Compra corrigida", corrected.title)
        assertEquals(3, corrected.installmentCount)
        assertEquals(listOf(7000L, 7000L, 7000L), (0L..2L).map { ui.report(month.plusMonths(it)).totalExpensesCents })
        assertEquals(0L, ui.report(month.plusMonths(3)).totalExpensesCents)
        ui.assertNoRecords()
    }

    @Test fun recordedHistorySurvivesAdjustingAndStoppingTheRemainingForecasts() {
        val previous = month.minusMonths(1)
        val source = purchase(60000, 3).copy(effectiveFrom = previous.atDay(1), startDate = previous.atDay(1))
        ui.seed(source)
        val payment = Record("pagamento-ficticio", 19000, Movement.EXPENSE, PaymentMethod.CARD, Category.OTHER,
            "Carteira", "Pagamento fictício", previous.atDay(12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        runBlocking { assertTrue(ui.app.repository.save(payment, null)) }
        ui.open(ui.report().lines.single())
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("22001")
        ui.saveEditor()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 22001L }
        assertEquals(22001L, ui.report().totalExpensesCents)
        assertEquals(20000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        ui.action(ui.report().lines.single(), "Interromper futuras")
        ui.waitData { it.rules.any { version -> !version.active } }
        assertEquals(20000L, ui.report(previous).totalExpensesCents)
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(0L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(listOf(payment), runBlocking { ui.app.repository.exportRecords() })
        assertEquals(19000L, Ledger.month(listOf(payment), previous, ZoneId.systemDefault()).expenses)
        assertEquals(0L, Ledger.month(listOf(payment), month, ZoneId.systemDefault()).expenses)
        assertEquals(22001L, ui.data().overrides.single().amountCents)
    }

    @Test fun installmentEditorRequiresExplicitRevealWhenValuesAreHidden() {
        ui.seed(purchase(60000, 3))
        rule.runOnIdle { ui.app.settings.hideAmounts(true) }
        ui.open(ui.report().lines.single())
        rule.onNodeWithTag("forecast-amount").assertDoesNotExist()
        rule.onAllNodesWithText(Money.format(60000), substring = true).assertCountEquals(0)
        rule.onAllNodesWithText(Money.format(20000), substring = true).assertCountEquals(0)
        rule.onNodeWithText("Mostrar valor da previsão para edição").performScrollTo().performClick()
        val amount = rule.onNodeWithTag("forecast-amount").assertExists().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(20000L, MoneyInput.normalize(amount)?.toLongOrNull())
        assertEquals(60000L, ui.data().rules.single().amountCents)
        assertTrue(ui.data().overrides.isEmpty())
        ui.assertNoRecords()
    }
}
