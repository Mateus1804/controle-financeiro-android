package com.mateuswilliam.financas.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.core.Money
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FinanceDesignTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dynamicColorsFollowTheSelectedModeAndFallbackOnOlderAndroid() {
        var dark by mutableStateOf(false)
        var primary = Color.Unspecified
        rule.setContent { FinanceTheme(darkTheme = dark) {
            val actual = MaterialTheme.colorScheme.primary
            SideEffect { primary = actual }
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.runOnIdle { assertEquals(if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context).primary else Color(0xFF54558B), primary) }
        rule.runOnIdle { dark = true }
        rule.runOnIdle { assertEquals(if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context).primary else Color(0xFFC6C0F5), primary) }
    }

    @Test fun fallbackAndDynamicTextPairsHaveReadableContrastInBothModes() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var pairs = emptyList<Pair<Color, Color>>()
        rule.setContent { FinanceTheme(darkTheme = dark, dynamicColor = dynamic) {
            val c = MaterialTheme.colorScheme
            val semantic = LocalFinanceColors.current
            SideEffect { pairs = listOf(c.onBackground to c.background, c.onSurface to c.surfaceContainerLowest,
                c.onPrimaryContainer to c.primaryContainer, c.onSurfaceVariant to c.surfaceContainerLow,
                semantic.income to c.surfaceContainerLowest, semantic.expense to c.surfaceContainerLowest) }
        } }
        for (useDynamic in listOf(false, true)) for (useDark in listOf(false, true)) {
            rule.runOnIdle { dark = useDark; dynamic = useDynamic }
            rule.runOnIdle { pairs.forEach { (foreground, background) ->
                val ratio = (maxOf(foreground.luminance(), background.luminance()) + .05f) /
                    (minOf(foreground.luminance(), background.luminance()) + .05f)
                assertTrue("Contraste $ratio, dinâmico=$useDynamic, escuro=$useDark", ratio >= 4.5f)
            } }
        }
    }

    @Test fun maximumAmountFitsNarrowSurfaceAtDoubleFontScaleWithoutDroppingDigits() {
        rule.setContent { FinanceTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(272.dp)) { Amount(Money.MAX_CENTS, false, Modifier.testTag("long-amount"), 40) }
            }
        } }
        rule.onNodeWithTag("long-amount").assertTextEquals(Money.format(Money.MAX_CENTS))
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("long-amount").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("O valor não pode ser cortado", layouts.single().hasVisualOverflow)
    }

    @Test fun hiddenAmountDoesNotExposeItsValueToAccessibility() {
        rule.setContent { FinanceTheme { Amount(123456, true, Modifier.testTag("hidden-amount")) } }
        rule.onNodeWithTag("hidden-amount").assertTextEquals("••••")
        rule.onAllNodesWithText(Money.format(123456), substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun freeTextMaskKeepsDatesAndDescriptionsAndNeverChangesStoredText() {
        val original = "Compra de R$ 1.234,56 em 25/10/2026 às 10:30; Pix 42,90 e BRL 12."
        assertEquals("Compra de •••• em 25/10/2026 às 10:30; Pix •••• e ••••.", privateDisplayText(original, true))
        assertEquals(original, privateDisplayText(original, false))
        assertEquals("Parcela 2 de 12, em 25/10/2026", privateDisplayText("Parcela 2 de 12, em 25/10/2026", true))
    }

    @Test fun concealedEditorRequiresExplicitRevealAndCanHideAgain() {
        rule.setContent { FinanceTheme { PrivateValueGate(true, "Mostrar valor para edição") {
            androidx.compose.material3.Text(Money.format(4290))
        } } }
        rule.onAllNodesWithText(Money.format(4290)).assertCountEquals(0)
        rule.onNodeWithText("Mostrar valor para edição").performClick()
        rule.onNodeWithText(Money.format(4290)).assertIsDisplayed()
        rule.onNodeWithText("Ocultar novamente").performClick()
        rule.onAllNodesWithText(Money.format(4290)).assertCountEquals(0)
    }
}
