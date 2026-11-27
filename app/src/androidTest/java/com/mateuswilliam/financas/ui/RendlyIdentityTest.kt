package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RendlyIdentityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Test fun nameRemainsOptionalAndPersistsWhenSetInSettings() {
        runBlocking { app.repository.clear() }
        app.settings.reset()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("sheet-summary").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Como você quer ser chamado?").assertDoesNotExist()
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithTag("display-name-settings").performClick()
        rule.onNodeWithTag("display-name-input").performTextInput("  Júlia Maria  ")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Salvar").performClick()
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá, Júlia Maria! 👋🏼")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá, Júlia Maria! 👋🏼")
        assertEquals("Júlia Maria", app.settings.snapshot().displayName)
    }
}
