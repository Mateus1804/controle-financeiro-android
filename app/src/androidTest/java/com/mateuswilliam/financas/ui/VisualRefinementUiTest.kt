package com.mateuswilliam.financas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/** Dados fictícios; cobre tema claro/escuro, salário estourado, valores ocultos e mês sem gastos. */
class VisualRefinementUiTest {
    @get:Rule val rule = createComposeRule()
    private val month = YearMonth.of(2026, 10)
    private val at = month.atDay(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun expense(id: String, cents: Long, category: Category) =
        Record(id, cents, Movement.EXPENSE, PaymentMethod.PIX, category, "Conta fictícia", "Teste", at)

    private fun both(content: @Composable () -> Unit, check: () -> Unit) {
        var dark by mutableStateOf(false)
        rule.setContent { FinanceTheme(darkTheme = dark) { content() } }
        check()
        dark = true; rule.waitForIdle()
        check()
    }

    @Test fun salaryExceededKeepsRealPercentageAbove100() = both({
        MonthlyResultCard(SalarySummary(100000, false, 125000, null), hidden = false) {}
    }) {
        rule.onNodeWithText("Gastos acima do salário").assertExists()
        rule.onNodeWithText("Salário utilizado: 125,00%").assertExists()
    }

    @Test fun salaryHiddenRevealsNoValuesOrPercentages() = both({
        MonthlyResultCard(SalarySummary(100000, false, 40000, 10000), hidden = true) {}
    }) {
        rule.onNodeWithText("Salário utilizado: ••••").assertExists()
        rule.onAllNodesWithText("40,00%", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun missingSalaryOffersRegistration() = both({
        MonthlyResultCard(SalarySummary(null, false, 40000, null), hidden = false) {}
    }) { rule.onNodeWithText("Informar salário líquido").assertHasClickAction() }

    @Test fun emptyMonthShowsMessageAndNoLegend() = both({
        ExpenseChart(emptyList(), month, hidden = false) {}
    }) { rule.onNodeWithText("Nenhuma despesa confirmada neste período.").assertExists() }

    @Test fun expenseRefundIsVisibleEvenWhenThereAreNoNewExpenses() = both({
        val refund = expense("refund", 10000, Category.FOOD).copy(movement = Movement.REFUND)
        ExpenseChart(listOf(refund), month, hidden = false) {}
    }) {
        rule.onNodeWithTag("expense-refunds").assertExists()
        rule.onNodeWithText("Estornos do período: R$", substring = true).assertExists()
    }

    @Test fun manyCategoriesGroupSmallestInOthersAndHiddenHidesPercentages() {
        val records = Category.entries.take(8).mapIndexed { i, c -> expense("e$i", (800L - i * 90), c) }
        rule.setContent { FinanceTheme { ExpenseChart(records, month, hidden = true) {} } }
        rule.onAllNodesWithText("do total", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Percentual oculto").assertCountEquals(6)
        rule.onNodeWithText("Ver todos os 8 grupos").assertExists()
    }
}
