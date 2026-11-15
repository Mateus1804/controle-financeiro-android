package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercita a Activity entregue ao usuário, sem escolher uma interface alternativa. */
class RealizedEntryUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() {
        runBlocking { app.repository.clear() }
        app.settings.finishIntro(); app.settings.dismissNamePrompt()
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("add-movement") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    @Test fun quickIncomeStartsTheCorrectMovementAndKeepsIntegerCents() = enter(Movement.INCOME, "123456", 123456)
    @Test fun quickExpenseStartsTheCorrectMovementAndKeepsIntegerCents() = enter(Movement.EXPENSE, "1234", 1234)

    private fun enter(movement: Movement, input: String, expected: Long) {
        rule.newRendlyRecord(movement)
        rule.onNodeWithTag("amount-input").performTextInput(input)
        rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Lançamento fictício")
        rule.onNodeWithText("Descrição").performImeAction()
        val save = if (movement == Movement.INCOME) "Salvar lançamento" else "Registrar despesa"
        rule.onNode(hasText(save) and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val record = runBlocking { app.repository.exportRecords().single() }
        assertEquals(movement, record.movement)
        assertEquals(expected, record.amountCents)
        assertEquals("Lançamento fictício", record.description)
        assertFalse(app.settings.snapshot().consented)
    }
}
