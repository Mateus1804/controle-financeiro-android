package com.mateuswilliam.financas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

    @Test fun emptyMonthShowsMessageAndNoLegend() = both({
        ExpenseChart(emptyList(), month, hidden = false) {}
    }) { rule.onNodeWithText("Nenhum gasto registrado neste período.").assertExists() }

    @Test fun manyCategoriesGroupSmallestInOthersAndHiddenHidesPercentages() {
        val records = Category.entries.take(8).mapIndexed { i, c -> expense("e$i", (800L - i * 90), c) }
        rule.setContent { FinanceTheme { ExpenseChart(records, month, hidden = true) {} } }
        rule.onAllNodesWithText("do total", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Percentual oculto").assertCountEquals(6)
        rule.onNodeWithText("Ver todos os 8 grupos").assertExists()
    }
    @Test fun smallSliceHasHonestPercentageAndExplainsVisualEnlargement() = both({
        ExpenseChart(listOf(expense("maior", 999999, Category.HOME), expense("menor", 1, Category.FOOD)), month, hidden = false) {}
    }) {
        rule.onNodeWithText("<0,01% do total", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Fatias muito pequenas foram ampliadas para leitura. Os percentuais da legenda continuam reais.").assertExists()
    }

    @Test fun percentageForegroundContrastsWithEverySliceInBothThemes() {
        var colors = emptyList<Color>()
        both({ colors = LocalFinanceColors.current.chart }) {
            rule.runOnIdle {
                assertTrue(colors.isNotEmpty())
                colors.forEach { background ->
                    val foreground = expensePercentageTextColor(background)
                    val luminances = listOf(foreground.luminance(), background.luminance()).sorted()
                    assertTrue("Contraste insuficiente na legenda", (luminances[1] + 0.05f) / (luminances[0] + 0.05f) >= 4.5f)
                }
            }
        }
    }

}
