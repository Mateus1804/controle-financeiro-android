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

class MonthlyRecurringUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { CurrentForecastUi(rule) }
    private val month get() = ui.month
    private val plan get() = ForecastRule("escopo-ui", month.atDay(1), "Conta mensal", ForecastKind.EXPENSE,
        5000, Category.HOME, ForecastFrequency.MONTHLY, month.atDay(1))

    @Before fun reset() { ui.reset(); ui.seed(plan) }

    @Test fun editingThisCycleSurvivesRecreationAndKeepsTheFollowingMonth() {
        ui.open(ui.report().lines.single())
        rule.onNodeWithText("Alterar só este mês").assertExists()
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("7500")
        rule.onNodeWithTag("forecast-title").performScrollTo().performTextReplacement("Conta ajustada só neste mês")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("forecast-title").assertTextContains("Conta ajustada só neste mês")
        ui.saveEditor()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 7500L }
        assertEquals(7500L, ui.report().totalExpensesCents)
        assertEquals(5000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(5000L, ui.data().rules.single().amountCents)
        ui.showMonth(month.plusMonths(1))
        ui.scroll("forecast-line:${plan.itemId}:month:${month.plusMonths(1)}")
        rule.onNodeWithText("Conta ajustada só neste mês").assertDoesNotExist()
        ui.showMonth(month)
        ui.scroll("forecast-line:${plan.itemId}:month:$month")
        rule.onNodeWithText("Conta ajustada só neste mês").assertExists()
        ui.assertNoRecords()
    }

    @Test fun aRecordedExpenseStaysSeparateFromTheRecurringForecast() {
        val payment = Record("despesa-real", 6250, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Carteira", "Despesa já registrada", System.currentTimeMillis())
        runBlocking { assertTrue(ui.app.repository.save(payment, null)) }
        val line = ui.report().lines.single()
        val tag = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
        ui.scroll(tag); ui.clickTag(tag)
        rule.onNodeWithText("Editar").assertExists()
        rule.onNodeWithText("Pular este mês").assertExists()
        rule.onNodeWithText("Registrar pagamento").assertDoesNotExist()
        rule.onNodeWithText("Confirmar pagamento").assertDoesNotExist()
        assertEquals(5000L, ui.report().totalExpensesCents)
        assertEquals(listOf(payment), runBlocking { ui.app.repository.exportRecords() })
        assertEquals(6250L, Ledger.month(listOf(payment), month, ZoneId.systemDefault()).expenses)
    }

    @Test fun anIgnoredCycleOffersRestoreAndKeepsTheFollowingMonth() {
        ui.action(ui.report().lines.single(), "Pular este mês")
        ui.waitData { it.overrides.singleOrNull()?.ignored == true }
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(5000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        val ignored = ui.report().lines.single()
        assertTrue(ignored.ignored)
        val tag = "forecast-actions:${ignored.itemId}:${ignored.occurrenceKey}"
        ui.scroll(tag); ui.clickTag(tag)
        rule.onNodeWithText("Restaurar este mês").assertExists()
        rule.onNodeWithText("Pular este mês").assertDoesNotExist()
        ui.clickText("Restaurar este mês")
        ui.waitData { it.overrides.singleOrNull()?.ignored == false }
        assertEquals(5000L, ui.report().totalExpensesCents)
        ui.assertNoRecords()
    }

    @Test fun stoppingFromTheNextCyclePreservesTheEarlierOccurrenceOverride() {
        runBlocking { ui.app.forecasts.saveOverride(ForecastOverride(plan.itemId, "month:$month", 6250,
            title = "Ajuste deste mês")) }
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 6250L }
        ui.showMonth(month.plusMonths(1))
        ui.action(ui.report(month.plusMonths(1)).lines.single(), "Interromper futuras")
        ui.waitData { it.rules.any { version -> !version.active && version.effectiveFrom == month.plusMonths(1).atDay(1) } }
        assertEquals(6250L, ui.report().totalExpensesCents)
        assertEquals("Ajuste deste mês", ui.report().lines.single().title)
        assertEquals(0L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(0L, ui.report(month.plusMonths(2)).totalExpensesCents)
        assertEquals(6250L, ui.data().overrides.single().amountCents)
        ui.assertNoRecords()
    }

    @Test fun futureScopeLoadsTheRuleAmountAndReplacesLaterRulesWithoutLosingTheOccurrence() {
        ui.seed(plan.copy(effectiveFrom = month.plusMonths(1).atDay(1), amountCents = 9000))
        runBlocking { ui.app.forecasts.saveOverride(ForecastOverride(plan.itemId, "month:$month", 6250)) }
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 6250L }
        ui.open(ui.report().lines.single())
        ui.clickText("Alterar só este mês"); ui.clickText("Aplicar deste mês em diante")
        val amount = rule.onNodeWithTag("forecast-amount").performScrollTo().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(5000L, MoneyInput.normalize(amount)?.toLongOrNull())
        rule.onNodeWithTag("forecast-amount").performTextReplacement("8000")
        ui.saveEditor()
        ui.waitData { it.rules.size == 1 && it.rules.single().amountCents == 8000L }
        assertEquals(6250L, ui.report().totalExpensesCents)
        assertEquals(8000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(8000L, ui.report(month.plusMonths(2)).totalExpensesCents)
        assertEquals(6250L, ui.data().overrides.single().amountCents)
        ui.assertNoRecords()
    }
}
