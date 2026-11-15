package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class MonthLedgerScreenTest {
    @get:Rule val rule = createComposeRule()
    private val month = YearMonth.of(2026, 11)
    private val at = month.atDay(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun record(id: String, cents: Long, movement: Movement) = Record(id, cents, movement,
        PaymentMethod.PIX, if (movement == Movement.INCOME) Category.SALARY else Category.OTHER,
        "Carteira", id, at, if (movement == Movement.TRANSFER) "Banco" else "")
    @Test fun transferDoesNotChangeMonthlyResultAndRowsHaveDayHeading() {
        val rows = listOf(record("Salário", 100000, Movement.INCOME), record("Mercado", 30000, Movement.EXPENSE), record("Entre contas", 50000, Movement.TRANSFER))
        rule.setContent { FinanceTheme { MonthLedgerScreen(rows, month, {}, false, null, {}, emptyList(), null, {}, {}) } }
        rule.onNodeWithTag("month-net").assertTextContains(Money.format(70000), substring = true)
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("05/11/2026"))
        rule.onNodeWithText("05/11/2026").assertIsDisplayed()
        rule.onAllNodesWithTag("payday-budget").assertCountEquals(0)
    }
    @Test fun monthlyFiguresKeepLargeAmountsOnOneLine() {
        val income = 1234567890L
        val expense = 321000L
        rule.setContent { FinanceTheme { MonthLedgerScreen(listOf(record("Renda", income, Movement.INCOME), record("Gasto", expense, Movement.EXPENSE)),
            month, {}, false, null, {}, emptyList(), null, {}, {}) } }
        rule.onNode(hasText(Money.format(income)) and hasAnyAncestor(hasTestTag("month-income")), useUnmergedTree = true).assertStartAlignedTextFits("Receita mensal", singleLine = true)
        rule.onNode(hasText(Money.format(expense)) and hasAnyAncestor(hasTestTag("month-expenses")), useUnmergedTree = true).assertStartAlignedTextFits("Despesa mensal", singleLine = true)
        rule.onNode(hasText(Money.format(income - expense)) and hasAnyAncestor(hasTestTag("month-net")), useUnmergedTree = true).assertStartAlignedTextFits("Saldo mensal", singleLine = true)
    }
    @Test fun hiddenFiguresDoNotExposeAmounts() {
        rule.setContent { FinanceTheme { MonthLedgerScreen(listOf(record("Salário", 100000, Movement.INCOME)), month, {}, true, null, {}, emptyList(), null, {}, {}) } }
        rule.onNodeWithTag("month-net").assertTextContains("••••", substring = true)
        rule.onAllNodesWithText("R$ 1.000,00", substring = true).assertCountEquals(0)
    }
}
