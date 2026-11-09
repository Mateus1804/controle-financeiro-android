package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.data.CapturePreferences
import com.mateuswilliam.financas.data.Appearance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RendlyIdentityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun optionalNameUpdatesImmediatelyPersistsAndCanBeRemoved() {
        val app = rule.activity.application as FinanceApplication
        app.settings.setDisplayName("")
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá! 👋🏼")
        app.settings.setDisplayName("  Ana Lúcia Silva  ")
        rule.waitForIdle()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá, Ana Lúcia Silva! 👋🏼")
        assertEquals("Ana Lúcia Silva", CapturePreferences(app).snapshot().displayName)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá, Ana Lúcia Silva! 👋🏼")
        assertTrue(runCatching { app.settings.setDisplayName("a".repeat(41)) }.isFailure)
        assertEquals("Ana Lúcia Silva", app.settings.snapshot().displayName)
        app.settings.setDisplayName("")
        rule.waitForIdle()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá! 👋🏼")
        for (theme in listOf(Appearance.LIGHT, Appearance.DARK, Appearance.SYSTEM)) {
            app.settings.setAppearance(theme); rule.waitForIdle()
            assertEquals(theme, CapturePreferences(app).snapshot().appearance)
        }
    }
    @Test fun captureFictionalHomeInBothThemes() = kotlinx.coroutines.runBlocking<Unit> {
        val app = rule.activity.application as FinanceApplication
        app.repository.clear()
        app.forecasts.initialize()
        app.settings.dismissNamePrompt()
        val date = java.time.LocalDate.now()
        app.forecasts.saveRule(com.mateuswilliam.financas.core.ForecastRule("rendly-income-example", date, "Renda fictícia", com.mateuswilliam.financas.core.ForecastKind.INCOME, 300000,
            com.mateuswilliam.financas.core.Category.SALARY, com.mateuswilliam.financas.core.ForecastFrequency.MONTHLY, date))
        app.forecasts.saveRule(com.mateuswilliam.financas.core.ForecastRule("rendly-expense-example", date, "Despesa fictícia", com.mateuswilliam.financas.core.ForecastKind.EXPENSE, 220000,
            com.mateuswilliam.financas.core.Category.HOME, com.mateuswilliam.financas.core.ForecastFrequency.MONTHLY, date))
        androidx.lifecycle.ViewModelProvider(rule.activity)[com.mateuswilliam.financas.ForecastViewModel::class.java].refresh()
        rule.waitUntil(10000) { rule.onAllNodesWithText(com.mateuswilliam.financas.core.Money.format(80000)).fetchSemanticsNodes().isNotEmpty() }
        val directory = java.io.File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        for (theme in listOf(Appearance.LIGHT, Appearance.DARK)) {
            app.settings.setAppearance(theme)
            rule.waitForIdle()
            val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(directory, "rendly-${theme.name.lowercase()}-ficticio.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    @Test fun firstOpeningCanBeSkippedAndIsNotRepeatedAfterRecreation() {
        val app = rule.activity.application as FinanceApplication
        app.getSharedPreferences("capture_settings", android.content.Context.MODE_PRIVATE).edit()
            .remove("display_name").putBoolean("name_prompt_seen", false).commit()
        rule.waitForIdle()
        rule.onNodeWithText("Como você quer ser chamado?").assertExists()
        rule.onNodeWithText("Agora não").performClick()
        assertTrue(CapturePreferences(app).snapshot().namePromptSeen)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Como você quer ser chamado?").assertDoesNotExist()
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá! 👋🏼")
    }

}
