package com.mateuswilliam.financas.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mateuswilliam.financas.MainActivity
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Forecast entry and actual notification review use the current primary Activity. */
class CommitmentEntryUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { ForecastReportTestDriver(rule) }
    private val app get() = ui.app
    private val dao get() = app.database.financeDao()

    @Before fun reset() = ui.reset()

    @Test fun installmentForecastDistributesEveryCentWithoutRecordingPayments() = runBlocking<Unit> {
        ui.startForecast(Movement.EXPENSE, "Notebook fictício", "60001", ForecastFrequency.INSTALLMENTS)
        ui.type("forecast-installment-count", "6")
        rule.onNodeWithText("1 parcelas de ${Money.format(10001)} e 5 de ${Money.format(10000)}")
            .performScrollTo().assertIsDisplayed()
        assertTrue(ui.current().rules.isEmpty())
        assertTrue(app.repository.exportRecords().isEmpty())
        ui.saveForecast()
        ui.waitRules(1)
        val saved = ui.current().rules.single()
        assertEquals(60001L, saved.amountCents)
        assertEquals(ForecastFrequency.INSTALLMENTS, saved.frequency)
        assertEquals(6, saved.installmentCount)
        var cycle = ui.period()
        val parts = (1..6).map {
            val part = ui.report(cycle).lines.single()
            cycle = CycleCalendar.periodContaining(cycle.endExclusive, ui.current().cycleRules)
            part
        }
        assertEquals((1..6).map { "installment:$it" }, parts.map { it.occurrenceKey })
        assertEquals(listOf(10001L, 10000L, 10000L, 10000L, 10000L, 10000L), parts.map { it.amountCents })
        assertEquals(60001L, parts.sumOf { it.amountCents })
        assertEquals((0L..5L).map { saved.startDate.plusMonths(it) }, parts.map { it.date })
        assertTrue(ui.report(cycle).lines.isEmpty())
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.activityRule.scenario.recreate()
        ui.waitRules(1)
        assertEquals(saved, ui.current().rules.single())
    }

    @Test fun notificationRevisionRequiresAcceptanceAndKeepsEditedActualCents() = runBlocking<Unit> {
        val timestamp = System.currentTimeMillis()
        val draft = DraftEntity("parcelada-ficticia", "fixture:parcelada", "fixture.local", "Banco fictício",
            "EXEMPLO FICTÍCIO", "Compra aprovada no cartão de R$ 129,90.", 12990, Movement.EXPENSE,
            PaymentMethod.CARD, Category.OTHER, "Dados fictícios", timestamp, timestamp - 60_000)
        dao.insertDraft(draft)
        ui.clickTag("dashboard-notification-review", dashboard = true)
        rule.waitUntil(10000) { rule.onAllNodesWithTag("review-${draft.id}").fetchSemanticsNodes().isNotEmpty() }
        ui.clickTag("review-${draft.id}")
        ui.type("amount-input", "13500")
        rule.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Compra revisada fictícia")
        rule.onNodeWithText("Descrição").performImeAction()
        app.repository.capture(NotificationInput(draft.sourcePackage, draft.sourceKey, draft.bank, "EXEMPLO atualizado",
            "Compra aprovada no cartão de R$ 139,90.", timestamp), simulation = true)
        rule.waitUntil(10000) { rule.onAllNodesWithText("Usar esta versão e manter a edição").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Confirmar lançamento").assertIsNotEnabled()
        assertTrue(app.repository.exportRecords().isEmpty())
        ui.clickText("Usar esta versão e manter a edição")
        ui.clickText("Confirmar lançamento")
        rule.waitUntil(10000) { runBlocking { app.repository.exportRecords().size == 1 } }
        val saved = app.repository.exportRecords().single()
        assertEquals(13500L, saved.amountCents)
        assertEquals(Movement.EXPENSE, saved.movement)
        assertEquals("Compra revisada fictícia", saved.description)
        assertEquals(draft.id, dao.allRecords().single().sourceDraftId)
        assertEquals("SAVED", dao.draftById(draft.id)?.status)
        assertEquals("", dao.draftById(draft.id)?.sourceBody)
        assertTrue(ui.current().rules.isEmpty())
        assertFalse(app.settings.snapshot().consented)
    }

    @Test fun incomeOccurrenceEditKeepsExactCentsAndPreservesTheFollowingCycle() = runBlocking<Unit> {
        ui.seed("salary", "Salário fictício", 300000, ForecastKind.INCOME, ForecastFrequency.MONTHLY, Category.SALARY)
        ui.waitRules(1)
        val cycle = ui.period()
        val next = CycleCalendar.periodContaining(cycle.endExclusive, ui.current().cycleRules)
        ui.edit(ui.report().lines.single())
        rule.onNodeWithText("Alterar só este mês").assertExists()
        ui.type("forecast-amount", "325019")
        ui.saveForecast()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 325019L }
        assertEquals(325019L, ui.report().incomeCents)
        assertEquals(300000L, ui.report(next).incomeCents)
        assertTrue(app.repository.exportRecords().isEmpty())
        rule.activityRule.scenario.recreate()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 325019L }
        assertEquals(325019L, ui.report().incomeCents)
        assertEquals(300000L, ui.report(next).incomeCents)
    }

    @Test fun ignoringAndRestoringAnExpenseOccurrenceLeavesIncomeAndFutureCyclesIntact() = runBlocking<Unit> {
        ui.seed("rent", "Aluguel fictício", 65000, frequency = ForecastFrequency.MONTHLY, category = Category.HOME)
        ui.seed("salary", "Salário fictício", 100000, ForecastKind.INCOME, ForecastFrequency.MONTHLY, Category.SALARY)
        ui.waitRules(2)
        val cycle = ui.period()
        val next = CycleCalendar.periodContaining(cycle.endExclusive, ui.current().cycleRules)
        ui.openActions(ui.report().lines.single { it.itemId == "rent" })
        ui.clickText("Pular este mês")
        ui.waitData { it.overrides.singleOrNull()?.ignored == true }
        assertEquals(0L, ui.report().totalExpensesCents)
        assertEquals(100000L, ui.report().incomeCents)
        assertEquals(65000L, ui.report(next).totalExpensesCents)
        ui.openActions(ui.report().lines.single { it.itemId == "rent" })
        ui.clickText("Restaurar este mês")
        ui.waitData { it.overrides.singleOrNull()?.ignored == false }
        assertEquals(65000L, ui.report().commitmentsCents)
        assertEquals(100000L, ui.report().incomeCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }

    @Test fun hiddenIncomeAmountRequiresAnExplicitRevealInTheCurrentEditor() = runBlocking<Unit> {
        ui.seed("salary", "Salário fictício", 325019, ForecastKind.INCOME, ForecastFrequency.MONTHLY, Category.SALARY)
        ui.waitRules(1)
        rule.runOnIdle { app.settings.hideAmounts(true) }
        ui.edit(ui.report().lines.single())
        rule.onNodeWithTag("forecast-amount").assertDoesNotExist()
        rule.onAllNodes(hasText(Money.format(325019), substring = true) or
            hasContentDescription(Money.format(325019), substring = true)).assertCountEquals(0)
        rule.onNodeWithText("Mostrar valor da previsão para edição").performScrollTo().performClick()
        rule.onNodeWithTag("forecast-amount").assertExists()
        ui.saveForecast()
        ui.waitData { it.overrides.singleOrNull()?.amountCents == 325019L }
        assertEquals(325019L, ui.report().incomeCents)
        assertTrue(app.repository.exportRecords().isEmpty())
    }
}
