package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.Appearance
import com.mateuswilliam.financas.data.DraftEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RedesignInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Before fun reset() {
        runBlocking { app.repository.clear(); Unit }
        app.settings.finishIntro(); app.settings.dismissNamePrompt()
        waitForEnabled(hasTestTag("add-movement"))
        rule.waitUntil(10000) { rule.onAllNodesWithTag("forecast-dashboard").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    @Test fun transferSelectedInTheEditorShowsDestinationAndCannotSaveWithoutOne() {
        runBlocking { app.repository.addAccount("Origem fictícia") }
        app.settings.rememberOrigin("Origem fictícia")
        openExpense()
        rule.openRecordDetails()
        rule.onNodeWithContentDescription("Movimentação").performScrollTo().assertIsEnabled().performClick()
        rule.onNode(hasText("Transferência") and hasClickAction()).performClick()
        rule.onNodeWithTag("amount-input").performScrollTo().performTextInput("2500")
        rule.onNodeWithTag("amount-input").performImeAction()
        waitKeyboard()
        rule.onNodeWithText("Conta de destino").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Transferência fictícia")
        rule.onNodeWithText("Descrição").performImeAction(); waitKeyboard()
        waitForEnabled(hasText("Salvar lançamento") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.onNodeWithText("Informe a conta de destino.").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }

    @Test fun pendingAndEditorConcealCurrencyFromTheOriginalNotification() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        app.database.financeDao().insertDraft(DraftEntity("privado-ui", "fixture-ui", "simulacao.local", "Banco fictício",
            "Compra R$ 42,90", "Compra de R$ 42,90 no mercado fictício.", 4290, Movement.EXPENSE, PaymentMethod.CARD,
            Category.FOOD, "Valor de 42,90 identificado.", now, now - 60000))
        app.settings.hideAmounts(true)
        rule.openRendlyReview()
        waitForEnabled(hasTestTag("review-privado-ui"))
        rule.onAllNodesWithText("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("review-privado-ui").performClick()
        rule.onAllNodesWithText("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("42,90", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("amount-input").assertCountEquals(0)
        rule.onNodeWithText("Mostrar valor para edição").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("amount-input").assertExists()
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun plannerNavigationAndReviewSettingsKeepReadableTouchTargets() {
        for (label in listOf("Mês", "Gráficos", "Fixas e parcelas", "Mais")) {
            waitForEnabled(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
        }
        rule.openRendlyReview()
        rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
        rule.openRendlySettings()
        rule.onNodeWithTag("settings-list").assertExists()
        rule.openRendlyMonth()
        waitForEnabled(hasTestTag("add-movement")).assertHeightIsAtLeast(48.dp)
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
            checkpoint("Aplicando tela estreita e fonte 200%")
            command("wm size 640x1280"); command("wm density 320"); command("settings put system font_scale 2.0")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.9f && rule.activity.resources.configuration.screenWidthDp <= 320 }
            checkpoint("Conferindo navegação com fonte ampliada")
            for (label in listOf("Mês", "Gráficos", "Fixas e parcelas", "Mais", "Mês")) {
                val tab = waitForEnabled(hasText(label) and hasClickAction()).performScrollTo()
                rule.onNodeWithText(label, useUnmergedTree = true).assertStartAlignedTextFits(label)
                tab.assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            }
            rule.openRendlyReview()
            rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
            rule.openRendlySettings()
            rule.onNodeWithTag("settings-list").assertExists()
            rule.openRendlyMonth()
            waitForEnabled(hasTestTag("add-movement")).assertHeightIsAtLeast(48.dp)
            rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
            checkpoint("Capturando orçamento escuro com fonte ampliada")
            rule.waitForIdle(); capture("orcamento-fonte-ampliada-escuro")
            rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
            rule.waitForIdle(); capture("orcamento-fonte-ampliada-claro")
            checkpoint("Abrindo formulário em tela estreita")
            openExpense()
            rule.onNodeWithTag("amount-input").performTextInput("1234")
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Fonte ampliada fictícia")
            capture("formulario-pequeno-teclado")
            rule.onNodeWithText("Descrição").performImeAction(); waitKeyboard()
            waitForEnabled(hasText("Registrar despesa") and hasClickAction())
                .performScrollTo().assertIsDisplayed().performClick()
            rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
            val saved = runBlocking { app.repository.exportRecords().single() }
            assertEquals(1234L, saved.amountCents)
            assertEquals(Movement.EXPENSE, saved.movement)
            assertEquals("Fonte ampliada fictícia", saved.description)
        } finally {
            checkpoint("Restaurando configuração do emulador")
            command("settings put system font_scale $previousFont"); command("wm density $previousDensity"); command("wm size $previousSize")
        }
    }

    @Test fun optionalNameCanBeSkippedAndCaptureStillNeedsConsentInBothThemes() {
        app.settings.reset()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("display-name-input").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Como você quer ser chamado?").assertIsDisplayed()
        for (theme in listOf(Appearance.LIGHT, Appearance.DARK)) {
            rule.runOnIdle { app.settings.setAppearance(theme) }
            rule.waitForIdle(); capture("nome-opcional-${theme.name.lowercase()}")
            assertFalse(app.settings.snapshot().consented)
            assertFalse(app.settings.snapshot().enabled)
        }
        rule.onNodeWithText("Agora não").performClick()
        assertTrue(app.settings.snapshot().namePromptSeen)
        waitForEnabled(hasTestTag("add-movement")).assertIsDisplayed()
        rule.openRendlySettings()
        for (theme in listOf(Appearance.LIGHT, Appearance.DARK)) {
            rule.runOnIdle { app.settings.setAppearance(theme) }
            rule.onNodeWithContentDescription("Capturar notificações").performScrollTo().assertIsEnabled().performClick()
            rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
            rule.waitForIdle(); capture("consentimento-${theme.name.lowercase()}")
            assertFalse(app.settings.snapshot().consented)
            assertFalse(app.settings.snapshot().enabled)
            rule.onNodeWithText("Agora não").performClick()
            assertFalse(app.settings.snapshot().consented)
            assertFalse(app.settings.snapshot().enabled)
        }
    }

    @Test fun lastTransactionCanBeReadAboveTheFixedActionWithoutOcclusion() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        repeat(30) { index ->
            app.repository.save(Record("linha-$index", 10000L + index, Movement.EXPENSE, PaymentMethod.PIX,
                Category.FOOD, "Conta fictícia", "Lançamento fictício $index", now - index * 1000L), null)
        }
        rule.openRendlyMonth()
        assertEquals(30, app.repository.exportRecords().size)
        rule.waitUntil(10000) {
            try {
                rule.onNodeWithTag("records-list").performScrollToNode(hasText("Lançamento fictício 29"))
                rule.onNodeWithText("Lançamento fictício 29").assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
        }
        val record = rule.onNodeWithText("Lançamento fictício 29").fetchSemanticsNode().boundsInRoot
        val action = waitForEnabled(hasTestTag("add-movement")).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("A ação fixa não pode cobrir a última linha do histórico", record.bottom <= action.top)
        capture("extrato-ultima-linha")
    }

    private fun openExpense() = rule.newRendlyRecord()

    private fun waitForEnabled(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.waitUntil(10000) { rule.onAllNodes(matcher and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        return rule.onNode(matcher).assertIsEnabled()
    }

    private fun checkpoint(message: String) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\n[redesign] $message\n")
        })
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { task -> Thread(task, "redesign-capture").apply { isDaemon = true } }
        try {
            val bitmap = executor.submit<android.graphics.Bitmap> {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?: error("Captura do emulador indisponível")
            }.get(15, java.util.concurrent.TimeUnit.SECONDS)
            try {
                val directory = java.io.File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        } finally { executor.shutdownNow() }
    }

    private fun waitKeyboard() {
        rule.waitUntil(5000) {
            val windows = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            windows.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
    }
}
