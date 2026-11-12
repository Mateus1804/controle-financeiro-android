package com.mateuswilliam.financas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Dados fictícios; roda no emulador API 35. */
class PlanningImprovementsUiTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)

    @Test fun paydayCardShowsTotalWeeklyAverageAndDeficit() {
        val ok = PaydayBudget(today.plusDays(14), 14, 300000, 100000, 50000)
        val short = PaydayBudget(today.plusDays(7), 7, 50000, 60000, 10000)
        var budget by mutableStateOf<PaydayBudget?>(ok)
        rule.setContent { FinanceTheme { PaydayBudgetCard(budget, true, false) {} } }
        rule.onNodeWithTag("payday-total").assertTextContains(Money.format(150000))
        rule.onNodeWithTag("payday-weekly").assertExists()
        budget = short; rule.waitForIdle()
        rule.onNodeWithText("Déficit até o pagamento").assertExists()
        rule.onNodeWithTag("payday-weekly").assertDoesNotExist()
    }

    @Test fun paydayCardAsksForMissingPaydayAndHidesValues() {
        var clicked = false
        rule.setContent { FinanceTheme { PaydayBudgetCard(null, true, false) { clicked = true } } }
        rule.onNodeWithTag("payday-missing").performClick()
        assert(clicked)
    }

    @Test fun paydayCardHidesValuesWhenPrivate() {
        rule.setContent { FinanceTheme { PaydayBudgetCard(PaydayBudget(today.plusDays(5), 5, 90000, 1000, 0), true, true) {} } }
        rule.onAllNodesWithText("R$", substring = true).assertCountEquals(0)
    }

    @Test fun comparisonShowsSignedDifferenceAndOpensCategory() {
        val zone = ZoneId.systemDefault()
        fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val records = listOf(
            Record("a", 30000, Movement.EXPENSE, PaymentMethod.PIX, Category.TRANSPORT, "Conta", "Teste", at(LocalDate.of(2026, 10, 5))),
            Record("b", 42000, Movement.EXPENSE, PaymentMethod.PIX, Category.TRANSPORT, "Conta", "Teste", at(LocalDate.of(2026, 11, 5))))
        var opened: Category? = null
        rule.setContent { FinanceTheme { MonthComparisonCard(MonthComparisons.compare(records, YearMonth.of(2026, 11), LocalDate.of(2026, 12, 1), zone), false) { opened = it } } }
        rule.onNodeWithText("+${Money.format(12000)}").assertExists()
        rule.onNodeWithTag("comparison-TRANSPORT").performClick()
        assert(opened == Category.TRANSPORT)
    }
}
