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
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(screenshot)
        try {
            val folder = java.io.File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            java.io.File(folder, "diagnostico-grafico-fonte200.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { screenshot.recycle() }
        listOf("nome" to label, "valor" to amount, "percentual" to percentage).forEach { (field, node) ->
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts))
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { layout ->
                    val message = "TEXT_METRICS $field text=${layout.layoutInput.text.text} size=${layout.size} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} maxLines=${layout.layoutInput.maxLines} exceeded=${layout.multiParagraph.didExceedMaxLines} constraints=${layout.layoutInput.constraints} font=${layout.layoutInput.style.fontSize} lineHeight=${layout.layoutInput.style.lineHeight} lines=${layout.lineCount} right=${layout.getLineRight(0)} bottom=${layout.getLineBottom(layout.lineCount - 1)}"
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply { putString("stream", "\n$message\n") })
                }
                assertTrue("$field cortado em 320dp/200%: ${layouts.map { it.size to it.lineCount }}", layouts.none { it.hasVisualOverflow })
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
