package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RedesignInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() { runBlocking { app.repository.clear() }; app.settings.finishIntro(); rule.waitForIdle() }

    @Test fun quickIncomeStartsTheCorrectMovementAndKeepsIntegerCents() {
        openQuick("income")
        rule.onNodeWithTag("amount-input").performTextInput("123456")
        confirm()
        val saved = runBlocking { app.repository.exportRecords().single() }
        assertEquals(Movement.INCOME, saved.movement); assertEquals(123456L, saved.amountCents)
    }

    @Test fun quickExpenseStartsTheCorrectMovement() {
        openQuick("expense")
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        confirm()
        assertEquals(Movement.EXPENSE, runBlocking { app.repository.exportRecords().single().movement })
    }

    @Test fun quickTransferShowsDestinationAndCannotSaveWithoutOne() {
        openQuick("transfer")
        rule.onNodeWithTag("amount-input").performTextInput("2500")
        rule.onNodeWithText("Conta de destino").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Transferência fictícia")
        rule.onNodeWithText("Descrição").performImeAction(); waitKeyboard()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }

    @Test fun pendingAndEditorConcealCurrencyFromTheOriginalNotification() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        app.database.financeDao().insertDraft(DraftEntity("privado-ui", "fixture-ui", "simulacao.local", "Banco fictício",
            "Compra R$ 42,90", "Compra de R$ 42,90 no mercado fictício.", 4290, Movement.EXPENSE, PaymentMethod.CARD,
            Category.FOOD, "Valor de 42,90 identificado.", now, now - 60000))
        app.settings.hideAmounts(true)
        rule.onNode(hasText("Revisar") and hasClickAction()).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("review-privado-ui").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("review-privado-ui").performClick()
        rule.onAllNodesWithText("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("amount-input").assertCountEquals(0)
        rule.onNodeWithText("Mostrar valor para edição").performScrollTo().performClick()
        rule.onNodeWithTag("amount-input").assertExists()
    }

    @Test fun navigationKeepsFourReadableDestinationsAndTouchTargets() {
        for (label in listOf("Resumo", "Revisar", "Extrato", "Ajustes")) {
            rule.onNode(hasText(label) and hasClickAction()).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        }
        rule.onNodeWithContentDescription("Novo lançamento").assertIsDisplayed()
    }

    @Test fun narrowScreenWithDoubleFontScaleKeepsActionsAndNavigationReachable() {
        fun command(value: String): String {
            val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(value)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        }
        val previousSize = Regex("Override size: ([0-9]+x[0-9]+)").find(command("wm size"))?.groupValues?.get(1) ?: "reset"
        val previousDensity = Regex("Override density: ([0-9]+)").find(command("wm density"))?.groupValues?.get(1) ?: "reset"
        val previousFont = command("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        try {
            command("wm size 640x1280"); command("wm density 320"); command("settings put system font_scale 2.0")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.9f && rule.activity.resources.configuration.screenWidthDp <= 320 }
            for (label in listOf("Resumo", "Revisar", "Extrato", "Ajustes")) {
                rule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            }
            rule.onNode(hasText("Resumo") and hasClickAction()).performClick()
            for (type in listOf("income", "expense", "transfer")) {
                rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("quick-$type"))
                rule.onNodeWithTag("quick-$type").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            }
            rule.runOnIdle { app.settings.setAppearance(com.mateuswilliam.financas.data.Appearance.DARK) }
            rule.waitForIdle(); capture("resumo-fonte-ampliada-escuro")
            rule.runOnIdle { app.settings.setAppearance(com.mateuswilliam.financas.data.Appearance.LIGHT) }
            rule.waitForIdle(); capture("resumo-fonte-ampliada-claro")
            rule.onNodeWithContentDescription("Novo lançamento").performClick()
            rule.onNodeWithTag("amount-input").performTextInput("1234")
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Fonte ampliada fictícia")
            capture("formulario-pequeno-teclado")
            rule.onNodeWithText("Descrição").performImeAction(); waitKeyboard()
            rule.onNodeWithText("Confirmar", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
            rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
            assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
        } finally {
            command("settings put system font_scale $previousFont"); command("wm density $previousDensity"); command("wm size $previousSize")
        }
    }

    @Test fun introductionHasManualChoiceAndKeepsExplicitConsentInBothThemes() {
        app.settings.reset()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar manualmente").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { app.settings.setAppearance(com.mateuswilliam.financas.data.Appearance.LIGHT) }
        rule.waitForIdle(); capture("introducao-claro")
        rule.runOnIdle { app.settings.setAppearance(com.mateuswilliam.financas.data.Appearance.DARK) }
        rule.waitForIdle(); capture("introducao-escuro")
        rule.onNodeWithText("Ativar preenchimento por notificações").performClick()
        rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
        assertFalse(app.settings.state.value.consented)
        rule.onNodeWithText("Agora não").performClick()
        assertFalse(app.settings.state.value.consented)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Captura do emulador indisponível")
        try {
            val directory = java.io.File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun lastTransactionCanBeReadAboveTheFixedActionWithoutOcclusion() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        repeat(30) { index ->
            app.repository.save(Record("linha-$index", 10000L + index, Movement.EXPENSE, PaymentMethod.PIX,
                Category.FOOD, "Conta fictícia", "Lançamento fictício $index", now - index * 1000L), null)
        }
        rule.onNode(hasText("Extrato") and hasClickAction()).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("30 lançamento(s)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("records-list").performScrollToNode(hasText("Lançamento fictício 29"))
        val record = rule.onNodeWithText("Lançamento fictício 29").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithContentDescription("Novo lançamento").fetchSemanticsNode().boundsInRoot
        assertTrue("A ação fixa não pode cobrir a última linha do histórico", record.bottom <= action.top)
    }

    private fun openQuick(type: String) {
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("quick-$type"))
        rule.onNodeWithTag("quick-$type").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
    }
    private fun confirm() {
        rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Registro fictício")
        rule.onNodeWithText("Descrição").performImeAction(); waitKeyboard()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
    }
    private fun waitKeyboard() {
        rule.waitUntil(5000) {
            val windows = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            windows.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
    }
}
