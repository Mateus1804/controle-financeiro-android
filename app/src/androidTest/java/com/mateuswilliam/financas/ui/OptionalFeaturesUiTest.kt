package com.mateuswilliam.financas.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
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
import java.time.Instant
import java.time.ZoneId

class OptionalFeaturesUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    @Before fun reset() = rule.resetReviewBackupMain()
    private fun forecast(cents: Long = 60000) = ForecastRule("mensal-ficticia", LocalDate.now(), "Aluguel fictício", ForecastKind.EXPENSE,
        cents, Category.HOME, ForecastFrequency.MONTHLY, LocalDate.now())
    private fun draft(id: String, source: String = id) = DraftEntity(id, source, "fixture.local", "Inter pessoal",
        "EXEMPLO FICTÍCIO · $id", "Compra aprovada no cartão de R$ 42,90. Mercado Exemplo.", 4290,
        Movement.EXPENSE, PaymentMethod.CARD, Category.FOOD, "Dados fictícios para revisão.", System.currentTimeMillis(), System.currentTimeMillis() - 60_000)
    private fun scrollForecast(matcher: SemanticsMatcher) {
        rule.openRendlyPlanner()
        rule.onNodeWithTag("forecast-dashboard").performScrollToNode(matcher)
    }
    private fun openReview(id: String) {
        val model = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]
        rule.waitUntil(10000) { model.state.value.pending.any { it.id == id } }
        rule.openRendlyReview()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("pending-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("pending-list").performScrollToNode(hasTestTag("review-$id"))
        rule.onNodeWithTag("review-$id").performClick()
    }

    @Test fun monthlyForecastCanBeCreatedAndRecoveredAfterRecreation() = runBlocking<Unit> {
        scrollForecast(hasTestTag("forecast-add-expense"))
        rule.onNodeWithTag("forecast-add-expense").performClick()
        rule.onNodeWithTag("forecast-title").performTextInput("Limite de consumo fictício")
        rule.onNodeWithTag("forecast-amount").performScrollTo().performTextInput("10000")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        waitForKeyboard()
        rule.onNodeWithContentDescription("Frequência").performScrollTo().performClick()
        rule.onNodeWithText("Mensal").performClick()
        rule.onNodeWithTag("forecast-save").performClick()
        rule.waitUntil(10000) { runBlocking { app.forecasts.dataset.first().rules.size == 1 } }
        val saved = app.forecasts.dataset.first().rules.single()
        assertEquals(10000L, saved.amountCents)
        assertEquals(ForecastFrequency.MONTHLY, saved.frequency)
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.dismissFeedback("Previsão salva")
        rule.activityRule.scenario.recreate()
        rule.openRendlyPlanner()
        val data = app.forecasts.dataset.first()
        val period = CycleCalendar.periodContaining(LocalDate.now(), data.cycleRules)
        val line = ForecastCalculator.report(data, period).lines.single { it.itemId == saved.itemId }
        val actions = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
        scrollForecast(hasTestTag(actions))
        rule.onNodeWithTag(actions).performClick()
        rule.onNodeWithText("Alterar só este mês").performClick()
        rule.onNodeWithTag("forecast-title").assertTextContains("Limite de consumo fictício")
        assertAmountInput("forecast-amount", 10000)
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(saved, app.forecasts.dataset.first().rules.single())
        screenshot("planejamento-claro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle()
        screenshot("planejamento-escuro")
    }

    @Test fun forecastAndExplicitExpenseKeepSeparateDatesAndDoNotDuplicateOnRecreation() = runBlocking<Unit> {
        val planned = forecast()
        app.forecasts.saveRule(planned)
        rule.waitForIdle()
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.newRendlyRecord(com.mateuswilliam.financas.core.Movement.EXPENSE)
        rule.onNodeWithTag("amount-input").performTextInput("60000")
        rule.onNodeWithText("Descrição").performScrollTo().performTextInput("Aluguel realizado fictício")
        rule.onNodeWithText("Descrição").performScrollTo().performImeAction()
        waitForKeyboard()
        rule.onNode(hasText("Registrar despesa") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val record = app.repository.exportRecords().single()
        assertEquals(60000L, record.amountCents)
        assertEquals(LocalDate.now(), Instant.ofEpochMilli(record.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate())
        assertEquals(planned, app.forecasts.dataset.first().rules.single())
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("records-list").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(record), app.repository.exportRecords())
        assertEquals(planned, app.forecasts.dataset.first().rules.single())
        rule.onNodeWithText("Confirmar pagamento").assertDoesNotExist()
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
        rule.onNodeWithText("Sugestão tratada").assertExists()
    }

    @Test fun newArrivalsPreserveEditsAndFollowTheExistingReviewQueue() = runBlocking<Unit> {
        app.database.financeDao().insertDraft(draft("fila-a"))
        app.database.financeDao().insertDraft(draft("fila-b"))
        val model = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]
        rule.waitUntil(10000) {
            model.state.value.pending.map { it.id }.containsAll(listOf("fila-a", "fila-b"))
        }
        openReview("fila-a")
        rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement("9999")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Edição preservada")
        app.database.financeDao().insertDraft(draft("fila-nova").copy(capturedAt = System.currentTimeMillis()))
        rule.waitUntil(5000) { runBlocking { app.repository.pending.first().size == 3 } }
        rule.waitForIdle()
        rule.onNodeWithText("Descrição").assertTextContains("Edição preservada")
        assertAmountInput("amount-input", 9999)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("Descrição").assertTextContains("Edição preservada")
        assertAmountInput("amount-input", 9999)
        confirmDescription("Edição preservada")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
        assertEquals(9999L, app.repository.exportRecords().single().amountCents)
        rule.onNodeWithTag("review-next").performClick()
        assertReviewTitle("EXEMPLO FICTÍCIO · fila-b")
        assertEquals(setOf("fila-b", "fila-nova"), app.repository.pending.first().map { it.id }.toSet())
        screenshot("revisao-em-sequencia")
        confirmDescription("Segunda revisão explícita")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().size == 2 } }
        rule.onNodeWithTag("review-next").performClick()
        assertReviewTitle("EXEMPLO FICTÍCIO · fila-nova")
        assertEquals("fila-nova", app.repository.pending.first().single().id)
        assertEquals(2, app.repository.exportRecords().size)
    }

    @Test fun changedNotificationRequiresAcknowledgementWithoutErasingFormFields() = runBlocking<Unit> {
        val value = draft("alterada", "fixture:alterada")
        app.database.financeDao().insertDraft(value)
        openReview(value.id)
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Descrição editada")
        app.repository.capture(NotificationInput(value.sourcePackage, value.sourceKey, value.bank, "EXEMPLO FICTÍCIO atualizado",
            "Compra aprovada no cartão de R$ 48,90. Mercado Exemplo.", value.occurredAt), simulation = true)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Usar esta versão e manter a edição").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Confirmar lançamento") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText("Descrição").assertTextContains("Descrição editada")
        rule.onNodeWithText("Descrição").performImeAction(); waitForKeyboard()
        rule.onNodeWithText("Usar esta versão e manter a edição").performScrollTo().performClick()
        rule.onNode(hasText("Confirmar lançamento") and hasClickAction()).assertIsEnabled()
        rule.onNodeWithText("Descrição").assertTextContains("Descrição editada")
        confirmDescription("Descrição editada")
        rule.waitUntil(5000) { runBlocking { app.repository.exportRecords().isNotEmpty() } }
        assertEquals(4290L, app.repository.exportRecords().single().amountCents)
        assertEquals("Descrição editada", app.repository.exportRecords().single().description)
    }

    @Test fun enabledProtectionHidesFinancialScreensAndNotificationDeepLinks() {
        val originalIntent = Intent(rule.activity.intent)
        runBlocking { app.database.financeDao().insertDraft(draft("pendencia-privada")) }
        try {
            rule.runOnIdle { app.settings.setLockEnabled(true) }
            rule.waitUntil(5000) { rule.onAllNodesWithText("Acesso protegido").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithTag("rendly-greeting").assertCountEquals(0)
            rule.onAllNodesWithTag("forecast-dashboard").assertCountEquals(0)
            rule.onAllNodesWithTag("add-realized-expense").assertCountEquals(0)
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
            rule.runOnIdle { app.settings.reset(); app.settings.finishIntro(); app.settings.dismissNamePrompt() }
        }
    }

    @Test fun backupPasswordIsNotSavedOnRecreationAndCancellingChangesNothing() = runBlocking<Unit> {
        val initial = Record("password-cancellation", 12345, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta fictícia", "Registro preservado após cancelar", System.currentTimeMillis())
        app.repository.save(initial, null)
        rule.openRendlySettings()
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
        for (label in listOf("Senha do backup", "Repetir senha")) {
            assertEquals("", rule.onNodeWithText(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        }
        rule.onAllNodesWithText("curta", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
        assertEquals(listOf(initial), app.repository.exportRecords())
    }

    @Test fun secureScreenAlsoProtectsTheNativeDateDialog() {
        try {
            rule.runOnIdle { app.settings.setSecureScreen(true) }
            rule.waitForIdle()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                rule.newRendlyRecord(com.mateuswilliam.financas.core.Movement.EXPENSE)
                rule.onNodeWithTag("record-date").performScrollTo().performClick()
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
        app.forecasts.saveRule(forecast(123456))
        val previousFont = shellOutput("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null }
        val previousOrientation = rule.activity.requestedOrientation
        try {
            rule.runOnIdle { app.settings.hideAmounts(true); app.settings.setAppearance(Appearance.DARK) }
            checkpoint("Ajustando fonte ampliada")
            command("settings put system font_scale 1.5")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.4f }
            rule.onNodeWithText("Mês", useUnmergedTree = true).assertStartAlignedTextFits("Mês")
            rule.activityRule.scenario.onActivity { activity ->
                assertFalse(WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
            }
            scrollForecast(hasTestTag("forecast-add-expense"))
            rule.onAllNodesWithText(Money.format(123456), substring = true).assertCountEquals(0)
            rule.onAllNodesWithContentDescription(Money.format(123456), substring = true).assertCountEquals(0)
            checkpoint("Capturando planejamento com fonte ampliada")
            screenshot("planejamento-fonte-ampliada")
            rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
            rule.waitForIdle()
            rule.runOnIdle {
                assertTrue(WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars)
            }
            rule.onAllNodesWithText(Money.format(123456), substring = true).assertCountEquals(0)
            rule.onAllNodesWithContentDescription(Money.format(123456), substring = true).assertCountEquals(0)
            screenshot("planejamento-fonte-ampliada-claro")
            rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
            checkpoint("Mudando orientação")
            rule.activityRule.scenario.onActivity { activity -> activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            scrollForecast(hasTestTag("forecast-add-expense"))
            rule.onNodeWithTag("forecast-add-expense").assertIsDisplayed().performClick()
            rule.onNodeWithTag("forecast-title").assertExists()
            rule.onNodeWithText("Mostrar valor da previsão para edição").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("forecast-amount").assertDoesNotExist()
            rule.onNodeWithText("Cancelar").performClick()
            checkpoint("Capturando planejamento em paisagem")
            screenshot("planejamento-paisagem")
            checkpoint("Paisagem conferida")
        } finally {
            checkpoint("Restaurando fonte e orientação")
            if (previousFont != null) command("settings put system font_scale $previousFont")
            else command("settings delete system font_scale")
            rule.activityRule.scenario.onActivity { activity -> activity.requestedOrientation = previousOrientation }
        }
    }

    private fun checkpoint(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\n[interface] $message\n")
        })
    }

    private fun assertReviewTitle(title: String) {
        val originalTitle = SemanticsMatcher("Título original da revisão: $title") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } == listOf(title)
        }
        rule.onNode(originalTitle).assertExists()
    }

    private fun confirmDescription(description: String) {
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement(description)
        rule.onNodeWithText("Descrição").performImeAction()
        waitForKeyboard()
        rule.onNode(hasText("Confirmar lançamento") and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { assertTrue(it()) }
    }
    private fun waitForKeyboard() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitUntil(5000) {
            val views = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            views.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        rule.waitForIdle()
    }
    private fun assertAmountInput(tag: String, cents: Long) {
        val text = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals(cents, MoneyInput.normalize(text)?.toLongOrNull())
    }
    private fun command(value: String) {
        shellOutput(value)
    }
    private fun shellOutput(value: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(value)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        if (app.settings.snapshot().appearance == Appearance.DARK) rule.waitUntil(5000) {
            // Em paisagem, versões antigas podem ocultar os ícones. A configuração de contraste independe de pixels fixos.
            !WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars
        }
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "finance-ui-capture").apply { isDaemon = true }
        }
        try {
            val bitmap = executor.submit<Bitmap> {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Captura do emulador indisponível")
            }.get(15, java.util.concurrent.TimeUnit.SECONDS)
            try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        } finally { executor.shutdownNow() }
    }
}
