package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CentralEntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monthlySalaryKeepsEnteredFieldsWithoutCreatingAnActualReceipt() {
        var seed: Record? = null
        var frequency: ForecastFrequency? = null
        var actualSaved = false
        val date = LocalDate.now().minusDays(1)
        compose.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, { _, _ -> actualSaved = true }, null, null,
                emptyList(), "Conta principal", { it }, initialMovement = Movement.INCOME,
                initialCategory = Category.SALARY, initialDescription = "Salário líquido", initialDate = date,
                realizedOnly = true, onOpenForecast = { record, selected -> seed = record; frequency = selected })
        } }
        compose.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("400000")
        compose.onNodeWithText("Única").performScrollTo().performClick()
        compose.onNodeWithText("Mensal").performClick()
        compose.onNodeWithText("Continuar previsão").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertFalse(actualSaved)
            assertEquals(ForecastFrequency.MONTHLY, frequency)
            assertEquals(400000L, seed?.amountCents)
            assertEquals("Salário líquido", seed?.description)
            assertEquals(Category.SALARY, seed?.category)
            assertEquals("Conta principal", seed?.bank)
            assertEquals(date, java.time.Instant.ofEpochMilli(seed!!.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate())
        }
    }

    @Test fun forecastReceivesAmountDateCategoryAndAccountWithoutRetyping() {
        var saved: ForecastRule? = null
        val start = LocalDate.of(2026, 12, 5)
        compose.setContent { FinanceTheme {
            ForecastEditor(null, null, BudgetPeriod(start, start.plusMonths(1)), false, false,
                {}, { saved = it }, {}, initialKind = ForecastKind.INCOME, initialTitle = "Freelancer",
                initialCategory = Category.SELF_EMPLOYED, initialFrequency = ForecastFrequency.MONTHLY,
                initialAmountCents = 125050, initialStartDate = start, initialAccount = "Conta principal")
        } }
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(125050L, saved?.amountCents)
            assertEquals(start, saved?.startDate)
            assertEquals(Category.SELF_EMPLOYED, saved?.category)
            assertEquals("Conta principal", saved?.account)
            assertEquals(ForecastFrequency.MONTHLY, saved?.frequency)
        }
    }

    @Test fun futureSingleEntryBecomesAForecastAndDoesNotChangeTheAccountBalance() {
        var selectedFrequency: ForecastFrequency? = null
        var seed: Record? = null
        var actualSaved = false
        compose.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, { _, _ -> actualSaved = true }, null, null,
                emptyList(), "Conta principal", { it }, initialMovement = Movement.EXPENSE,
                initialCategory = Category.HOME, initialDescription = "Aluguel", initialDate = LocalDate.now().plusDays(5),
                realizedOnly = true, onOpenForecast = { record, frequency -> seed = record; selectedFrequency = frequency })
        } }
        compose.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("120000")
        compose.onNodeWithText("Continuar previsão").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertFalse(actualSaved)
            assertEquals(ForecastFrequency.ONCE, selectedFrequency)
            assertEquals(120000L, seed?.amountCents)
            assertEquals("Aluguel", seed?.description)
        }
    }

    @Test fun incomeCanBePlannedInInstallmentsWithTheEnteredTotal() {
        var saved: ForecastRule? = null
        val start = LocalDate.of(2026, 12, 5)
        compose.setContent { FinanceTheme {
            ForecastEditor(null, null, BudgetPeriod(start, start.plusMonths(1)), false, false,
                {}, { saved = it }, {}, initialKind = ForecastKind.INCOME, initialTitle = "Projeto freelancer",
                initialCategory = Category.SELF_EMPLOYED, initialFrequency = ForecastFrequency.INSTALLMENTS,
                initialAmountCents = 10000, initialStartDate = start, initialAccount = "Conta principal")
        } }
        compose.onNodeWithTag("forecast-installment-count").performScrollTo().performTextInput("3")
        compose.onNodeWithTag("forecast-save").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(ForecastKind.INCOME, saved?.kind)
            assertEquals(ForecastFrequency.INSTALLMENTS, saved?.frequency)
            assertEquals(10000L, saved?.amountCents)
            assertEquals(3, saved?.installmentCount)
        }
    }

}
