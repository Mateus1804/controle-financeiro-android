package com.mateuswilliam.financas.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class FinanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FinanceApplication

    @Before fun reset() {
        runBlocking { app.maintenanceReady.await(); app.repository.clear() }
        app.settings.reset()
        waitFor(hasTestTag("sheet-summary"))
    }

    @Test fun spreadsheetExampleUsesOneEditorAndDoesNotDoubleCountReserveTransfers() {
        openEntry(SheetEntryKind.INCOME)
        amount("400000")
        description("Salário fictício")
        save()
        waitRecords(1)

        openEntry(SheetEntryKind.EXPENSE)
        amount("250000")
        description("Moradia fictícia")
        save()
        waitRecords(2)

        openEntry(SheetEntryKind.TRANSFER)
        amount("100000")
        rule.onNodeWithContentDescription("Destino").performScrollTo().performClick()
        clickText(AccountFunds.RESERVE_NAME)
        waitFor(hasContentDescription("Destino") and hasText(AccountFunds.RESERVE_NAME))
        save()
        waitRecords(3)

        assertMetric("sheet-result", 150000)
        assertMetric("sheet-income", 400000)
        assertMetric("sheet-expense", 250000)
        assertMetric("sheet-wealth", 150000)
        assertMetric("sheet-operating", 50000)
        assertMetric("sheet-reserve", 100000)
        assertMetric("sheet-saved", 100000)

        scrollSummary(hasTestTag("sheet-chart"))
        rule.onNodeWithText(ExpenseLegendText.percentage(10000)).assertExists()
        scrollSummary(hasText(Category.OTHER.label) and hasClickAction())
        clickText(Category.OTHER.label)
        waitFor(hasTestTag("records-list"))
        rule.onNodeWithText("Moradia fictícia").assertExists()
        rule.onNodeWithText("Salário fictício").assertDoesNotExist()
        rule.onNodeWithText("Transferência entre minhas contas").assertDoesNotExist()
    }

    @Test fun openingBalanceAndExpenseShareTheListAndUpdateAfterEditing() {
        openEntry(SheetEntryKind.OPENING)
        amount("100000")
        save()
        rule.waitUntil(10000) { runBlocking { app.repository.snapshot().accountBalanceEvents.size == 1 } }
        assertMetric("sheet-result", 0)
        assertMetric("sheet-wealth", 100000)

        openEntry(SheetEntryKind.EXPENSE)
        amount("R$ 125,32")
        description("Compra fictícia")
        save()
        waitRecords(1)
        assertMetric("sheet-result", -12532)
        assertMetric("sheet-wealth", 87468)

        clickText("Lançamentos")
        val row = hasText("Compra fictícia") and hasClickAction()
        waitFor(hasTestTag("records-list"))
        rule.onNodeWithTag("records-list").performScrollToNode(row)
        rule.onNode(row).performClick()
        rule.onNodeWithTag("amount-input").assertTextContains("125,32")
        amount("25000")
        save()
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().single().amountCents == 25000L } }
        clickText("Resumo")
        assertMetric("sheet-wealth", 75000)
        assertMetric("sheet-result", -25000)
    }

    @Test fun formKeepsTheDraftAfterRecreationAndRejectsAnInvalidAmount() {
        openEntry(SheetEntryKind.EXPENSE)
        amount("1234")
        description("Rascunho fictício")
        rule.activityRule.scenario.recreate()
        waitFor(hasTestTag("amount-input"))
        rule.onNodeWithTag("amount-input").assertTextContains("12,34")
        rule.onNodeWithTag("sheet-description").assertTextContains("Rascunho fictício")

        amount("R$ 1,234")
        save(expectClosed = false)
        rule.onNodeWithTag("sheet-entry-save").assertExists()
        assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })

        amount("987")
        save()
        waitRecords(1)
        val record = runBlocking { app.repository.exportRecords().single() }
        assertEquals(987L, record.amountCents)
        assertEquals("Rascunho fictício", record.description)
    }

    @Test fun changingTheCycleInSettingsUpdatesBothTabs() {
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("Início do ciclo"))
        clickText("Início do ciclo")
        rule.onNodeWithContentDescription("Dia de início").performClick()
        clickText("Dia 5")
        rule.onNodeWithTag("cycle-save").performClick()
        rule.waitUntil(10000) { runBlocking { app.forecasts.dataset.first().cycleRules.lastOrNull()?.day == 5 } }
        rule.onNodeWithContentDescription("Voltar").performClick()
        waitFor(hasTestTag("sheet-summary"))
        assertCycleDayFive()
        clickText("Lançamentos")
        assertCycleDayFive()
    }

    @Test fun hidingValuesConcealsAmountsAndPercentagesInTheSummary() {
        val record = Record("private-example", 12345, Movement.EXPENSE, PaymentMethod.UNKNOWN,
            Category.FOOD, "Carteira", "Despesa fictícia de R$ 123,45", System.currentTimeMillis())
        runBlocking { assertTrue(app.repository.save(record, null)) }
        assertMetric("sheet-result", -12345)
        rule.onNodeWithContentDescription("Ocultar valores").performClick()
        scrollSummary(hasTestTag("sheet-chart"))
        rule.onAllNodesWithText(Money.format(12345), substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText(ExpenseLegendText.percentage(10000), useUnmergedTree = true).assertCountEquals(0)
        clickText("Lançamentos")
        waitFor(hasTestTag("records-list"))
        rule.onNodeWithText("Despesa fictícia de ••••").assertExists()
    }

    @Test fun anArchivedWalletIsNeverSuggestedForANewEntry() {
        runBlocking {
            app.repository.addAccount("Carteira")
            app.repository.setAccountArchived(AccountNames.key("Carteira"), true)
        }
        assertMetric("sheet-wealth", 0)
        scrollSummary(hasTestTag("sheet-account-list"))
        openEntry(SheetEntryKind.EXPENSE)
        rule.onNodeWithContentDescription("Conta").assertTextContains("Escolher conta")
    }

    @Test fun editorRemainsUsableOnANarrowScreenAtDoubleFontScale() {
        val previousSize = Regex("Override size: ([0-9]+x[0-9]+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val previousFont = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        try {
            shell("wm size 640x1280")
            shell("settings put system font_scale 2.0")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.9f &&
                rule.activity.resources.configuration.screenWidthDp <= 320 }
            waitFor(hasText("Resumo") and hasClickAction())
            waitFor(hasText("Lançamentos") and hasClickAction())
            openEntry(SheetEntryKind.EXPENSE)
            amount("1234")
            description("Fonte ampliada fictícia")
            save()
            waitRecords(1)
            assertMetric("sheet-result", -1234)
        } finally {
            shell("settings put system font_scale $previousFont")
            shell("wm size $previousSize")
        }
    }

    @Test fun summaryInBothThemesUsesTheSameRecordedValues() {
        val now = System.currentTimeMillis()
        runBlocking {
            app.repository.save(Record("preview-income", 400000, Movement.INCOME, PaymentMethod.UNKNOWN,
                Category.SALARY, "Carteira", "Salário fictício", now), null)
            app.repository.save(Record("preview-home", 180000, Movement.EXPENSE, PaymentMethod.UNKNOWN,
                Category.HOME, "Carteira", "Aluguel fictício", now), null)
            app.repository.save(Record("preview-food", 70000, Movement.EXPENSE, PaymentMethod.UNKNOWN,
                Category.FOOD, "Carteira", "Mercado fictício", now), null)
            app.repository.save(Record("preview-saved", 100000, Movement.TRANSFER, PaymentMethod.UNKNOWN,
                Category.OTHER, "Carteira", "Reserva fictícia", now, AccountFunds.RESERVE_NAME), null)
        }
        for (mode in listOf(Appearance.LIGHT, Appearance.DARK)) {
            rule.runOnIdle { app.settings.setAppearance(mode) }
            assertMetric("sheet-result", 150000)
            rule.onNodeWithTag("sheet-summary").performScrollToIndex(0)
            rule.waitForIdle()
            val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (screen != null) {
                val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
                try { File(dir, "rendly-planilha-${mode.name.lowercase()}.png").outputStream().use {
                    screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } } finally { screen.recycle() }
            }
        }
    }

    private fun openEntry(kind: SheetEntryKind) {
        waitFor(hasTestTag("add-movement")).performClick()
        if (kind != SheetEntryKind.EXPENSE) {
            rule.onNodeWithContentDescription("Tipo de lançamento").performClick()
            clickText(kind.title)
        }
    }

    private fun amount(value: String) = rule.onNodeWithTag("amount-input").performScrollTo().performTextReplacement(value)
    private fun description(value: String) =
        rule.onNodeWithTag("sheet-description").performScrollTo().performTextReplacement(value)

    private fun save(expectClosed: Boolean = true) {
        rule.onNodeWithTag("sheet-entry-save").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        if (expectClosed) rule.waitUntil(10000) { rule.onAllNodesWithTag("sheet-entry-save").fetchSemanticsNodes().isEmpty() }
    }

    private fun clickText(text: String) = waitFor(hasText(text) and hasClickAction()).performClick()

    private fun waitFor(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        rule.waitUntil(10000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return rule.onNode(matcher)
    }

    private fun waitRecords(count: Int) = rule.waitUntil(10000) {
        runBlocking { app.repository.exportRecords().size == count }
    }

    private fun scrollSummary(matcher: SemanticsMatcher) {
        rule.waitUntil(10000) {
            try {
                rule.onNodeWithTag("sheet-summary").performScrollToNode(matcher)
                true
            } catch (_: AssertionError) { false }
        }
    }

    private fun assertMetric(tag: String, cents: Long) {
        val matcher = hasTestTag(tag) and hasText(Money.format(cents))
        scrollSummary(matcher)
        rule.onNode(matcher).assertExists()
    }

    private fun assertCycleDayFive() {
        val today = LocalDate.now()
        val start = if (today.dayOfMonth >= 5) today.withDayOfMonth(5) else today.minusMonths(1).withDayOfMonth(5)
        val label = "${start.format(financeDateFormat)} a ${start.plusMonths(1).minusDays(1).format(financeDateFormat)}"
        waitFor(hasTestTag("financial-period") and hasText(label))
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
