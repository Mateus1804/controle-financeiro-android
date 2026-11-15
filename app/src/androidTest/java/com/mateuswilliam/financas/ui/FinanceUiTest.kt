package com.mateuswilliam.financas.ui

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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

    @Before fun reset() {
        runBlocking { app.maintenanceReady.await(); app.repository.clear(); Unit }
        app.settings.finishIntro()
        app.settings.dismissNamePrompt()
        waitForDashboard()
    }

    @Test fun manualEntryWorksWithoutNotificationAccess() {
        openExpense()
        rule.onNodeWithTag("amount-input").performTextInput("4290")
        confirmEntry("Mercado de exemplo")
        openStatement()
        scrollRecordsTo("Mercado de exemplo")
        rule.onNodeWithText("Mercado de exemplo").assertIsDisplayed()
        val saved = runBlocking { app.repository.exportRecords().single() }
        assertEquals(4290L, saved.amountCents)
        assertEquals(Movement.EXPENSE, saved.movement)
        assertFalse(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
    }

    @Test fun formattedPasteKeepsItsValueInReais() {
        openExpense()
        rule.onNodeWithTag("amount-input").performTextInput("R$ 1.234,56")
        confirmEntry()
        assertEquals(123456L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun cursorSelectionReplacementAndBackspaceKeepCents() {
        openExpense()
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
        openExpense()
        val field = rule.onNodeWithTag("amount-input")
        field.performTextInput("1234")
        field.performTextClearance()
        rule.onNodeWithText("Descrição").performTextInput("Teste de valor apagado")
        clickEnabled(hasText("Registrar despesa") and hasClickAction())
        rule.onNodeWithText("Informe um valor maior que zero.").assertExists()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
        field.assertExists().performTextInput("1")
        confirmEntry()
        assertEquals(1L, runBlocking { app.repository.exportRecords().single().amountCents })
    }

    @Test fun existingAmountIsNotScaledAgainWhenEditing() = runBlocking {
        app.repository.save(Record("editar-exemplo", 4290, Movement.EXPENSE, PaymentMethod.PIX,
            Category.FOOD, "Conta de exemplo", "Editar valor de exemplo", System.currentTimeMillis()), null)
        openStatement()
        scrollRecordsTo("Editar valor de exemplo")
        rule.onNodeWithText("Editar valor de exemplo").performClick()
        confirmEntry(saveLabel = "Salvar lançamento")
        assertEquals(4290L, app.repository.exportRecords().single().amountCents)
    }

    @Test fun appearancePersistsAfterActivityRecreationAndUpdatesSystemBars() {
        openSettings()
        clickEnabled(hasText("Escuro") and hasClickAction())
        rule.runOnIdle {
            assertEquals(Appearance.DARK, app.settings.state.value.appearance)
            assertFalse(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars)
            assertFalse(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightNavigationBars)
        }
        assertStatusBarBackground(dark = true)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        openSettings()
        rule.onNodeWithText("Escuro").assertIsSelected()
        screenshot("ajustes-escuro")
        clickEnabled(hasText("Claro") and hasClickAction())
        rule.onNodeWithText("Claro").assertIsSelected()
        rule.runOnIdle {
            assertEquals(Appearance.LIGHT, app.settings.state.value.appearance)
            assertTrue(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars)
            assertTrue(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightNavigationBars)
        }
        assertStatusBarBackground(dark = false)
        screenshot("ajustes-claro")
        clickEnabled(hasText("Sistema") and hasClickAction())
        rule.onNodeWithText("Sistema").assertIsSelected()
    }

    @Test fun decliningDisclosureKeepsCaptureDisabled() {
        openSettings()
        clickEnabled(hasContentDescription("Capturar notificações"))
        rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
        clickEnabled(hasText("Agora não") and hasClickAction())
        rule.onNodeWithText("Captura desativada. O cadastro manual continua disponível.").assertIsDisplayed()
    }

    @Test fun screenshotsUseOnlyFictionalRecords() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val examples = listOf(
            Record("exemplo-1", 300000, Movement.INCOME, PaymentMethod.PIX, Category.SALARY, "Conta de exemplo", "Receita de exemplo", now),
            Record("exemplo-2", 6850, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Conta de exemplo", "Almoço de exemplo", now - 1000),
            Record("exemplo-3", 11990, Movement.EXPENSE, PaymentMethod.PIX, Category.TRANSPORT, "Conta de exemplo", "Combustível de exemplo", now - 2000),
        )
        examples.forEach { app.repository.save(it, null) }
        app.database.financeDao().insertDraft(DraftEntity("pendencia-exemplo", "captura-exemplo", "simulacao.local",
            "Banco de exemplo", "EXEMPLO — compra aprovada", "Compra aprovada de R$ 42,90 no supermercado de exemplo.",
            4290, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Confira os dados sugeridos antes de confirmar.", now, now - 60_000))
        rule.waitUntil(10000) {
            rule.onAllNodes(hasTestTag("month-net") and hasText("2.811,60", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("resumo")
        for (theme in listOf(Appearance.DARK, Appearance.LIGHT)) {
            rule.runOnIdle { app.settings.setAppearance(theme) }
            screenshot("resumo-${if (theme == Appearance.DARK) "escuro" else "claro"}")
        }
        openStatement()
        for (theme in listOf(Appearance.DARK, Appearance.LIGHT)) {
            rule.runOnIdle { app.settings.setAppearance(theme) }
            rule.onNodeWithTag("records-list").performScrollToNode(hasText("Combustível de exemplo"))
            rule.onNodeWithText("Combustível de exemplo").assertIsDisplayed()
            screenshot("extrato-${if (theme == Appearance.DARK) "escuro" else "claro"}")
        }
        rule.openRendlyReview()
        rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
        screenshot("pendencias")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        screenshot("pendencias-escuro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
        clickEnabled(hasTestTag("review-pendencia-exemplo"))
        rule.onNodeWithText("Conferir movimentação").assertIsDisplayed()
        screenshot("revisao")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        screenshot("revisao-escuro")
        assertEquals(examples.map { it.id }.toSet(), app.repository.exportRecords().map { it.id }.toSet())
        assertEquals(1, app.database.financeDao().allDrafts().size)
    }


    @Test fun optionalNameCanBeSkippedBeforeManualEntryWithoutConsent() {
        rule.runOnIdle { app.settings.reset() }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("display-name-input").fetchSemanticsNodes().isNotEmpty() }
        clickEnabled(hasText("Agora não") and hasClickAction())
        assertTrue(app.settings.snapshot().namePromptSeen)
        assertFalse(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
        openExpense()
        rule.onNodeWithTag("amount-input").assertIsDisplayed()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }

    @Test fun savingOptionalNameDoesNotReplaceDisclosureConsent() {
        rule.runOnIdle { app.settings.reset() }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("display-name-input").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("display-name-input").performTextInput("Pessoa de exemplo")
        clickEnabled(hasText("Salvar") and hasClickAction())
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá, Pessoa de exemplo! 👋🏼")
        assertFalse(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
        openSettings()
        clickEnabled(hasContentDescription("Capturar notificações"))
        rule.onNodeWithText("Antes de ativar a captura").assertIsDisplayed()
        clickEnabled(hasText("Agora não") and hasClickAction())
        assertFalse(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
    }

    @Test fun skippedOptionalNameStaysClosedAfterRecreationWithoutEnablingCapture() {
        rule.runOnIdle { app.settings.reset() }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("display-name-input").fetchSemanticsNodes().isNotEmpty() }
        clickEnabled(hasText("Agora não") and hasClickAction())
        rule.activityRule.scenario.recreate()
        waitForDashboard()
        rule.onAllNodesWithTag("display-name-input").assertCountEquals(0)
        rule.onNodeWithTag("rendly-greeting").assertTextEquals("Olá! 👋🏼")
        assertTrue(app.settings.snapshot().namePromptSeen)
        assertFalse(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
    }

    @Test fun accountCanBeAddedWithoutLosingTheAmount() {
        openExpense()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        // O teclado mede a janela fora do relógio Compose; o toque só ocorre após estabilizar a geometria.
        waitForStableBounds(rule.onNodeWithTag("amount-input"))
        rule.openRecordDetails()
        val addAccount = rule.onNodeWithContentDescription("Adicionar Conta ou banco de origem").performScrollTo()
        waitForStableBounds(addAccount)
        addAccount.performClick()
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
        openExpense()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        rule.onNodeWithText("Descrição").performTextInput("Formulário de exemplo")
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("amount-input").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Descrição").assertTextContains("Formulário de exemplo")
        confirmEntry("Formulário de exemplo")
        val saved = runBlocking { app.repository.exportRecords().single() }
        assertEquals(1234L, saved.amountCents)
        assertEquals("Formulário de exemplo", saved.description)
    }

    @Test fun chartOpensOnlyTheSelectedExpenseGroup() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        app.repository.save(Record("grafico-gasto", 10000, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta teste", "Despesa do gráfico", now), null)
        app.repository.save(Record("grafico-receita", 20000, Movement.INCOME, PaymentMethod.PIX, Category.FOOD,
            "Conta teste", "Receita excluída do gráfico", now), null)
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 2 } }
        scrollDashboardTo(hasTestTag("expense-group-FOOD"))
        screenshot("grafico-categorias")
        rule.onNodeWithTag("expense-group-FOOD").performClick()
        scrollRecordsTo("Despesa do gráfico")
        rule.onNodeWithText("Despesa do gráfico").assertIsDisplayed()
        rule.onAllNodesWithText("Receita excluída do gráfico").assertCountEquals(0)
    }


    @Test fun rejectedPasteShowsAnErrorAndCannotSaveThePreviousAmount() {
        openExpense()
        rule.onNodeWithTag("amount-input").performTextInput("1234")
        rule.onNodeWithTag("amount-input").performTextReplacement("1,234")
        rule.onNodeWithText("Entrada inválida ou acima do limite. Digite números ou cole um valor como 1.234,56.").assertExists()
        rule.onNodeWithText("Descrição").performTextInput("Teste de colagem inválida")
        clickEnabled(hasText("Registrar despesa") and hasClickAction())
        rule.onNodeWithTag("amount-input").assertExists()
        rule.onNodeWithText("Entrada inválida ou acima do limite. Digite números ou cole um valor como 1.234,56.").assertExists()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
    }

    @Test fun largeTextAndOpenKeyboardAllowManualEntry() {
        val previousFontScale = rule.activity.resources.configuration.fontScale.toString()
        fun fontScale(value: String) {
            val command = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("settings put system font_scale " + value)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        }
        try {
            fontScale("1.5")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.4f }
            openExpense()
            rule.onNodeWithTag("amount-input").performClick().performTextInput("1234")
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Fonte ampliada")
            screenshot("formulario-fonte-ampliada")
            confirmEntry()
            assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
        } finally { fontScale(previousFontScale) }
    }

    @Test fun landscapeLayoutKeepsTheFormUsable() {
        val previousOrientation = rule.activity.requestedOrientation
        try {
            rule.activityRule.scenario.onActivity { activity -> activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            rule.waitForIdle()
            openExpense()
            // O usuário precisa alcançar o campo antes de focá-lo. Em paisagem, o foco
            // sem rolagem dispara duas solicitações de reposicionamento durante o IME.
            val amount = rule.onNodeWithTag("amount-input")
            amount.performScrollTo().assertIsDisplayed().performClick()
            rule.waitUntil(5000) {
                android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                    androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
            }
            rule.waitForIdle()
            amount.performTextInput("1234")
            amount.performImeAction()
            rule.waitUntil(5000) {
                val windows = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
                windows.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
            }
            rule.waitForIdle()
            rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Paisagem")
            screenshot("formulario-paisagem")
            confirmEntry()
            assertEquals(1234L, runBlocking { app.repository.exportRecords().single().amountCents })
        } finally {
            rule.activityRule.scenario.onActivity { activity -> activity.requestedOrientation = previousOrientation }
        }
    }


    @Test fun hiddenAmountsAlsoHideChartLabelsAndSemantics() = runBlocking<Unit> {
        app.repository.save(Record("privacidade-grafico", 123456, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta exemplo", "Exemplo privado", System.currentTimeMillis()), null)
        scrollDashboardTo(hasTestTag("expense-group-FOOD"))
        rule.onNodeWithTag("expense-group-FOOD").assertTextContains(Money.format(123456))
        clickEnabled(hasContentDescription("Ocultar valores"))
        scrollDashboardTo(hasTestTag("expense-group-FOOD"))
        rule.onAllNodesWithText(Money.format(123456), substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(Money.format(123456), substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("100,00%", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("100,00%", substring = true, useUnmergedTree = true).assertCountEquals(0)
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
        val listenerWasAllowed = component in previousListeners.split(':')
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val alertsWereGranted = android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        fun denyRuntimeAlerts() {
            val permissionManager = checkNotNull(app.getSystemService("permission"))
            val revoke = permissionManager.javaClass.getMethod("revokePostNotificationPermissionWithoutKillForTest",
                String::class.java, Int::class.javaPrimitiveType)
            val userId = command("am get-current-user").trim().toInt()
            // TestApi usado pelo CTS: revoga a permissão real sem encerrar a instrumentação.
            automation.adoptShellPermissionIdentity("android.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL",
                "android.permission.REVOKE_RUNTIME_PERMISSIONS")
            try { revoke.invoke(permissionManager, app.packageName, userId) }
            finally { automation.dropShellPermissionIdentity() }
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS))
        }
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
            // Da API 33 em diante, o estado dos alertas depende da permissão runtime, não do app-op legado.
            if (android.os.Build.VERSION.SDK_INT >= 33) denyRuntimeAlerts()
            rule.runOnIdle {
                app.settings.acceptDisclosure()
                app.settings.selectPackages(setOf("banco.exemplo"))
            }
            listenerAccess(true)
            resume()
            rule.waitUntil(10000) { app.listenerConnected.value }
            openSettings()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Captura ativa para 1 aplicativo(s).").fetchSemanticsNodes().isNotEmpty() }
            val alertAction = rule.onNodeWithText("Permitir alertas de revisão")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                assertFalse(androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled())
                alertAction.assertExists()
            } else {
                // Nessas versões não há POST_NOTIFICATIONS runtime; a ação reflete o bloqueio global efetivo do sistema.
                if (androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()) alertAction.assertDoesNotExist()
                else alertAction.assertExists()
            }
            listenerAccess(false)
            resume()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Falta autorizar o acesso às notificações no Android.").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Autorizar no Android"))
            rule.onNodeWithText("Autorizar no Android").assertIsDisplayed().assertIsEnabled()
            rule.openRendlyMonth()
            waitForDashboard()
            rule.openRendlyReview()
            rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
            clickEnabled(hasText("Configurar captura") and hasClickAction())
            rule.onNodeWithText("Falta autorizar o acesso às notificações no Android.").assertExists()
        } finally {
            try { listenerAccess(listenerWasAllowed) }
            finally {
                if (alertsWereGranted) automation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
                rule.runOnIdle { app.settings.setEnabled(false) }
            }
        }
    }


    @Test fun pausingCaptureKeepsConsentAndManualEntryAvailableAfterRecreation() {
        rule.runOnIdle {
            app.settings.acceptDisclosure()
            app.settings.selectPackages(setOf("banco.exemplo"))
        }
        openSettings()
        clickEnabled(hasContentDescription("Capturar notificações"))
        assertTrue(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Capturar notificações").assertIsOff()
        assertTrue(app.settings.snapshot().consented)
        assertFalse(app.settings.snapshot().enabled)
        rule.openRendlySettings()
        openExpense()
        rule.onNodeWithTag("amount-input").assertIsDisplayed()
    }

    private fun waitForStableBounds(node: SemanticsNodeInteraction) {
        var previous: androidx.compose.ui.geometry.Rect? = null
        var stableSince = android.os.SystemClock.uptimeMillis()
        rule.waitUntil(5000) {
            val bounds = node.fetchSemanticsNode().boundsInWindow
            val now = android.os.SystemClock.uptimeMillis()
            if (bounds != previous) {
                previous = bounds; stableSince = now
                false
            } else now - stableSince >= 300
        }
        node.assertIsDisplayed()
    }

    private fun waitForDashboard() {
        rule.waitUntil(10000) {
            rule.onAllNodes(hasTestTag("add-movement") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("records-list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun clickEnabled(matcher: SemanticsMatcher) {
        rule.waitUntil(10000) { rule.onAllNodes(matcher and isEnabled()).fetchSemanticsNodes().size == 1 }
        rule.onNode(matcher).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }

    private fun openExpense() {
        rule.newRendlyRecord()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("amount-input").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openSettings() {
        if (rule.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isEmpty())
            rule.openRendlySettings()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollDashboardTo(matcher: SemanticsMatcher) {
        rule.openRendlyGraphs()
        rule.waitUntil(10000) {
            try {
                rule.onNodeWithTag("graphs-screen").performScrollToNode(matcher)
                true
            } catch (_: AssertionError) { false }
        }
    }

    private fun scrollRecordsTo(description: String) {
        rule.waitUntil(10000) {
            try {
                rule.onNodeWithTag("records-list").performScrollToNode(hasText(description))
                rule.onNodeWithText(description).assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
        }
    }

    private fun openStatement() { rule.openRendlyMonth() }

    private fun confirmEntry(description: String = "Lançamento fictício de teste", saveLabel: String = "Registrar despesa") {
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement(description)
        rule.onNodeWithText("Descrição").performImeAction()
        rule.waitUntil(5000) {
            val windows = if (android.os.Build.VERSION.SDK_INT >= 29)
                android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            windows.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        val confirm = rule.onNode(hasText(saveLabel) and hasClickAction())
        if (rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            rule.activity.resources.configuration.fontScale >= 1.5f) confirm.performScrollTo()
        clickEnabled(hasText(saveLabel) and hasClickAction())
        try {
            rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
            rule.waitUntil(10000) { rule.onAllNodesWithTag("amount-input").fetchSemanticsNodes().isEmpty() }
        } catch (failure: Throwable) {
            val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") {
                rule.onAllNodes(isRoot())[it].printToString()
            }
            throw AssertionError("A confirmação não terminou. Estado da interface:\n" + roots, failure)
        }
    }

    private fun assertStatusBarBackground(dark: Boolean) {
        rule.waitUntil(5000) {
            val bitmap = captureEmulatorScreen() ?: return@waitUntil false
            val pixel = bitmap.getPixel(bitmap.width / 2, 2)
            bitmap.recycle()
            val brightness = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
            if (dark) brightness < 128 else brightness > 128
        }
    }

    private fun screenshot(name: String) {
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        rule.waitForIdle()
        val bitmap = captureEmulatorScreen() ?: error("Não foi possível capturar a tela do emulador.")
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }

    private fun captureEmulatorScreen(): Bitmap? {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        if (android.os.Build.VERSION.SDK_INT != 26) return automation.takeScreenshot()
        // Na API 26, takeScreenshot usa o tamanho lógico e falha se wm size exceder a tela física.
        // O screencap solicita as dimensões nativas e mantém a proteção de janelas seguras.
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p")).use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    }
}
