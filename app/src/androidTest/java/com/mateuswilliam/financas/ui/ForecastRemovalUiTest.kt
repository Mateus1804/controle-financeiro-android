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

class ForecastRemovalUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { CurrentForecastUi(rule) }
    private val month get() = ui.month

    @Before fun reset() = ui.reset()

    @Test fun ignoringAndRestoringAnInstallmentChangesOnlyTheSelectedForecast() {
        ui.seed(ForecastRule("compra-ficticia", month.atDay(1), "Compra fictícia", ForecastKind.EXPENSE,
            20000, Category.OTHER, ForecastFrequency.INSTALLMENTS, month.atDay(1), installmentCount = 2))
        val first = ui.report().lines.single()
        ui.action(first, "Ignorar somente ocorrência")
        ui.waitData { it.overrides.singleOrNull()?.ignored == true }
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(10000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals("installment:1", ui.data().overrides.single().occurrenceKey)
        val ignored = ui.report().lines.single()
        assertTrue(ignored.ignored)
        assertFalse(ignored.included)
        ui.scroll("forecast-line:${first.itemId}:${first.occurrenceKey}")
        rule.onNodeWithText("Ignorada neste ciclo").assertExists()
        ui.action(ignored, "Restaurar somente ocorrência")
        ui.waitData { it.overrides.singleOrNull()?.ignored == false }
        assertEquals(10000L, ui.report().totalExpensesCents)
        assertEquals(10000L, ui.report(month.plusMonths(1)).totalExpensesCents)
        assertEquals(0L, ui.report(month.plusMonths(2)).totalExpensesCents)
        ui.assertNoRecords()
    }

    @Test fun stoppingARecurringForecastPreservesEarlierForecastsAndRecordedExpenses() {
        val previous = month.minusMonths(1)
        ui.seed(ForecastRule("conta-ficticia", previous.atDay(1), "Conta fictícia", ForecastKind.EXPENSE,
            5000, Category.HOME, ForecastFrequency.MONTHLY, previous.atDay(1)))
        val payment = Record("historico", 4500, Movement.EXPENSE, PaymentMethod.PIX, Category.HOME,
            "Carteira", "Histórico preservado", previous.atDay(8).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        runBlocking { assertTrue(ui.app.repository.save(payment, null)) }
        ui.action(ui.report().lines.single(), "Interromper futuras")
        ui.waitData { it.rules.any { version -> !version.active } }
        assertEquals(5000L, ui.report(previous).totalExpensesCents)
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(0L, ui.report(month.plusMonths(1)).totalExpensesCents)
        rule.onNodeWithTag("forecast-line:conta-ficticia:month:$month").assertDoesNotExist()
        assertEquals(listOf(payment), runBlocking { ui.app.repository.exportRecords() })
        assertEquals(4500L, Ledger.month(listOf(payment), previous, ZoneId.systemDefault()).expenses)
        assertEquals(0L, Ledger.month(listOf(payment), month, ZoneId.systemDefault()).expenses)
    }
}
