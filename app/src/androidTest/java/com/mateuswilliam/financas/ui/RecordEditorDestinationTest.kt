package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordEditorDestinationTest {
    @get:Rule val rule = createComposeRule()
    private var saved: Record? = null
    private val transfer = Record("transferência-fictícia", 4290, Movement.TRANSFER, PaymentMethod.PIX,
        Category.OTHER, "Conta de origem", "Transferência fictícia", 1_792_800_000_000L, "Conta de destino")

    @Test fun convertingATransferToExpenseDoesNotSaveTheHiddenDestination() {
        showEditor(transfer)
        chooseMovement("Transferência", "Despesa")
        confirm()
        rule.runOnIdle {
            assertEquals(Movement.EXPENSE, saved?.movement)
            assertEquals("", saved?.destination)
        }
    }

    @Test fun savingALegacyIncomeClearsItsResidualDestination() {
        showEditor(transfer.copy(movement = Movement.INCOME))
        confirm()
        rule.runOnIdle {
            assertEquals(Movement.INCOME, saved?.movement)
            assertEquals("", saved?.destination)
        }
    }

    @Test fun switchingBackToTransferRequiresSelectingDestinationAgain() {
        showEditor(transfer)
        chooseMovement("Transferência", "Despesa")
        chooseMovement("Despesa", "Transferência")
        rule.onNodeWithText("Conta de destino").performScrollTo()
        confirm()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Informe a conta de destino.").performScrollTo().assertIsDisplayed()
    }

    private fun showEditor(record: Record) {
        rule.setContent { FinanceTheme {
            RecordEditor(draft = null, existing = record, busy = false, onDismiss = {}, onSave = { saved = it },
                onIgnore = null, onDelete = null, accounts = emptyList(), lastOrigin = "", onAddAccount = { it })
        } }
    }

    private fun chooseMovement(current: String, next: String) {
        rule.onNodeWithText(current).performScrollTo().performClick()
        rule.onNodeWithText(next).performClick()
    }

    private fun confirm() {
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
    }
}
