package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.time.LocalDate

/** Drives only the planner installed by MainActivity, with no alternate UI or launch extras. */
internal class ForecastReportTestDriver(
    val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
) {
    val app get() = rule.activity.application as FinanceApplication
    val model get() = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
    val today get() = LocalDate.now()
    fun current() = runBlocking { app.forecasts.dataset.first() }
    fun period() = CycleCalendar.periodContaining(today, current().cycleRules)
    fun report(period: BudgetPeriod = period()) = ForecastCalculator.report(current(), period)

    fun reset() {
        runBlocking {
            app.maintenanceReady.await()
            app.repository.clear()
            Unit
            app.forecasts.setCycleDay(1, today)
        }
        app.settings.finishIntro()
        app.settings.dismissNamePrompt()
        model.refresh()
        waitData { it.rules.isEmpty() && it.cycleRules.lastOrNull()?.day == 1 }
        rule.openRendlyPlanner()
        rule.waitForIdle()
    }

    fun waitData(predicate: (ForecastDataset) -> Boolean) {
        rule.waitUntil(10000) { model.dataset.value?.let(predicate) == true && !model.busy.value }
        rule.waitForIdle()
    }
    fun waitRules(count: Int) = waitData { it.rules.size == count }
    fun scroll(tag: String) {
        if (tag.startsWith("salary-") || tag.startsWith("payday-")) {
            rule.openRendlyPlanner(); rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag(tag))
        } else { rule.openRendlyPlanner(); rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag(tag)) }
    }
    fun clickTag(tag: String, dashboard: Boolean = false) {
        if (tag == "dashboard-notification-review") { rule.openRendlyReview(); return }
        if (tag == "open-accounts") { rule.openRendlySettings(); rule.onNodeWithTag("settings-accounts").performClick(); return }
        if (tag == "forecast-settings") { rule.openRendlySettings(); return }
        if (tag == "add-realized-expense" || tag == "add-realized-income") {
            rule.newRendlyRecord(if (tag.endsWith("income")) Movement.INCOME else Movement.EXPENSE); return
        }
        if (dashboard) { rule.openRendlyPlanner(); scroll(tag) }
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    fun clickText(text: String) {
        rule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    fun type(tag: String, value: String) {
        rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }
    fun saveForecast() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        clickTag("forecast-save")
    }
    fun openActions(line: ForecastLine) = clickTag("forecast-actions:${line.itemId}:${line.occurrenceKey}", dashboard = true)
    fun edit(line: ForecastLine) { openActions(line); clickText(if (line.frequency == ForecastFrequency.ONCE) "Editar" else "Alterar só este mês") }
    fun openProjections() = rule.openRendlyProjections()
    fun assertProjection(period: BudgetPeriod, percentage: String?, marginCents: Long) {
        val text = "Gastos totais: ${percentage?.let { "$it% da renda" } ?: "renda não informada"}"
        rule.onNodeWithTag("forecast-projections").performScrollToNode(hasTestTag("projection-margin-${period.start}"))
        rule.onNodeWithTag("projection-margin-${period.start}").assertTextContains(Money.format(marginCents))
        val percentageMatcher = hasText(text) and hasAnySibling(hasTestTag("projection-margin-${period.start}"))
        rule.onNodeWithTag("forecast-projections").performScrollToNode(percentageMatcher)
        rule.onNode(percentageMatcher).assertIsDisplayed()
    }
    suspend fun seed(id: String, title: String, cents: Long, kind: ForecastKind = ForecastKind.EXPENSE,
        frequency: ForecastFrequency = ForecastFrequency.ONCE, category: Category = Category.OTHER,
        start: LocalDate = period().start, count: Int = 1) {
        app.forecasts.saveRule(ForecastRule(id, start, title, kind, cents, category, frequency, start, installmentCount = count))
    }
}
