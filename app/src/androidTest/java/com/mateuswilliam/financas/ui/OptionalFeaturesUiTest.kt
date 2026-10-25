package com.mateuswilliam.financas.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.Appearance
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.notifications.ReviewNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class OptionalFeaturesUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() { runBlocking { app.repository.clear() }; app.settings.finishIntro(); rule.waitForIdle() }
    private fun forecast() = RecurringPlan("mensal-ficticia", "Aluguel fictício", 60000, Movement.EXPENSE,
        PaymentMethod.PIX, Category.HOME, "Conta de exemplo", "2026-10-31", 31)
    private fun draft(id: String, source: String = id) = DraftEntity(id, source, "fixture.local", "Inter pessoal",
        "EXEMPLO FICTÍCIO", "Compra aprovada no cartão de R$ 42,90. Mercado Exemplo.", 4290,
        Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Dados fictícios para revisão.", System.currentTimeMillis(), System.currentTimeMillis() - 60_000)
    private fun openPlanning() { rule.onNodeWithText("Planejar limites e recorrências").performClick() }
    private fun openReview(id: String) {
        rule.onNodeWithText("Revisar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("review-$id").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("review-$id").performClick()
    }

    @Test fun monthlyBudgetCanBeCreatedAndRecoveredAfterRecreation() = runBlocking<Unit> {
        openPlanning()
        rule.onNodeWithText("Adicionar limite mensal").performClick()
        rule.onNodeWithTag("amount-input").performTextInput("10000")
        rule.onNodeWithTag("amount-input").performImeAction()
        waitForKeyboard()
        rule.onNodeWithText("Salvar limite").performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.budgets.first().isNotEmpty() } }
        assertEquals(10000L, app.repository.budgets.first().single().limitCents)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Editar limite").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Editar limite").assertIsDisplayed()
        assertEquals(YearMonth.now().toString(), app.repository.budgets.first().single().month)
        screenshot("planejamento-claro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle()
        screenshot("planejamento-escuro")
    }

    @Test fun forecastRequiresConfirmationAndRecordsTheEffectiveDateWithoutDuplicating() = runBlocking<Unit> {
        app.repository.saveRecurring(forecast())
        openPlanning()
        rule.onNodeWithTag("planning-list").performScrollToNode(hasText("Conferir e registrar"))
        rule.onNodeWithText("Conferir e registrar").performScrollTo().performClick()
        rule.onNodeWithText("Conferir previsão").assertExists()
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.onNodeWithText("Descrição").performScrollTo().performImeAction()
        waitForKeyboard()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val record = app.repository.exportRecords().single()
        assertEquals(60000L, record.amountCents)
        assertEquals(LocalDate.now(), java.time.Instant.ofEpochMilli(record.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        assertEquals("2026-11-30", app.repository.recurring.first().single().nextDate)
    }

    @Test fun duplicateWarningLeavesTheManualRecordIntactAndAllowsExplicitConfirmation() = runBlocking<Unit> {
        val value = draft("duplicidade")
        app.repository.save(Record("manual-ficticio", 4290, Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD,
            "Inter pessoal", "Mercado Exemplo", value.occurredAt), null)
        app.database.financeDao().insertDraft(value)
        openReview(value.id)
        rule.onNodeWithText("Possível duplicidade").assertExists()
        assertEquals(1, app.repository.exportRecords().size)
        screenshot("possivel-duplicidade")
        confirmDescription("Outra compra confirmada pelo usuário")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 2 } }
        assertTrue(app.repository.exportRecords().any { it.id == "manual-ficticio" })
        rule.onNodeWithText("Movimentação revisada").assertExists()
    }

    @Test fun newArrivalsPreserveEditsAndRemainOutsideTheCurrentReviewSnapshot() = runBlocking<Unit> {
        app.database.financeDao().insertDraft(draft("fila-a"))
        app.database.financeDao().insertDraft(draft("fila-b"))
        openReview("fila-a")
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("9999")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Edição preservada")
        app.database.financeDao().insertDraft(draft("fila-nova").copy(capturedAt = System.currentTimeMillis()))
        rule.waitUntil(5000) { rule.onAllNodesWithText("1 nova(s) pendência(s) disponível(is). Os campos desta revisão foram mantidos.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Descrição").assertTextContains("Edição preservada")
        rule.onNodeWithText("Revisão 1 de 2").assertExists()
        confirmDescription("Edição preservada")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
        assertEquals(9999L, app.repository.exportRecords().single().amountCents)
        rule.onNodeWithText("Revisar próxima").performClick()
        rule.onNodeWithText("Revisão 2 de 2").assertExists()
        screenshot("revisao-em-sequencia")
    }

    @Test fun changedNotificationRequiresAcknowledgementWithoutErasingFormFields() = runBlocking<Unit> {
        val value = draft("alterada", "fixture:alterada")
        app.database.financeDao().insertDraft(value)
        openReview(value.id)
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Descrição editada")
        app.repository.capture(NotificationInput(value.sourcePackage, value.sourceKey, value.bank, "EXEMPLO FICTÍCIO atualizado",
            "Compra aprovada no cartão de R$ 48,90. Mercado Exemplo.", value.occurredAt), simulation = true)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar esta versão e manter a edição").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Confirmar").assertIsNotEnabled()
        rule.onNodeWithText("Descrição").assertTextContains("Descrição editada")
        rule.onNodeWithText("Descrição").performImeAction(); waitForKeyboard()
        rule.onNodeWithText("Usar esta versão e manter a edição").performScrollTo().performClick()
        rule.onNodeWithText("Descrição").assertTextContains("Descrição editada")
        confirmDescription("Descrição editada")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
        assertEquals(4290L, app.repository.exportRecords().single().amountCents)
        assertEquals("Descrição editada", app.repository.exportRecords().single().description)
    }

    @Test fun enabledProtectionHidesFinancialScreensAndNotificationDeepLinks() {
        val originalIntent = Intent(rule.activity.intent)
        try {
            rule.runOnIdle { app.settings.setLockEnabled(true) }
            rule.waitUntil(5000) { rule.onAllNodesWithText("Acesso protegido").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText("Finanças").assertCountEquals(0)
            rule.onAllNodesWithText("Extrato").assertCountEquals(0)
            rule.runOnIdle { assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("Acesso protegido").assertExists()
            rule.activityRule.scenario.onActivity { activity -> InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                Intent(activity, MainActivity::class.java).putExtra(ReviewNotifications.EXTRA_DRAFT_ID, "pendencia-privada")) }
            rule.waitForIdle()
            rule.onNodeWithText("Acesso protegido").assertExists()
            rule.onAllNodesWithText("Conferir movimentação").assertCountEquals(0)
        } finally {
            // O monitor do ActivityScenario identifica a atividade pelo Intent de abertura.
            rule.activityRule.scenario.onActivity { it.intent = originalIntent }
            rule.runOnIdle { app.settings.reset(); app.settings.finishIntro() }
        }
    }

    @Test fun backupPasswordIsNotSavedOnRecreationAndCancellingChangesNothing() = runBlocking<Unit> {
        rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Criar backup criptografado"))
        rule.onNodeWithText("Criar backup criptografado").performClick()
        rule.onNodeWithText("Senha do backup").performTextInput("curta")
        rule.onNodeWithText("Repetir senha").performTextInput("curta")
        rule.onNodeWithText("Repetir senha").performImeAction()
        waitForKeyboard()
        rule.onNodeWithText("Preparar backup").performClick()
        rule.onNodeWithText("Use uma senha com pelo menos 12 caracteres.").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Senha do backup").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", rule.onNodeWithText("Senha do backup").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onAllNodesWithText("curta", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun secureScreenAlsoProtectsTheNativeDateDialog() {
        try {
            rule.runOnIdle { app.settings.setSecureScreen(true) }
            rule.waitForIdle()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                rule.onNodeWithContentDescription("Novo lançamento").performClick()
                rule.onNodeWithText("Data:", substring = true).performScrollTo().performClick()
                rule.waitUntil(5000) { android.view.inspector.WindowInspector.getGlobalWindowViews().size >= 3 }
                rule.runOnIdle {
                    val windows = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    assertTrue(windows.all { (it.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE != 0 })
                }
            } else {
                // WindowInspector só existe a partir da API 29. A API 26 verifica a janela real devolvida pelo mesmo componente.
                rule.runOnIdle {
                    val dialog = showFinanceDatePicker(rule.activity, android.R.style.Theme_Material_Light_Dialog_Alert, LocalDate.of(2026, 10, 25)) {}
                    try {
                        assertTrue(dialog.isShowing)
                        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                        assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    } finally { dialog.dismiss() }
                }
            }
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= 29)
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            rule.runOnIdle { app.settings.setSecureScreen(false) }
        }
    }

    @Test fun hiddenPlanningAndLargeTextStayUsableInBothThemes() = runBlocking<Unit> {
        val month = YearMonth.now().toString()
        app.repository.saveBudget(MonthlyBudget(month, Category.HOME, 123456))
        app.repository.saveRecurring(forecast())
        try {
            rule.runOnIdle { app.settings.hideAmounts(true); app.settings.setAppearance(Appearance.DARK) }
            command("settings put system font_scale 1.5")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.4f }
            rule.onNodeWithText("Resumo", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts)); assertEquals(1, layouts.single().lineCount); assertFalse(layouts.single().isLineEllipsized(0))
            }
            rule.runOnIdle { assertFalse(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars) }
            openPlanning()
            rule.onNodeWithTag("planning-list").performScrollToNode(hasText("Gasto e limite ocultos"))
            rule.onAllNodesWithText(Money.format(123456), substring = true).assertCountEquals(0)
            rule.onAllNodesWithContentDescription(Money.format(123456), substring = true).assertCountEquals(0)
            screenshot("planejamento-fonte-ampliada")
            rule.runOnIdle { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            rule.onNodeWithTag("planning-list").performScrollToNode(hasText("Adicionar recorrência"))
            rule.onNodeWithText("Adicionar recorrência").assertIsDisplayed()
            screenshot("planejamento-paisagem")
        } finally {
            command("settings put system font_scale 1.0")
            rule.runOnIdle { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    private fun confirmDescription(description: String) {
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement(description)
        rule.onNodeWithText("Descrição").performImeAction()
        waitForKeyboard()
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).assertIsDisplayed().performClick()
    }
    private fun waitForKeyboard() {
        rule.waitUntil(5000) {
            val views = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            views.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        rule.waitForIdle()
    }
    private fun command(value: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(value)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
    private fun screenshot(name: String) {
        rule.waitUntil(8000) { rule.onAllNodesWithText("Limite mensal salvo.").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        if (app.settings.snapshot().appearance == Appearance.DARK) rule.waitUntil(5000) {
            // Em paisagem, versões antigas podem ocultar os ícones. A configuração de contraste independe de pixels fixos.
            !WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars
        }
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Captura do emulador indisponível")
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
