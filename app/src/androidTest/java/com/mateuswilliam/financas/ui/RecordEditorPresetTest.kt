package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordEditorPresetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun recurringExpenseKeepsItsMovementWhenTheAmountIsCorrected() {
        confirmWithCorrectedAmount(Movement.EXPENSE)
    }

    @Test fun recurringIncomeKeepsItsMovementWhenTheAmountIsCorrected() {
        confirmWithCorrectedAmount(Movement.INCOME)
    }

    private fun confirmWithCorrectedAmount(movement: Movement) {
        val forecast = Record("previsão-fictícia", 4290, movement, PaymentMethod.PIX,
            Category.OTHER, "Conta fictícia", "Previsão fictícia", 1_792_800_000_000L)
        var saved: Record? = null
        rule.setContent { FinanceTheme {
            RecordEditor(draft = null, existing = null, preset = forecast, busy = false,
                onDismiss = {}, onSave = { saved = it }, onIgnore = null, onDelete = null,
                accounts = emptyList(), lastOrigin = "", onAddAccount = { it })
        } }

        rule.onNodeWithText("Movimentação").assertDoesNotExist()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("5000")
        rule.onNodeWithTag("amount-input").performImeAction()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.runOnIdle {
            assertEquals(movement, saved?.movement)
            assertEquals(5000L, saved?.amountCents)
            assertEquals(forecast.occurredAt, saved?.occurredAt)
        }
    }
}
