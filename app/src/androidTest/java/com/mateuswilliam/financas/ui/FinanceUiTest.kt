package com.mateuswilliam.financas.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Before fun reset() { runBlocking { app.repository.clear() }; rule.waitForIdle() }

    @Test fun manualEntryWorksWithoutNotificationAccess() {
        rule.onNodeWithContentDescription("Novo lançamento").performClick()
        rule.onNodeWithText("Valor (R$)").performTextInput("42,90")
        rule.onNodeWithText("Descrição").performTextInput("Mercado de exemplo")
        rule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Novo lançamento").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Extrato").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Mercado de exemplo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mercado de exemplo").assertIsDisplayed()
    }

    @Test fun decliningDisclosureKeepsCaptureDisabled() {
        rule.onNodeWithText("Ajustes").performClick()
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
        rule.onNodeWithText("Revisar").performClick()
        rule.onNodeWithText("Caixa de revisão").assertIsDisplayed()
        screenshot("pendencias")
        rule.onNodeWithTag("review-pendencia-exemplo").performClick()
        rule.onNodeWithText("Conferir movimentação").assertIsDisplayed()
        screenshot("revisao")
    }

    private fun screenshot(name: String) {
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
