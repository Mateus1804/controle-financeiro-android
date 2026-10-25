package com.mateuswilliam.financas.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Usa o seletor real do Android no emulador dedicado, somente com arquivo e senha fictícios. */
class BackupDocumentFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val secret = "Arquivo fictício 2026!"

    @Before fun reset() { runBlocking { app.repository.clear() }; app.settings.finishIntro(); rule.waitForIdle() }

    @Test fun createAndRestoreThroughTheAndroidDocumentPicker() = runBlocking<Unit> {
        val expected = Record("backup-document-ui", 123456, Movement.EXPENSE, PaymentMethod.PIX, Category.FOOD,
            "Conta fictícia", "Recuperação fictícia pelo seletor", System.currentTimeMillis())
        app.repository.save(expected, null)
        val info = automation.serviceInfo
        val oldFlags = info.flags
        automation.serviceInfo = info.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            rule.onNode(hasText("Ajustes") and hasClickAction()).performClick()
            rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Criar backup criptografado"))
            rule.onNodeWithText("Criar backup criptografado").performClick()
            rule.onNodeWithText("Senha do backup").performTextInput(secret)
            rule.onNodeWithText("Repetir senha").performTextInput(secret)
            rule.onNodeWithText("Repetir senha").performImeAction()
            waitForKeyboard()
            rule.onNodeWithText("Preparar backup").performClick()
            val fileName = "backup-ficticio-${UUID.randomUUID()}.cfbackup"
            val name = awaitSystemNode { it.className?.toString() == "android.widget.EditText" && it.isEditable }
            assertTrue(name.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
            }))
            click(awaitSystemNode { it.isEnabled && (it.viewIdResourceName?.endsWith(":id/button_save") == true || it.text?.toString() in setOf("Save", "SAVE", "Salvar", "SALVAR")) })
            awaitApplication()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Backup salvo.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            app.repository.clear()
            rule.runOnIdle { app.settings.finishIntro() }
            rule.waitForIdle()
            assertTrue(app.repository.exportRecords().isEmpty())
            // O aviso temporário fica acima da navegação e não deve receber o toque destinado à restauração.
            rule.waitUntil(8000) { rule.onAllNodesWithText("Backup salvo.", substring = true).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Restaurar backup"))
            rule.onNodeWithText("Restaurar backup").performScrollTo().assertIsDisplayed().performClick()
            click(awaitSystemNode { it.text?.toString() == fileName })
            awaitApplication()
            rule.waitUntil(10000) { rule.onAllNodesWithText("Senha do backup").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Senha do backup").performTextInput(secret)
            rule.onNodeWithText("Senha do backup").performImeAction()
            waitForKeyboard()
            rule.onNodeWithText("Conferir backup").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Conferir restauração").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.repository.exportRecords().isEmpty())
            val restore = rule.onNode(hasText("Restaurar backup") and hasAnyAncestor(hasTestTag("restore-preview-dialog")))
            restore.assertIsNotEnabled()
            rule.onNodeWithText("Substituir os dados locais por este backup").performClick()
            restore.performClick()
            rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
            assertEquals(expected, app.repository.exportRecords().single())
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

    private fun awaitSystemNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName?.toString() != app.packageName) {
                find(root, predicate)?.let { return it }
            }
            SystemClock.sleep(100)
        }
        error("O seletor do Android não apresentou o controle esperado. Janela: ${automation.rootInActiveWindow?.packageName}")
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
        rule.waitUntil(5000) {
            val views = if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else listOf(rule.activity.window.decorView)
            views.none { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        }
        rule.waitForIdle()
    }
}
