package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordEditorNatureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun missingOriginalKeepsStoredIncomeReversalNature() {
        val refund = Record("estorno", 5000, Movement.REFUND, PaymentMethod.PIX,
            Category.SALARY, "Conta", "Devolução", 1_792_800_000_000L,
            originalRecordId = "original-ausente", reversalMovement = Movement.INCOME)
        var saved: Record? = null
        rule.setContent { FinanceTheme {
            RecordEditor(null, refund, false, {}, { saved = it }, null, null, emptyList(), "", { it })
        } }
        rule.onNode(hasText("Confirmar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.runOnIdle {
            assertNotNull(saved)
            assertEquals("original-ausente", saved?.originalRecordId)
            assertEquals(Movement.INCOME, saved?.reversalMovement)
            assertEquals(Category.SALARY, saved?.category)
        }
    }

    @Test fun templateStartsWithEmptyEditableAmount() {
        rule.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, {}, null, null, emptyList(), "Carteira", { it },
                initialDescription = "Aluguel", initialCategory = Category.HOME,
                initialRecurring = true, onSaveRecurring = { _, _ -> })
        } }
        rule.onNodeWithTag("amount-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("record-description").performScrollTo().assertTextContains("Aluguel")
        rule.onNodeWithText("Moradia").performScrollTo().assertExists()
    }

    @Test fun newIncomeDraftUsesIncomeCategoriesWhileLegacyEntriesArePreserved() {
        val draft = DraftEntity("draft-income", "source", "example.bank", "Conta", "Recebimento", "Salário", 5000,
            Movement.INCOME, PaymentMethod.PIX, Category.HOME, "Sugestão", 1_792_800_000_000L, 1_792_800_000_000L)
        rule.setContent { FinanceTheme {
            RecordEditor(draft, null, false, {}, {}, null, null, emptyList(), "", { it })
        } }
        rule.onNodeWithText("Outras receitas").performScrollTo().assertExists()
        rule.onNodeWithText("Moradia").assertDoesNotExist()
    }

    @Test fun firstManualEntryKeepsAnEditableWalletWithoutRequiringInitialSetup() {
        var saved: Record? = null
        rule.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, { saved = it }, null, null, emptyList(), "", { it })
        } }
        rule.onNodeWithTag("amount-input").performTextInput("100")
        rule.onNodeWithTag("record-description").performScrollTo().performTextInput("Compra")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(hasText("Confirmar") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.runOnIdle { assertEquals("Carteira", saved?.bank) }
    }

    @Test fun archivedWalletIsNotSuggestedAgain() {
        val wallet = AccountBalance(AccountOption("carteira", "Carteira", 0), true, null, null)
        rule.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, {}, null, null, emptyList(), "Carteira", { it }, balances = listOf(wallet))
        } }
        rule.onNodeWithText("Escolher conta").performScrollTo().assertExists()
        rule.onNodeWithText("Carteira").assertDoesNotExist()
    }

    @Test fun smartDefaultsDoNotReuseExpenseCategoriesFromLegacyIncome() {
        val old = Record("old-income", 1000, Movement.INCOME, PaymentMethod.PIX, Category.HOME, "Carteira", "Salário", 1_792_800_000_000L)
        rule.setContent { FinanceTheme {
            RecordEditor(null, null, false, {}, {}, null, null, emptyList(), "Carteira", { it }, initialMovement = Movement.INCOME, history = listOf(old))
        } }
        rule.onNodeWithTag("record-description").performScrollTo().performTextInput("Salário")
        rule.onNodeWithText("Outras receitas").performScrollTo().assertExists()
        rule.onNodeWithText("Moradia").assertDoesNotExist()
    }
}
