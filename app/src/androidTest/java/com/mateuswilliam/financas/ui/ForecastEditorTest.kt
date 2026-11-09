package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ForecastEditorTest {
    @get:Rule val compose = createComposeRule()
    private val period = BudgetPeriod(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5))

    @Test fun salaryTemplateNeedsAmountAndSavesMonthlyWithoutAccount() {
        var saved: ForecastRule? = null
        compose.setContent { FinanceTheme {
            ForecastEditor(null, null, period, false, false, {}, { saved = it }, {},
                initialKind = ForecastKind.INCOME, initialTitle = "Salário")
        } }
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("300000")
        compose.onNodeWithContentDescription("Valor estimado").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle {
            assertEquals(300000L, saved?.amountCents)
            assertEquals(ForecastKind.INCOME, saved?.kind)
            assertEquals(ForecastFrequency.MONTHLY, saved?.frequency)
            assertEquals(Category.SALARY, saved?.category)
            assertEquals("", saved?.account)
            assertEquals(true, saved?.estimated)
        }
    }

    @Test fun installmentOccurrenceUsesInstallmentAmountAndStableKey() {
        val source = ForecastRule("purchase", period.start, "Compra", ForecastKind.EXPENSE,
            10000, Category.OTHER, ForecastFrequency.INSTALLMENTS, period.start, installmentCount = 3)
        val line = ForecastLine("purchase", "installment:1", "Compra", period.start, 3334,
            source.kind, source.category, source.group, source.frequency, false, false, true, "", false, source)
        var saved: ForecastOverride? = null
        compose.setContent { FinanceTheme {
            ForecastEditor(source, line, period, false, false, {}, { fail("Must save only an override") }, { saved = it })
        } }
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle {
            assertEquals(3334L, saved?.amountCents)
            assertEquals("installment:1", saved?.occurrenceKey)
        }
    }

    @Test fun occurrenceEditPreservesActivationOverrideForArchivedRule() {
        val source = ForecastRule("legacy", period.start, "Histórico", ForecastKind.INCOME,
            300000, Category.SALARY, ForecastFrequency.MONTHLY, period.start, active = false)
        val original = ForecastOverride("legacy", "month:2026-11", active = true)
        val line = ForecastLine("legacy", original.occurrenceKey, source.title, period.start, 300000,
            source.kind, source.category, source.group, source.frequency, false, false, true, "", false, source)
        var saved: ForecastOverride? = null
        compose.setContent { FinanceTheme {
            ForecastEditor(source, line, period, false, false, {}, { fail("Must preserve occurrence scope") },
                { saved = it }, existingOverride = original)
        } }
        compose.onNodeWithTag("forecast-amount").performScrollTo().performTextReplacement("285000")
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle {
            assertEquals(true, saved?.active)
            assertEquals(285000L, saved?.amountCents)
            assertEquals(original.occurrenceKey, saved?.occurrenceKey)
        }
    }

    @Test fun hiddenImportedTitleDoesNotExposeCurrencyUntilRevealed() {
        val source = ForecastRule("private", period.start, "Compra R$ 1.234,56", ForecastKind.EXPENSE,
            1000, Category.HOME, ForecastFrequency.MONTHLY, period.start,
            account = "Conta R$ 900,00", statementKey = "Fatura R$ 500,00")
        compose.setContent { FinanceTheme {
            ForecastEditor(source, null, period, false, true, {}, {}, {}, knownStatements = listOf(source.statementKey))
        } }
        compose.onNodeWithTag("forecast-title").assertDoesNotExist()
        compose.onNodeWithText("Compra R$ 1.234,56").assertDoesNotExist()
        compose.onNodeWithText("Mostrar descrição da previsão para edição").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithTag("forecast-title").assertExists()
        compose.onNodeWithTag("forecast-details").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText(source.account).assertDoesNotExist()
        compose.onNodeWithText(source.statementKey).assertDoesNotExist()
        compose.onNodeWithText("Mostrar conta da previsão para edição").assertExists()
        compose.onNodeWithText("Mostrar vínculo da fatura para edição").assertExists()
    }

    @Test fun reserveCannotWithdrawMoreThanKnownBalance() {
        var saved = false
        compose.setContent { FinanceTheme {
            ReserveEditor(ReserveEventKind.WITHDRAW, 1000, false, false, {}, { _, _, _ -> saved = true })
        } }
        compose.onNodeWithTag("reserve-amount").performScrollTo().performTextReplacement("1001")
        compose.onNodeWithTag("reserve-reason").performScrollTo().performTextInput("Emergência")
        compose.onNodeWithTag("reserve-save").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { assertFalse(saved) }
        compose.onNodeWithText("A retirada não pode superar o saldo da reserva.").performScrollTo().assertExists()
    }
}
