package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnifiedMovementsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { CurrentForecastUi(rule) }
    private val month get() = ui.month
    private val finance get() = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]

    @Before fun reset() = ui.reset()

    @Test fun aRealizedExpenseIsEnteredOnceWithoutPayingTheRecurringForecast() = enterActual(Movement.EXPENSE)
    @Test fun aRealizedIncomeIsEnteredOnceWithoutReceivingTheRecurringForecast() = enterActual(Movement.INCOME)

    private fun enterActual(movement: Movement) {
        val kind = if (movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE
        ui.seed(ForecastRule("previsao-separada", month.atDay(1), "Previsão separada", kind, 5000,
            if (movement == Movement.INCOME) Category.OTHER_INCOME else Category.HOME,
            ForecastFrequency.MONTHLY, month.atDay(1)))
        val tag = if (movement == Movement.INCOME) "add-realized-income" else "add-realized-expense"
        rule.onNodeWithTag(tag).assertIsEnabled()
        ui.clickTag(tag)
        rule.onNodeWithText("Recorrente").assertDoesNotExist()
        rule.onNodeWithText("Parcelada").assertDoesNotExist()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("5000")
        rule.onNodeWithTag("record-description").performScrollTo().performTextReplacement("Lançamento independente")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.clickText(if (movement == Movement.EXPENSE) "Registrar despesa" else "Salvar lançamento")
        rule.waitUntil(10000) { finance.state.value.records.size == 1 }
        val saved = runBlocking { ui.app.repository.exportRecords().single() }
        assertEquals(movement, saved.movement)
        assertEquals(5000L, saved.amountCents)
        val totals = Ledger.month(listOf(saved), month, ZoneId.systemDefault())
        assertEquals(5000L, if (movement == Movement.INCOME) totals.income else totals.expenses)
        assertEquals(5000L, if (movement == Movement.INCOME) ui.report().incomeCents else ui.report().totalExpensesCents)
        assertTrue(ui.data().overrides.isEmpty())
        rule.activityRule.scenario.recreate()
        assertEquals(listOf(saved), runBlocking { ui.app.repository.exportRecords() })
        assertEquals(1, ui.data().rules.size)
    }

    @Test fun oneCycleCanBeEditedAndIgnoredWithoutChangingTheNext() {
        val source = ForecastRule("unified", month.atDay(1), "Conta mensal", ForecastKind.EXPENSE,
            5000, Category.HOME, ForecastFrequency.MONTHLY, month.atDay(1))
        ui.seed(source)
        ui.open(ui.report().lines.single())
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("7500")
        ui.saveEditor()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 7500L }
        assertEquals(7500L, ui.report().totalExpensesCents)
        assertEquals(5000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        ui.action(ui.report().lines.single(), "Pular este mês")
        ui.waitData { it.overrides.singleOrNull()?.ignored == true }
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(5000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(7500L, ui.data().overrides.single().amountCents)
        assertEquals(5000L, ui.data().rules.single().amountCents)
        ui.assertNoRecords()
    }

    @Test fun aCrossMonthExpenseAppearsOnlyInTheActualStatementMonth() {
        val previous = month.minusMonths(1)
        ui.seed(ForecastRule("cross-month", previous.atDay(1), "Previsão do mês anterior", ForecastKind.EXPENSE,
            5000, Category.HOME, ForecastFrequency.ONCE, previous.atDay(12)))
        val payment = Record("payment", 5000, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Carteira", "Pagamento em outro mês", month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        seedActual(payment)
        openStatement()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText(payment.description))
        rule.onNodeWithText(payment.description).assertExists()
        rule.onNodeWithTag("records-list").performScrollToNode(hasContentDescription("Mês anterior"))
        rule.onNodeWithContentDescription("Mês anterior").performClick()
        rule.onNodeWithText(payment.description).assertDoesNotExist()
        rule.onNodeWithText("Previsão do mês anterior").assertDoesNotExist()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("Somente lançamentos já ocorridos"))
        rule.onNodeWithText("Somente lançamentos já ocorridos").assertExists()
        assertEquals(0L, Ledger.month(listOf(payment), previous, ZoneId.systemDefault()).expenses)
        assertEquals(5000L, Ledger.month(listOf(payment), month, ZoneId.systemDefault()).expenses)
        assertEquals(5000L, ui.report(previous).totalExpensesCents)
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(listOf(payment), runBlocking { ui.app.repository.exportRecords() })
    }

    @Test fun editingAnActualInstallmentExpenseDoesNotEditItsForecastSchedule() {
        ui.seed(ForecastRule("unified-purchase", month.atDay(1), "Compra prevista", ForecastKind.EXPENSE,
            10000, Category.OTHER, ForecastFrequency.INSTALLMENTS, month.atDay(1), installmentCount = 2))
        val payment = Record("installment-payment", 5000, Movement.EXPENSE, PaymentMethod.CARD, Category.OTHER,
            "Carteira", "Parcela registrada", System.currentTimeMillis())
        seedActual(payment)
        openStatement()
        rule.onNodeWithTag("records-list").performScrollToNode(hasText(payment.description))
        ui.clickText(payment.description)
        rule.onNodeWithTag("record-description").assertTextContains(payment.description)
        rule.onNodeWithTag("forecast-installment-count").assertDoesNotExist()
        rule.onNodeWithText("Editar compra parcelada").assertDoesNotExist()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("5500")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.clickText("Salvar lançamento")
        rule.waitUntil(10000) { finance.state.value.records.singleOrNull()?.amountCents == 5500L }
        val updated = runBlocking { ui.app.repository.exportRecords().single() }
        assertEquals(payment.id, updated.id)
        assertEquals(5500L, updated.amountCents)
        assertEquals(5500L, Ledger.month(listOf(updated), month, ZoneId.systemDefault()).expenses)
        assertEquals(5000L, ui.report().totalExpensesCents)
        assertEquals(5000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(0L, ui.report(month.plusMonths(2)).totalExpensesCents)
        assertTrue(ui.data().overrides.isEmpty())
    }

    @Test fun projectionsKeepAnalysisSeparateFromActualMovementManagement() {
        ui.seed(ForecastRule("analysis", month.atDay(1), "Despesa prevista", ForecastKind.EXPENSE,
            5000, Category.HOME, ForecastFrequency.MONTHLY, month.atDay(1)))
        val actual = Record("realized", 2500, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Carteira", "Despesa realizada", System.currentTimeMillis())
        seedActual(actual)
        rule.openRendlyProjections()
        rule.onNodeWithTag("forecast-projections").assertExists()
        rule.onNodeWithTag("forecast-projections").performScrollToNode(hasTestTag("projection-margin-${month.atDay(1)}"))
        rule.onNodeWithTag("projection-margin-${month.atDay(1)}").assertTextContains(Money.format(-5000))
        rule.onNodeWithText("Adicionar recorrência").assertDoesNotExist()
        rule.onNodeWithText("Registrar pagamento").assertDoesNotExist()
        assertEquals(5000L, ui.report().totalExpensesCents)
        assertEquals(2500L, Ledger.month(listOf(actual), month, ZoneId.systemDefault()).expenses)
        assertEquals(listOf(actual), runBlocking { ui.app.repository.exportRecords() })
    }

    private fun seedActual(record: Record) {
        runBlocking { assertTrue(ui.app.repository.save(record, null)) }
        rule.waitUntil(10000) { finance.state.value.records.any { it.id == record.id } }
    }

    private fun openStatement() {
        rule.openRendlyMonth()
        rule.onNodeWithTag("records-list").assertExists()
    }
}
