package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class LargeFontChartUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun narrowChartWithDoubleFontKeepsLongLabelAmountAndPercentageReadable() {
        val month = YearMonth.of(2026, 10)
        val name = "Conta pessoal de uso familiar com identificação longa"
        val record = Record("grande-ficticio", Money.MAX_CENTS, Movement.EXPENSE, PaymentMethod.PIX,
            Category.HOME, name, "Dados fictícios", month.atDay(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        var hidden by mutableStateOf(false)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FinanceTheme {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        ExpenseChart(listOf(record), month, hidden) {}
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Agrupar por").performScrollTo().performClick()
        rule.onNodeWithText("Conta / banco").performClick()
        val tag = "expense-group-${AccountNames.key(name)}"
        val group = rule.onNodeWithTag(tag).performScrollTo().assertHasClickAction()
        val inGroup = hasAnyAncestor(hasTestTag(tag))
        val label = rule.onNode(hasText(name) and inGroup, useUnmergedTree = true)
        val amount = rule.onNode(hasText(Money.format(Money.MAX_CENTS)) and inGroup, useUnmergedTree = true)
        val percentage = rule.onNode(hasText("100,00% do total") and inGroup, useUnmergedTree = true)
        listOf(label, amount, percentage).forEach { node ->
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts))
                assertTrue(layouts.isNotEmpty())
                assertTrue("Texto não pode ser cortado em 320dp/200%", layouts.none { it.hasVisualOverflow })
            }
        }
        assertTrue(label.fetchSemanticsNode().boundsInRoot.bottom <= amount.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(amount.fetchSemanticsNode().boundsInRoot.bottom <= percentage.fetchSemanticsNode().boundsInRoot.top)
        group.assertHeightIsAtLeast(48.dp)
        rule.runOnIdle { hidden = true }
        rule.onAllNodesWithText(Money.format(Money.MAX_CENTS), substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(Money.format(Money.MAX_CENTS), substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("100,00%", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("Mapa de gastos oculto. Valores e percentuais não são lidos em voz alta.").assertExists()
    }
}
