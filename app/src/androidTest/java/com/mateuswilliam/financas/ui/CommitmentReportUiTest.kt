package com.mateuswilliam.financas.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.Appearance
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The current report is the cycle projection; forecasts stay separate from actual records. */
class CommitmentReportUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { ForecastReportTestDriver(rule) }
    private val app get() = ui.app

    @Before fun reset() = ui.reset()

    @Test fun selectedCycleGroupsRecordedAndForecastExpensesWithoutLosingTheActualRecord() = runBlocking<Unit> {
        val cycle = ui.period()
        val next = CycleCalendar.periodContaining(cycle.endExclusive, ui.current().cycleRules)
        ui.seed("renda", "Receita deste ciclo", 100000, ForecastKind.INCOME)
        ui.seed("renda-seguinte", "Receita do próximo ciclo", 200000, ForecastKind.INCOME, start = next.start)
        ui.seed("aluguel", "Aluguel fictício", 20000, frequency = ForecastFrequency.MONTHLY, category = Category.HOME)
        ui.seed("curso", "Curso fictício", 10000, category = Category.EDUCATION)
        ui.seed("livro", "Livro fictício", 2000)
        ui.seed("item", "Item fictício", 1000)
        val actual = payment("despesa-avulsa", 90000, cycle)
        app.repository.save(actual, null)
        ui.waitRules(6)
        assertEquals(33000L, ui.report(cycle).totalExpensesCents)
        assertEquals(67000L, ui.report(cycle).marginCents)
        scrollCategories()
        assertCategory(Category.OTHER, 93000)
        assertCategory(Category.HOME, 20000)
        rule.runOnIdle { app.settings.setAppearance(Appearance.LIGHT) }
        rule.waitForIdle(); capture("previsoes-categorias-claro")
        rule.runOnIdle { app.settings.setAppearance(Appearance.DARK) }
        rule.waitForIdle(); capture("previsoes-categorias-escuro")
        ui.openProjections()
        ui.assertProjection(cycle, "123,00", -23000)
        ui.assertProjection(next, "10,00", 180000)

        rule.openRendlyPlanner()
        ui.edit(ui.report(cycle).lines.single { it.itemId == "curso" })
        ui.type("forecast-amount", "13000"); ui.saveForecast()
        ui.waitData { it.rules.singleOrNull { value -> value.itemId == "curso" }?.amountCents == 13000L }
        assertEquals(36000L, ui.report(cycle).totalExpensesCents)
        assertEquals(20000L, ui.report(next).totalExpensesCents)
        assertEquals(listOf(actual), app.repository.exportRecords())
        ui.openProjections()
        ui.assertProjection(cycle, "126,00", -26000)
        ui.assertProjection(next, "10,00", 180000)
    }

    @Test fun aboveIncomeShowsTheActualPercentageAndNegativeMargin() = runBlocking<Unit> {
        val cycle = ui.period()
        ui.seed("renda", "Receita prevista", 100000, ForecastKind.INCOME)
        ui.seed("compra-maior", "Compra fictícia", 125000)
        ui.waitRules(2)
        ui.openProjections()
        ui.assertProjection(cycle, "125,00", -25000)
        assertEquals(125000L, ui.report(cycle).totalExpensesCents)
        assertEquals(-25000L, ui.report(cycle).marginCents)
        assertEquals(0L, ui.report(cycle).unallocatedCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun hiddenValuesRemoveAmountsPercentagesAndCategoryGeometry() = runBlocking<Unit> {
        val cycle = ui.period()
        ui.seed("renda", "Receita prevista", 100000, ForecastKind.INCOME)
        ui.seed("compra-maior", "Compra fictícia", 125000)
        ui.waitRules(2)
        scrollCategories()
        val geometry = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        assertTrue(rule.onAllNodes(geometry, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        ui.openProjections()
        ui.assertProjection(cycle, "125,00", -25000)
        rule.runOnIdle { app.settings.hideAmounts(true) }
        rule.onNodeWithTag("projection-margin-${cycle.start}")
            .assertTextEquals("••••").assertContentDescriptionEquals("Valor oculto")
        rule.onAllNodesWithText("Percentual oculto").onFirst().assertIsDisplayed()
        assertPrivateNumbersAbsent()
        rule.openRendlyPlanner()
        scrollCategories()
        rule.onAllNodes(geometry, useUnmergedTree = true).assertCountEquals(0)
        assertPrivateNumbersAbsent()
        assertEquals(125000L, ui.report(cycle).totalExpensesCents)
        assertEquals(-25000L, ui.report(cycle).marginCents)
    }

    @Test fun absentIncomeKeepsPlannedExpensesAndOffersTheForecastIncomeEditor() = runBlocking<Unit> {
        val cycle = ui.period()
        ui.seed("sem-renda", "Conta fictícia", 9500)
        ui.waitRules(1)
        scrollCategories(); assertCategory(Category.OTHER, 9500)
        ui.openProjections()
        ui.assertProjection(cycle, null, -9500)
        assertEquals(9500L, ui.report(cycle).totalExpensesCents)
        assertNull(ui.report(cycle).expensePercentage)
        rule.openRendlyPlanner()
        ui.startForecast(Movement.INCOME, "Receita prevista", "100000")
        rule.onNode(hasText("Nova previsão") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithContentDescription("Tipo").assertTextContains("Receita")
        rule.onNodeWithTag("forecast-amount").assertExists()
        assertEquals(1, ui.current().rules.size)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun enlargedFontKeepsRoundedPercentageAndExactOneCentExcessReadable() = runBlocking<Unit> {
        val cycle = ui.period()
        ui.seed("renda", "Receita prevista", Money.MAX_CENTS, ForecastKind.INCOME)
        ui.seed("renda-inteira", "Compra fictícia", Money.MAX_CENTS)
        ui.seed("um-centavo", "Ajuste fictício", 1)
        ui.waitRules(3)
        val original = shell("settings get system font_scale").trim().toFloatOrNull()
        try {
            shell("settings put system font_scale 1.6")
            rule.waitUntil(10000) { rule.activity.resources.configuration.fontScale >= 1.5f }
            ui.openProjections()
            ui.assertProjection(cycle, "100,00", -1)
            assertEquals(Money.MAX_CENTS + 1, ui.report(cycle).totalExpensesCents)
            assertEquals(-1L, ui.report(cycle).marginCents)
            assertEquals("100.00", ui.report(cycle).expensePercentage?.toPlainString())
            // The product rounds percentages to two places; the margin retains the one-cent excess.
            rule.onNodeWithTag("forecast-projections").performScrollToNode(hasTestTag("projection-margin-${cycle.start}"))
            rule.onNodeWithTag("projection-margin-${cycle.start}").assertIsDisplayed()
                .assertTextEquals(Money.format(-1)).assertReadableLayout()
            val percentage = hasText("Gastos totais: 100,00% da renda") and
                hasAnySibling(hasTestTag("projection-margin-${cycle.start}"))
            rule.onNodeWithTag("forecast-projections").performScrollToNode(percentage)
            rule.onNode(percentage).assertIsDisplayed().assertReadableLayout()
            capture("previsoes-fonte-ampliada")
        } finally {
            if (original != null) shell("settings put system font_scale $original")
            else shell("settings delete system font_scale")
            rule.waitUntil(10000) { kotlin.math.abs(rule.activity.resources.configuration.fontScale - (original ?: 1f)) < 0.1f }
        }
    }

    private fun scrollCategories() {
        rule.openRendlyCommitment()
        rule.onNodeWithTag("commitment-screen").performScrollToNode(hasText("Gastos por categoria"))
    }

    private fun assertCategory(category: Category, cents: Long) {
        val value = Money.format(cents)
        val categoryRegion = hasAnyAncestor(hasTestTag("forecast-categories"))
        rule.onNodeWithTag("commitment-screen").performScrollToNode(hasText(value) and categoryRegion)
        val labels = rule.onAllNodes(hasText(category.label) and categoryRegion, useUnmergedTree = true).fetchSemanticsNodes()
        val sameRow = SemanticsMatcher("$value na mesma linha de ${category.label}") { amount ->
            val amountBounds = amount.boundsInRoot
            labels.any { label ->
                val labelBounds = label.boundsInRoot
                labelBounds.width > 0 && labelBounds.height > 0 &&
                    amountBounds.width > 0 && amountBounds.height > 0 &&
                    amountBounds.left >= labelBounds.right - 1f &&
                    minOf(amountBounds.bottom, labelBounds.bottom) - maxOf(amountBounds.top, labelBounds.top) >= minOf(amountBounds.height, labelBounds.height) / 2
            }
        }
        // Layout-only Rows flatten into one semantics parent; sibling membership is insufficient.
        rule.onNode(hasText(value) and categoryRegion and sameRow, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertPrivateNumbersAbsent() {
        listOf("125,00%", "1.250,00", "1.000,00", "250,00").forEach { value ->
            rule.onAllNodes(hasText(value, substring = true) or hasContentDescription(value, substring = true),
                useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun SemanticsNodeInteraction.assertReadableLayout() =
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(action(layouts))
            assertTrue("O texto precisa fornecer seu layout", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse("Texto cortado nas restrições ${layout.layoutInput.constraints}", layout.hasVisualOverflow)
            }
        }

    private fun payment(id: String, cents: Long, cycle: BudgetPeriod) = Record(id, cents, Movement.EXPENSE,
        PaymentMethod.CARD, Category.OTHER, "Conta fictícia", "Pagamento fictício",
        cycle.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

    private fun capture(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
