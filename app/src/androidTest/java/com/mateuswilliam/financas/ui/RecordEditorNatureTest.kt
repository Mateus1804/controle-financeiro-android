package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
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
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.runOnIdle {
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
}
