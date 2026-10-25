package com.mateuswilliam.financas.ui

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.data.Appearance
import androidx.core.view.WindowCompat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Before fun reset() { runBlocking { app.repository.clear() }; app.settings.finishIntro(); rule.waitForIdle() }

    @Test fun manualEntryWorksWithoutNotificationAccess() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("4290")
        rule.onNodeWithText("Descrição").performTextInput("Mercado de exemplo")
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Novo lançamento").fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasText("Extrato") and hasClickAction()).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Mercado de exemplo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mercado de exemplo").assertIsDisplayed()
        assertEquals(4290L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @Test fun formattedPasteKeepsItsValueInReais() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("R$ 1.234,56")
        confirmEntry()
        assertEquals(123456L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun cursorSelectionReplacementAndBackspaceKeepCents() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        val field = rule.onNodeWithTag("amount-input")
        field.performTextInput("1234")
        field.performTextInputSelection(TextRange(2), relativeToOriginalText = true)
        field.performTextInput("9")
        field.performTextInputSelection(TextRange(1, 3), relativeToOriginalText = true)
        field.performTextInput("0")
        field.performTextInputSelection(TextRange(4), relativeToOriginalText = true)
        field.performKeyInput { pressKey(Key.Backspace) }
        confirmEntry()
        assertEquals(103L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @Test fun clearingTheAmountDoesNotSaveZeroOrReuseTheOldValue() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        val field = rule.onNodeWithTag("amount-input")
        field.performTextInput("1234")
        field.performTextClearance()
        rule.onNodeWithText("Descrição").performTextInput("Teste de valor apagado")
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
        field.performTextInput("1")
        confirmEntry()
        assertEquals(1L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @Test fun existingAmountIsNotScaledAgainWhenEditing() = runBlocking {
        app.repository.save(Record("editar-exemplo", 4290, Movement.EXPENSE, PaymentMethod.PIX,
            Category.FOOD, "Conta de exemplo", "Editar valor de exemplo", System.currentTimeMillis()), null)
        rule.onNode(hasText("Extrato") and hasClickAction()).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Editar valor de exemplo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Editar valor de exemplo").performClick()
        confirmEntry()
        assertEquals(4290L, app.repository.exportRecords().single().amountCents)
    }

    @Test fun appearancePersistsAfterActivityRecreationAndUpdatesSystemBars() {
        rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
        rule.onNodeWithText("Escuro").performClick()
        rule.runOnIdle {
            assertEquals(Appearance.DARK, app.settings.state.value.appearance)
            assertFalse(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars)
        }
        assertStatusBarBackground(dark = true)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
        rule.onNodeWithText("Escuro").assertIsSelected()
        screenshot("ajustes-escuro")
        rule.onNodeWithText("Claro").performClick()
        rule.onNodeWithText("Claro").assertIsSelected()
        rule.runOnIdle {
            assertEquals(Appearance.LIGHT, app.settings.state.value.appearance)
            assertTrue(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars)
        }
        assertStatusBarBackground(dark = false)
        screenshot("ajustes-claro")
        rule.onNodeWithText("Sistema").performClick()
        rule.onNodeWithText("Sistema").assertIsSelected()
    }

    @Test fun decliningDisclosureKeepsCaptureDisabled() {
        rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("Capturar notificações").performClick()
        rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
        rule.onNodeWithText("Agora não").performClick()
        rule.onNodeWithText("Captura desativada. O cadastro manual continua disponível.").assertIsDisplayed()
    }

    @Test fun screenshotsUseOnlyFictionalRecords() = runBlocking {
        val now = System.currentTimeMillis()
        listOf(
            Record("exemplo-1", 300000, Movement.INCOME, PaymentMethod.PIX, Category.OTHER, "Conta de exemplo", "Receita de exemplo", now),
            Record("exemplo-2", 6850, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Conta de exemplo", "Almoço de exemplo", now - 1000),
            Record("exemplo-3", 11990, Movement.EXPENSE, PaymentMethod.PIX, Category.TRANSPORT, "Conta de exemplo", "Combustível de exemplo", now - 2000),
        ).forEach { app.repository.save(it, null) }
        app.database.financeDao().insertDraft(DraftEntity("pendencia-exemplo", "captura-exemplo", "simulacao.local",
            "Banco de exemplo", "EXEMPLO — compra aprovada", "Compra aprovada de R$ 42,90 no supermercado de exemplo.",
            4290, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Confira os dados sugeridos antes de confirmar.", now, now - 60_000))
        rule.waitUntil(5000) { rule.onAllNodesWithText("Últimos lançamentos").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("Vamos conferir?").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        screenshot("resumo")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle()
        screenshot("resumo-escuro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
        rule.waitForIdle()
        screenshot("resumo-claro")
        rule.onNodeWithText("Revisar").performClick()
        rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
        screenshot("pendencias")
        rule.onNodeWithTag("review-pendencia-exemplo").performClick()
        rule.onNodeWithText("Conferir movimentação").assertIsDisplayed()
        screenshot("revisao")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle()
        screenshot("revisao-escuro")
    }


    @Test fun firstUseCanContinueManuallyWithoutConsent() {
        app.settings.reset()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar manualmente").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Usar manualmente").performClick()
        rule.runOnIdle { assertTrue(app.settings.state.value.introSeen); assertFalse(app.settings.state.value.consented) }
        rule.onNodeWithContentDescription("Novo lançamento").assertIsDisplayed()
    }

    @Test fun introductionDoesNotReplaceDisclosureConsent() {
        app.settings.reset()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Ativar preenchimento por notificações").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ativar preenchimento por notificações").performClick()
        rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
        rule.onNodeWithText("Agora não").performClick()
        rule.runOnIdle { assertFalse(app.settings.state.value.consented) }
    }

    @Test fun closedCaptureNoticeStaysClosedAfterRecreation() {
        rule.onNodeWithContentDescription("Fechar aviso de captura").performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { app.settings.state.value.captureCondition == "CONSENT" }
        rule.onAllNodesWithContentDescription("Fechar aviso de captura").assertCountEquals(0)
        rule.runOnIdle { assertFalse(app.settings.state.value.consented) }
    }

    @Test fun accountCanBeAddedWithoutLosingTheAmount() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        rule.onNodeWithContentDescription("Adicionar Conta ou banco de origem").performScrollTo().performClick()
        try {
            rule.waitUntil(5000) { rule.onAllNodesWithTag("account-name-input").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("account-name-input").performTextInput("Inter pessoal")
        } catch (failure: Throwable) {
            val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") { rule.onAllNodes(isRoot())[it].printToString() }
            throw AssertionError("O cadastro de conta não ficou disponível. Estado da interface:\n$roots", failure)
        }
        rule.onNodeWithText("Adicionar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Adicionar conta ou banco").fetchSemanticsNodes().isEmpty() }
        confirmEntry()
        val record = runBlocking { app.repository.exportRecords().single() }
        assertEquals(1234L, record.amountCents); assertEquals("Inter pessoal", record.bank)
    }

    @Test fun amountAndDescriptionSurviveFormRecreation() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        rule.onNodeWithText("Descrição").performTextInput("Formulário de exemplo")
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("amount-input").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Descrição").assertTextContains("Formulário de exemplo")
        confirmEntry()
        assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @Test fun chartOpensOnlyTheSelectedExpenseGroup() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        app.repository.save(Record("grafico-gasto", 10000, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta teste", "Despesa do gráfico", now), null)
        app.repository.save(Record("grafico-receita", 20000, Movement.INCOME, PaymentMethod.PIX, Category.FOOD,
            "Conta teste", "Receita excluída do gráfico", now), null)
        rule.waitUntil(5000) { rule.onAllNodesWithText(Money.format(10000)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("expense-group-FOOD"))
        screenshot("grafico-categorias")
        rule.onNodeWithTag("expense-group-FOOD").performClick()
        rule.onNodeWithText("Despesa do gráfico").assertIsDisplayed()
        rule.onAllNodesWithText("Receita excluída do gráfico").assertCountEquals(0)
    }


    @Test fun rejectedPasteShowsAnErrorAndCannotSaveThePreviousAmount() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        rule.onNodeWithTag("amount-input").performTextReplacement("1,234")
        rule.onNodeWithText("Entrada inválida ou acima do limite. Digite números ou cole um valor como 1.234,56.").assertExists()
        rule.onNodeWithText("Descrição").performTextInput("Teste de colagem inválida")
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }

    @Test fun largeTextAndOpenKeyboardAllowManualEntry() {
        fun fontScale(value: String) {
            val command = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("settings put system font_scale " + value)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        }
        try {
            fontScale("1.5")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.4f }
            rule.onNodeWithContentDescription("Novo lançamento").performClick()
            rule.onNodeWithTag("amount-input").performClick().performTextInput("1234")
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Fonte ampliada")
            screenshot("formulario-fonte-ampliada")
            confirmEntry()
            assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
        } finally { fontScale("1.0") }
    }

    @Test fun landscapeLayoutKeepsTheFormUsable() {
        try {
            rule.runOnIdle { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            rule.onNodeWithContentDescription("Novo lançamento").performClick()
            rule.onNodeWithTag("amount-input").performTextInput("1234")
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Paisagem")
            screenshot("formulario-paisagem")
            confirmEntry()
            assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
        } finally {
            rule.runOnIdle { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }


    @Test fun hiddenAmountsAlsoHideChartLabelsAndSemantics() = runBlocking<Unit> {
        app.repository.save(Record("privacidade-grafico", 123456, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta exemplo", "Exemplo privado", System.currentTimeMillis()), null)
        rule.waitUntil(5000) { rule.onAllNodesWithText(Money.format(123456)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ocultar").performClick()
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("expense-group-FOOD"))
        rule.onAllNodesWithText(Money.format(123456)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(Money.format(123456), substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("100,00%").assertCountEquals(0)
    }


    @Test fun notificationAccessRefreshesAfterResumeAndRevocationAcrossAlertPermissions() {
        val component = "com.mateuswilliam.financas/com.mateuswilliam.financas.notifications.BankNotificationListener"
        fun command(value: String): String {
            val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(value)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        fun resume() {
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            rule.waitForIdle()
        }
        val shellListenerCommand = command("cmd notification help").contains("allow_listener")
        val previousListeners = command("settings get secure enabled_notification_listeners").trim().takeUnless { it == "null" }.orEmpty()
        fun listenerAccess(allowed: Boolean) {
            if (shellListenerCommand) command("cmd notification " + (if (allowed) "allow_listener " else "disallow_listener ") + component)
            else {
                val values = previousListeners.split(':').filter { it.isNotBlank() && it != component }.toMutableList()
                if (allowed) values.add(component)
                if (values.isEmpty()) command("settings delete secure enabled_notification_listeners")
                else command("settings put secure enabled_notification_listeners " + values.joinToString(":"))
            }
        }
        try {
            // A API 26 não possui a permissão runtime da API 33 nem o comando allow_listener.
            if (android.os.Build.VERSION.SDK_INT >= 33)
                command("appops set " + app.packageName + " POST_NOTIFICATION deny")
            rule.runOnIdle {
                app.settings.acceptDisclosure()
                app.settings.selectPackages(setOf("banco.exemplo"))
            }
            listenerAccess(true)
            resume()
            rule.waitUntil(10000) { app.listenerConnected.value }
            rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Captura ativa para 1 aplicativo(s).").fetchSemanticsNodes().isNotEmpty() }
            val alertAction = rule.onNodeWithText("Permitir alertas de revisão")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                assertFalse(androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                alertAction.assertExists()
            } else {
                // Nessas versões não há POST_NOTIFICATIONS runtime; a ação reflete o bloqueio global efetivo do sistema.
                if (androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()) alertAction.assertDoesNotExist()
                else alertAction.assertExists()
            }
            listenerAccess(false)
            resume()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Falta autorizar o acesso às notificações no Android.").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Resumo").performClick()
            rule.onNodeWithText("Autorizar acesso").assertExists()
        } finally {
            listenerAccess(false)
            if (android.os.Build.VERSION.SDK_INT >= 33)
                command("appops set " + app.packageName + " POST_NOTIFICATION default")
            rule.runOnIdle { app.settings.setEnabled(false) }
        }
    }


    @Test fun choosingManualPausesPreviouslyEnabledCapture() {
        app.settings.reset()
        rule.runOnIdle {
            app.settings.acceptDisclosure()
            app.settings.selectPackages(setOf("banco.exemplo"))
        }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar manualmente").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Usar manualmente").performClick()
        rule.runOnIdle {
            assertTrue(app.settings.state.value.consented)
            assertFalse(app.settings.state.value.enabled)
            assertTrue(app.settings.state.value.introSeen)
        }
    }

    private fun confirmEntry() {
        rule.onNodeWithText("Descrição").performTextReplacement("Lançamento fictício de teste")
        rule.onNodeWithText("Descrição").performImeAction()
        rule.waitUntil(5000) {
            val windows = if (android.os.Build.VERSION.SDK_INT >= 29)
                android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            windows.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        rule.waitForIdle()
        val confirm = rule.onNodeWithText("Confirmar", useUnmergedTree = true)
        if (rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) confirm.performScrollTo()
        confirm.assertIsDisplayed().performClick()
        try {
            rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
            rule.waitUntil(5000) { rule.onAllNodesWithTag("amount-input").fetchSemanticsNodes().isEmpty() }
        } catch (failure: Throwable) {
            val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") {
                rule.onAllNodes(isRoot())[it].printToString()
            }
            throw AssertionError("A confirmação não terminou. Estado da interface:\n" + roots, failure)
        }
    }

    private fun assertStatusBarBackground(dark: Boolean) {
        rule.waitUntil(5000) {
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: return@waitUntil false
            val pixel = bitmap.getPixel(bitmap.width / 2, 2)
            bitmap.recycle()
            val brightness = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
            if (dark) brightness < 128 else brightness > 128
        }
    }

    private fun screenshot(name: String) {
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Não foi possível capturar a tela do emulador.")
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
