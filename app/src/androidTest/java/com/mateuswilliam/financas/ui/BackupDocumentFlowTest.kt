package com.mateuswilliam.financas.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Usa o seletor real do Android no emulador dedicado, somente com arquivo e senha fictícios. */
class BackupDocumentFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val secret = "Arquivo fictício 2026!"
    // PBKDF2 mantém 600 mil iterações; o provedor Java da API 26 é lento sem aceleração.
    private val cryptoWaitMillis = 300_000L

    @Before fun reset() {
        runBlocking {
            app.maintenanceReady.await()
            app.repository.clear()
        }
        app.settings.finishIntro()
        app.settings.dismissNamePrompt()
    }

    @Test fun preparedBackupRemainsAvailableWhenTheActivityIsRecreated() {
        val key = "backup-export-rotation-test"
        val filename = "backup-rotacao-ficticio.cfbackup"
        val password = secret.toCharArray()
        rule.runOnIdle {
            ViewModelProvider(rule.activity)[key, FinanceViewModel::class.java].createBackup(password, filename)
        }
        rule.activityRule.scenario.recreate()
        val exporter = ViewModelProvider(rule.activity)[key, FinanceViewModel::class.java]
        rule.waitUntil(cryptoWaitMillis) { exporter.backupDestination.value == filename && !exporter.busy.value }
        assertTrue(password.all { it == '\u0000' })
        val file = File.createTempFile("backup-rotacao", ".cfbackup", app.cacheDir)
        try {
            rule.runOnIdle {
                assertEquals(filename, exporter.takeBackupDestination())
                exporter.busy.value = true
                exporter.writeBackup(Uri.fromFile(file))
            }
            rule.waitUntil(10000) { exporter.message.value?.startsWith("Backup salvo.") == true }
            assertTrue(file.length() > BackupCrypto.HEADER_BYTES)
        } finally { file.delete() }
    }

    @Test fun createAndRestoreThroughTheAndroidDocumentPicker() = runBlocking<Unit> {
        val expected = Record("backup-document-ui", 123456, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta fictícia", "Recuperação fictícia pelo seletor", System.currentTimeMillis())
        val date = java.time.LocalDate.now()
        val expectedForecast = ForecastRule("backup-document-forecast", date, "Previsão fictícia do backup", ForecastKind.EXPENSE,
            25000, Category.HOME, ForecastFrequency.MONTHLY, date)
        app.repository.save(expected, null)
        app.forecasts.saveRule(expectedForecast)
        val info = automation.serviceInfo
        val oldFlags = info.flags
        automation.serviceInfo = info.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            rule.waitUntil(10000) { rule.onAllNodesWithContentDescription("Ajustes").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Ajustes").assertIsDisplayed().performClick()
            rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Criar backup criptografado"))
            rule.onNodeWithText("Criar backup criptografado").performClick()
            rule.onNodeWithText("Senha do backup").performTextInput(secret)
            rule.onNodeWithText("Repetir senha").performTextInput(secret)
            rule.onNodeWithText("Repetir senha").performImeAction()
            waitForKeyboard()
            rule.onNodeWithText("Preparar backup").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            rule.waitForIdle()
            val fileName = "backup-ficticio-${UUID.randomUUID()}.cfbackup"
            val name = awaitSystemNode(cryptoWaitMillis) { it.className?.toString() == "android.widget.EditText" && it.isEditable }
            assertTrue(name.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
            }))
            click(awaitSystemNode { it.isEnabled && (it.viewIdResourceName?.endsWith(":id/button_save") == true || it.text?.toString() in setOf("Save", "SAVE", "Salvar", "SALVAR")) })
            awaitApplication()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Backup salvo.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            app.repository.clear()
            rule.runOnIdle {
                app.settings.finishIntro()
                app.settings.dismissNamePrompt()
            }
            rule.waitForIdle()
            assertTrue(app.repository.exportRecords().isEmpty())
            val local = expected.copy(id = "local-before-confirmation", description = "Preservado até confirmar a restauração")
            app.repository.save(local, null)
            // O aviso temporário fica acima da navegação e não deve receber o toque destinado à restauração.
            dismissBackupSavedMessage()
            rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Restaurar backup"))
            rule.onNodeWithText("Restaurar backup").performScrollTo().assertIsDisplayed().performClick()
            click(awaitSystemNode { it.text?.toString() == fileName })
            awaitApplication()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Senha do backup").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Senha do backup").performTextInput(secret)
            rule.onNodeWithText("Senha do backup").performImeAction()
            waitForKeyboard()
            rule.onNodeWithText("Conferir backup").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            rule.waitForIdle()
            rule.waitUntil(cryptoWaitMillis) { rule.onAllNodesWithText("Conferir restauração").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf(local), app.repository.exportRecords())
            val restore = rule.onNode(hasText("Restaurar backup") and hasAnyAncestor(hasTestTag("restore-preview-dialog")))
            restore.assertIsNotEnabled()
            rule.onNodeWithText("Substituir os dados locais por este backup").performClick()
            restore.performClick()
            rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().singleOrNull()?.id == expected.id } }
            assertEquals(expected, app.repository.exportRecords().single())
            assertEquals(expectedForecast, app.forecasts.dataset.first().rules.single())
            assertFalse(app.settings.snapshot().enabled)
        } catch (failure: Throwable) {
            val directory = java.io.File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            val bitmap = automation.takeScreenshot()
            if (bitmap != null) java.io.File(directory, "falha-seletor.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }
            fun describe(node: AccessibilityNodeInfo, depth: Int): String = if (depth > 16) "" else buildString {
                append("  ".repeat(depth)).append(node.className).append(" id=").append(node.viewIdResourceName)
                append(" text=").append(node.text).append(" description=").append(node.contentDescription)
                append(" enabled=").append(node.isEnabled).append(" clickable=").append(node.isClickable).append('\n')
                for (i in 0 until node.childCount) node.getChild(i)?.let { append(describe(it, depth + 1)) }
            }
            val tree = automation.rootInActiveWindow?.let { describe(it, 0) }.orEmpty()
            java.io.File(directory, "falha-seletor.txt").writeText(tree)
            throw AssertionError("Falha no seletor de arquivos do emulador. Hierarquia fictícia:\n$tree", failure)
        } finally {
            repeat(3) {
                if (automation.rootInActiveWindow?.packageName?.toString() != app.packageName) {
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    SystemClock.sleep(100)
                }
            }
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }

    private fun dismissBackupSavedMessage() {
        val message = hasText("Backup salvo.", substring = true)
        val dismissible = SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss) and
            (message or hasAnyDescendant(message))
        if (rule.onAllNodes(dismissible).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNode(dismissible).performSemanticsAction(SemanticsActions.Dismiss) { assertTrue(it()) }
        }
        rule.waitUntil(8000) { rule.onAllNodes(message).fetchSemanticsNodes().isEmpty() }
    }

    private fun awaitSystemNode(timeoutMillis: Long = 15000, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        try {
            // Avança a composição para que o pedido assíncrono de exportação possa abrir o seletor.
            rule.waitUntil(timeoutMillis) {
                val root = automation.rootInActiveWindow
                found = root?.takeIf { it.packageName?.toString() != app.packageName }?.let { find(it, predicate) }
                found != null
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("O seletor do Android não apresentou o controle esperado. Janela: ${automation.rootInActiveWindow?.packageName}", timeout)
        }
        return checkNotNull(found)
    }
    private fun awaitApplication() {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            if (automation.rootInActiveWindow?.packageName?.toString() == app.packageName) return
            SystemClock.sleep(100)
        }
        error("O seletor de arquivos não retornou ao aplicativo.")
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child, predicate)?.let { return it } }
        return null
    }
    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        // Na grade do DocumentsUI da API 26, o RecyclerView trata o toque sem marcar o texto como clicável.
        val control = target ?: node
        control.refresh()
        assertTrue("O controle do seletor precisa estar habilitado.", control.isEnabled)
        if (target == null || !control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            // O DocumentsUI antigo pode não executar ACTION_CLICK; o toque real continua sujeito ao resultado do fluxo.
            val bounds = android.graphics.Rect().also(control::getBoundsInScreen)
            assertFalse(bounds.isEmpty)
            val time = SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            val up = android.view.MotionEvent.obtain(time, time + 40, android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(down, true)); assertTrue(automation.injectInputEvent(up, true)) }
            finally { down.recycle(); up.recycle() }
        }
    }
    private fun waitForKeyboard() {
        // O diálogo tem janela própria; na API 26, os insets da Activity não enxergam seu teclado.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitUntil(5000) {
            val views = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            views.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        rule.waitForIdle()
    }
}
