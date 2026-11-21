package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mateuswilliam.financas.FinanceApplication
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.ForecastViewModel
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/** Only drives the production Activity and its existing forecast controls. */
internal class CurrentForecastUi(
    private val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
) {
    val app get() = rule.activity.application as FinanceApplication
    val model get() = ViewModelProvider(rule.activity)[ForecastViewModel::class.java]
    val month: YearMonth = YearMonth.now()
    private var selectedMonth = month

    fun reset() {
        runBlocking { app.repository.clear(); Unit }
        app.settings.finishIntro()
        app.settings.dismissNamePrompt()
        model.refresh()
        waitData { it.rules.isEmpty() && it.overrides.isEmpty() && it.cycleRules.isEmpty() }
        val finance = ViewModelProvider(rule.activity)[FinanceViewModel::class.java]
        rule.waitUntil(10000) { finance.state.value.let { it.loaded && it.records.isEmpty() && it.pending.isEmpty() } }
        rule.openRendlyPlanner()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("forecast-dashboard").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    fun data(): ForecastDataset = runBlocking { app.forecasts.dataset.first() }
    fun report(month: YearMonth = this.month): ForecastReport = ForecastCalculator.report(
        data(), BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1)),
    )
    fun seed(vararg rules: ForecastRule) {
        runBlocking { rules.forEach { app.forecasts.saveRule(it, replaceFuture = false) } }
        waitData { it.rules.containsAll(rules.toList()) }
    }
    fun waitData(predicate: (ForecastDataset) -> Boolean) {
        rule.waitUntil(10000) { model.dataset.value?.let(predicate) == true }
    }
    fun scroll(tag: String) {
        if (tag.startsWith("forecast-cycle-")) {
            rule.openRendlyPlanner()
            rule.onNodeWithTag("forecast-dashboard").performScrollToNode(hasTestTag(tag))
        } else {
            rule.openRendlyMonth()
            rule.scrollToLoadedItem("records-list", hasTestTag(tag))
        }
    }
    fun clickTag(tag: String) {
        if (tag == "add-realized-income" || tag == "add-realized-expense") {
            rule.newRendlyRecord(if (tag.endsWith("income")) Movement.INCOME else Movement.EXPENSE); return
        }
        if (tag == "open-accounts") { rule.openRendlySettings(); rule.onNodeWithTag("settings-accounts").performClick(); return }
        if (tag == "forecast-settings") { rule.openRendlySettings(); return }
        if (tag == "dashboard-notification-review") { rule.openRendlyReview(); return }
        val target = hasTestTag(tag)
        rule.waitUntil(10000) { rule.onAllNodes(target and isEnabled()).fetchSemanticsNodes().size == 1 }
        rule.onNode(target).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    fun clickText(text: String) {
        val target = hasText(text) and hasClickAction()
        rule.waitUntil(10000) { rule.onAllNodes(target and isEnabled()).fetchSemanticsNodes().size == 1 }
        rule.onNode(target).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    fun action(line: ForecastLine, label: String) {
        val tag = "forecast-actions:${line.itemId}:${line.occurrenceKey}"
        scroll(tag)
        clickTag(tag)
        clickText(label)
    }
    fun open(line: ForecastLine) = action(line, if (line.frequency == ForecastFrequency.ONCE) "Editar" else "Alterar só este mês")
    fun showMonth(target: YearMonth) {
        while (selectedMonth != target) {
            val next = selectedMonth < target
            val tag = if (next) "forecast-cycle-next" else "forecast-cycle-previous"
            scroll(tag)
            clickTag(tag)
            selectedMonth = selectedMonth.plusMonths(if (next) 1 else -1)
        }
        rule.waitForIdle()
    }
    fun saveEditor() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        clickTag("forecast-save")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("forecast-save").fetchSemanticsNodes().isEmpty() }
    }
    fun assertNoRecords() = assertTrue(runBlocking { app.repository.exportRecords().isEmpty() })
}
