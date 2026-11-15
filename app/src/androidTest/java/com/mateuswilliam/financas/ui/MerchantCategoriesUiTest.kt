package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.MerchantCategoryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MerchantCategoriesUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() { rule.resetReviewBackupMain(); app.merchantCategories.clear() }
    @After fun clear() { app.merchantCategories.clear() }

    @Test fun settingsCorrectAndForgetFutureSuggestionsWithoutRewritingHistory() = runBlocking<Unit> {
        val previous = Record("mercado-ficticio", 1234, Movement.EXPENSE, PaymentMethod.CARD,
            Category.FOOD, "Conta fictícia", "Histórico preservado", System.currentTimeMillis())
        app.repository.save(previous, null)
        app.merchantCategories.remember("mercado ficticio", Category.FOOD)
        rule.openRendlySettings()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("merchant-categories-settings"))
        rule.onNodeWithTag("merchant-categories-settings").performClick()
        rule.onNodeWithTag("merchant-actions-mercado ficticio").performClick()
        rule.onNodeWithText("Corrigir categoria").performClick()
        rule.onNodeWithContentDescription("Categoria lembrada").performClick()
        rule.onNodeWithText(Category.HOME.label).performClick()
        rule.onNodeWithTag("save-merchant-category").performClick()
        rule.waitUntil(5000) { app.merchantCategories.get("mercado ficticio") == Category.HOME }
        assertEquals(Category.HOME, MerchantCategoryStore(app).get("mercado ficticio"))
        assertEquals(listOf(previous), app.repository.exportRecords())
        rule.onNodeWithTag("merchant-actions-mercado ficticio").performClick()
        rule.onNodeWithText("Esquecer categoria").performClick()
        rule.onNodeWithText("Nenhuma categoria lembrada.").assertExists()
        assertNull(MerchantCategoryStore(app).get("mercado ficticio"))
        assertEquals(listOf(previous), app.repository.exportRecords())
    }
}
