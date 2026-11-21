package com.mateuswilliam.financas.core

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class PaydayBudgetTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun line(id: String, date: LocalDate, cents: Long, kind: ForecastKind = ForecastKind.EXPENSE,
                     category: Category = Category.HOME, group: ForecastGroup = ForecastGroup.COMMITMENT, included: Boolean = true): ForecastLine {
        val rule = ForecastRule(id, date, id, kind, cents, category, ForecastFrequency.ONCE, date, group = group)
        return ForecastLine(id, "once", id, date, cents, kind, category, group, ForecastFrequency.ONCE, false, !included, included, "", false, rule)
    }

    @Test fun `escolhe o proximo salario previsto ignorando passado e outras receitas`() {
        val lines = listOf(line("velho", today.minusDays(1), 1, ForecastKind.INCOME, Category.SALARY),
            line("bonus", today.plusDays(2), 1, ForecastKind.INCOME, Category.OTHER),
            line("sal2", today.plusDays(20), 1, ForecastKind.INCOME, Category.SALARY),
            line("sal1", today.plusDays(10), 1, ForecastKind.INCOME, Category.SALARY))
        assertEquals(today.plusDays(10), PaydayBudgets.nextPayday(lines, emptyList(), today))
        assertEquals(today.plusDays(3), PaydayBudgets.nextPayday(lines, listOf(today.plusDays(3)), today))
        assertNull(PaydayBudgets.nextPayday(emptyList(), emptyList(), today))
    }
    @Test fun `desconta apenas compromissos e nao desconta a reserva`() {
        val payday = today.plusDays(14)
        val lines = listOf(line("aluguel", today.plusDays(3), 100000), line("antes", today.minusDays(1), 5000),
            line("no-dia", payday, 7000), line("variavel", today.plusDays(2), 9000, group = ForecastGroup.VARIABLE),
            line("ignorada", today.plusDays(4), 8000, included = false))
        val budget = PaydayBudgets.compute(300000, lines, today, payday)
        assertEquals(100000L, budget.pendingCommitmentsCents)
        assertEquals(200000L, budget.spendableCents)
        assertEquals(14, budget.daysRemaining)
        assertEquals(100000L, budget.weeklyCents)
        assertEquals(0L, budget.deficitCents)
    }
    @Test fun `media semanal e proporcional aos dias restantes`() {
        val budget = PaydayBudgets.compute(10000, emptyList(), today, today.plusDays(10))
        assertEquals(7000L, budget.weeklyCents)
    }
    @Test fun `deficit considera somente compromissos acima do saldo`() {
        val budget = PaydayBudgets.compute(50000, listOf(line("a", today, 60000)), today, today.plusDays(7))
        assertEquals(-10000L, budget.spendableCents)
        assertEquals(10000L, budget.deficitCents); assertEquals(0L, budget.totalCents); assertEquals(0L, budget.weeklyCents)
    }
    @Test fun `pagamento hoje nao tem media semanal`() {
        assertNull(PaydayBudgets.compute(1000, emptyList(), today, today).weeklyCents)
    }
    @Test fun `mil reais menos trezentos em dez dias deixa setecentos e media de quatrocentos e noventa`() {
        val budget = PaydayBudgets.compute(100000, listOf(line("conta", today.plusDays(2), 30000)), today, today.plusDays(10))
        assertEquals(70000L, budget.totalCents)
        assertEquals(49000L, budget.weeklyCents)
    }

    @Test fun `variaveis ficam em cenario separado sem duplicar ocorrencias`() {
        val variable = line("variavel", today.plusDays(2), 20000, group = ForecastGroup.VARIABLE)
        val lines = listOf(line("fixa", today.plusDays(1), 30000), variable, variable,
            line("ignorada", today.plusDays(2), 9000, group = ForecastGroup.VARIABLE, included = false))
        val budget = PaydayBudgets.compute(100000, lines, today, today.plusDays(10))
        assertEquals(70000L, budget.totalCents)
        assertEquals(20000L, budget.variableForecastCents)
        assertEquals(50000L, budget.withVariablesCents)
        assertEquals(49000L, budget.weeklyCents)
    }

    private val zone = ZoneOffset.UTC
    private fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun ongoingUndatedExpenseRemainsInTheVariableScenario() {
        val start = LocalDate.of(2026, 11, 5)
        val payday = start.plusMonths(1)
        val groceries = ForecastRule("groceries", start, "Mercado", ForecastKind.EXPENSE, 9000,
            Category.FOOD, ForecastFrequency.MONTHLY, start, cycleAnchored = true, group = ForecastGroup.VARIABLE)
        val pastBill = groceries.copy(itemId = "past", title = "Conta vencida", amountCents = 12000,
            frequency = ForecastFrequency.ONCE, cycleAnchored = false, group = ForecastGroup.COMMITMENT)
        val commitment = groceries.copy(itemId = "fixed", title = "Compromisso sem data", group = ForecastGroup.COMMITMENT)
        val data = ForecastDataset(rules = listOf(groceries, pastBill, commitment), cycleRules = listOf(CycleRule(start, 5)))
        val lines = PaydayBudgets.upcomingLines(data, emptyList(), zone, at(today))
        val budget = PaydayBudgets.compute(100000, lines, today, payday)
        assertEquals(100000L, budget.spendableCents)
        assertEquals(91000L, budget.withVariablesCents)
        assertFalse(lines.any { it.itemId == "past" })
    }
    private fun scheduled(id: String, date: LocalDate, cents: Long, movement: Movement, category: Category) =
        Record(id, cents, movement, PaymentMethod.PIX, category, "Principal", id, at(date))

    @Test fun `scheduled linked expense replaces forecast amount date and keeps commitment group`() {
        val rent = line("rent", today.plusDays(1), 90000).rule
        val salary = line("salary", today.plusDays(10), 400000, ForecastKind.INCOME, Category.SALARY).rule
        val record = scheduled("scheduled-rent", today.plusDays(3), 30000, Movement.EXPENSE, Category.HOME)
            .copy(forecastItemId="rent", forecastOccurrenceKey="once")
        val lines = PaydayBudgets.upcomingLines(ForecastDataset(rules=listOf(rent,salary)),listOf(record),zone,at(today))
        val occurrences = lines.filter { it.itemId == "rent" }
        assertEquals(1,occurrences.size)
        assertEquals(today.plusDays(3),occurrences.single().date)
        assertEquals(30000L,occurrences.single().amountCents)
        val payday = PaydayBudgets.nextPayday(lines,emptyList(),today)!!
        val budget = PaydayBudgets.compute(100000,lines,today,payday)
        assertEquals(70000L,budget.totalCents)
        assertEquals(49000L,budget.weeklyCents)
    }

    @Test fun `future salary record replaces linked forecast date and supports salary without a rule`() {
        val salary = line("salary",today.plusDays(2),400000,ForecastKind.INCOME,Category.SALARY).rule
        val record = scheduled("scheduled-salary",today.plusDays(5),390000,Movement.INCOME,Category.SALARY)
            .copy(forecastItemId="salary",forecastOccurrenceKey="once")
        val lines = PaydayBudgets.upcomingLines(ForecastDataset(rules=listOf(salary)),listOf(record),zone,at(today))
        assertEquals(1,lines.size)
        assertEquals(390000L,lines.single().amountCents)
        assertEquals(today.plusDays(5),PaydayBudgets.nextPayday(lines,emptyList(),today))
        val plain = record.copy(forecastItemId=null,forecastOccurrenceKey=null)
        val noRule = PaydayBudgets.upcomingLines(ForecastDataset(),listOf(plain),zone,at(today))
        assertEquals(today.plusDays(5),PaydayBudgets.nextPayday(noRule,emptyList(),today))
    }

    @Test fun `salary received today selects following monthly occurrence without counting paid expenses again`() {
        val salary = line("salary",today,400000,ForecastKind.INCOME,Category.SALARY).rule.copy(frequency=ForecastFrequency.MONTHLY)
        val rent = line("rent",today,30000).rule
        val received = scheduled("received",today,390000,Movement.INCOME,Category.SALARY)
            .copy(forecastItemId="salary",forecastOccurrenceKey="month:2026-11")
        val paid = scheduled("paid",today,28000,Movement.EXPENSE,Category.HOME)
            .copy(forecastItemId="rent",forecastOccurrenceKey="once")
        val lines = PaydayBudgets.upcomingLines(ForecastDataset(rules=listOf(salary,rent)),listOf(received,paid),zone,at(today))
        assertFalse(lines.any { it.itemId == "rent" })
        assertEquals(today.plusMonths(1),PaydayBudgets.nextPayday(lines,emptyList(),today))
    }

    @Test fun `scheduled unlinked expenses stay variable and expenses rescheduled after salary are outside budget`() {
        val salary = line("salary",today.plusDays(10),400000,ForecastKind.INCOME,Category.SALARY).rule
        val rent = line("rent",today.plusDays(1),30000).rule
        val later = scheduled("later-rent",today.plusDays(11),31000,Movement.EXPENSE,Category.HOME)
            .copy(forecastItemId="rent",forecastOccurrenceKey="once")
        val variable = scheduled("variable",today.plusDays(2),20000,Movement.EXPENSE,Category.FOOD)
        val lines = PaydayBudgets.upcomingLines(ForecastDataset(rules=listOf(salary,rent)),listOf(later,variable),zone,at(today))
        val budget = PaydayBudgets.compute(100000,lines,today,PaydayBudgets.nextPayday(lines,emptyList(),today)!!)
        assertEquals(0L,budget.pendingCommitmentsCents)
        assertEquals(20000L,budget.variableForecastCents)
        assertEquals(80000L,budget.withVariablesCents)
    }

    @Test fun salaryAtFormerHorizonIncludesOnlyExpensesBeforePayday() {
        val payday = today.plusDays(62)
        val salary = line("salary", payday, 400000, ForecastKind.INCOME, Category.SALARY).rule
        val before = line("before", today.plusDays(61), 19001).rule
        val onPayday = line("on-payday", payday, 20000).rule
        val data = ForecastDataset(rules = listOf(salary, before, onPayday))

        assertEquals(PaydayBudget(payday, 62, 100000, 19001), budget(data))
    }

    @Test fun salarySearchContinuesPastSkippedCoveredAndRecategorizedMonths() {
        val salary = line("salary", today, 400000, ForecastKind.INCOME, Category.SALARY).rule
            .copy(frequency = ForecastFrequency.MONTHLY)
        val received = scheduled("received", today, 400000, Movement.INCOME, Category.SALARY)
            .copy(forecastItemId = salary.itemId, forecastOccurrenceKey = "month:2027-01")
        val data = ForecastDataset(rules = listOf(salary), overrides = listOf(
            ForecastOverride(salary.itemId, "month:2026-11", ignored = true),
            ForecastOverride(salary.itemId, "month:2026-12", active = false),
            ForecastOverride(salary.itemId, "month:2027-02", category = Category.OTHER_INCOME),
        ))

        assertEquals(LocalDate.of(2027, 3, 12), budget(data, listOf(received))?.payday)
    }

    @Test(timeout = 5000) fun distantSalaryIncludesSparseExpensesWithoutWalkingTheGap() {
        val payday = LocalDate.of(900000, 7, 12)
        val salary = line("salary", payday, 400000, ForecastKind.INCOME, Category.SALARY).rule
            .copy(frequency = ForecastFrequency.MONTHLY)
        val nearExpense = line("near", today.plusDays(1), 20000).rule
        val distantExpense = line("distant", payday.minusMonths(2), 11000).rule
            .copy(frequency = ForecastFrequency.MONTHLY, group = ForecastGroup.VARIABLE)
        val data = ForecastDataset(rules = listOf(salary, nearExpense, distantExpense))

        val expectedDays = 327_978_146
        assertEquals(PaydayBudget(payday, expectedDays, 100000, 20000, 22000), budget(data))
    }

    @Test fun scheduledSalaryAfterHorizonReplacesForecastDate() {
        val salary = line("salary", today.plusDays(1), 400000, ForecastKind.INCOME, Category.SALARY).rule
        val payday = today.plusDays(100)
        val record = scheduled("scheduled-salary", payday, 390000, Movement.INCOME, Category.SALARY)
            .copy(forecastItemId = salary.itemId, forecastOccurrenceKey = "once")
        val before = line("before", today.plusDays(99), 21000).rule

        assertEquals(PaydayBudget(payday, 100, 100000, 21000),
            budget(ForecastDataset(rules = listOf(salary, before)), listOf(record)))
    }

    @Test fun movedInstallmentCanBecomeSalaryThroughAnOccurrenceOverride() {
        val payday = today.plusDays(90)
        val income = line("income", today, 400000, ForecastKind.INCOME, Category.OTHER_INCOME).rule
            .copy(frequency = ForecastFrequency.INSTALLMENTS, installmentCount = 2)
        val data = ForecastDataset(rules = listOf(income), overrides = listOf(
            ForecastOverride(income.itemId, "installment:2", date = payday, category = Category.SALARY),
        ))

        assertEquals(payday, budget(data)?.payday)
    }

    @Test fun cycleDayClampingCanLeaveSalaryUncoveredSeveralMonthsLater() {
        val start = LocalDate.of(2026, 9, 30)
        val salary = line("salary", start, 400000, ForecastKind.INCOME, Category.SALARY).rule
            .copy(frequency = ForecastFrequency.MONTHLY, cycleAnchored = true, statementKey = "net")
        val total = salary.copy(itemId = "total", startDate = start.minusDays(1), dayOfMonth = 29,
            cycleAnchored = false, category = Category.OTHER_INCOME, statementTotal = true)
        val data = ForecastDataset(rules = listOf(salary, total), cycleRules = listOf(CycleRule(start, 30)))

        assertEquals(LocalDate.of(2027, 1, 30), budget(data)?.payday)
    }

    @Test(timeout = 5000) fun permanentlyExcludedSalaryNeverBecomesThePayday() {
        val salary = line("salary", today, 400000, ForecastKind.INCOME, Category.SALARY).rule
            .copy(frequency = ForecastFrequency.MONTHLY, statementKey = "net")
        val total = salary.copy(itemId = "total", category = Category.OTHER_INCOME, statementTotal = true)

        assertNull(budget(ForecastDataset(rules = listOf(salary, total))))
    }

    @Test fun skippedSalarySeriesEndWithoutAnotherPayday() {
        val monthly = line("monthly", today, 400000, ForecastKind.INCOME, Category.SALARY).rule
            .copy(frequency = ForecastFrequency.MONTHLY)
        val disabled = monthly.copy(effectiveFrom = today.plusMonths(2), active = false)
        val past = today.minusMonths(2)
        val installments = monthly.copy(itemId = "installments", startDate = past, effectiveFrom = past,
            frequency = ForecastFrequency.INSTALLMENTS, installmentCount = 2)
        val data = ForecastDataset(rules = listOf(monthly, disabled, installments), overrides = listOf(
            ForecastOverride(monthly.itemId, "month:2026-11", ignored = true),
            ForecastOverride(monthly.itemId, "month:2026-12", ignored = true),
        ))

        assertNull(budget(data))
    }

    @Test(timeout = 5000) fun distantSalaryIncludesEveryRecurringExpenseUpToPayday() {
        val payday = LocalDate.of(900000, 7, 12)
        val salary = line("salary", payday, 400000, ForecastKind.INCOME, Category.SALARY).rule
        val monthly = line("monthly", today, 101).rule.copy(frequency = ForecastFrequency.MONTHLY)
        val data = ForecastDataset(rules = listOf(salary, monthly))

        assertEquals(1_088_344_084L, budget(data)?.pendingCommitmentsCents)
    }

    @Test fun distantSalaryRejectsABudgetThatExceedsTheRangeOfCents() {
        val salary = line("salary", LocalDate.of(9999, 11, 1), 400000, ForecastKind.INCOME, Category.SALARY).rule
        val commitments = (1..100).map { index ->
            line("commitment-$index", today, Money.MAX_CENTS).rule.copy(frequency = ForecastFrequency.MONTHLY)
        }
        val data = ForecastDataset(rules = commitments + salary)

        assertThrows(ArithmeticException::class.java) { budget(data) }
    }

    @Test fun undatedExpensesCountTheCurrentAndShortenedCyclesBeforePayday() {
        val payday = LocalDate.of(2027, 1, 20)
        val initialCycle = CycleRule(LocalDate.of(2026, 10, 28), 28)
        val changedCycle = CycleRule(LocalDate.of(2026, 12, 15), 20)
        val salary = line("salary", payday, 400000, ForecastKind.INCOME, Category.SALARY).rule
        val groceries = line("groceries", initialCycle.effectiveStart, 9000, group = ForecastGroup.VARIABLE).rule
            .copy(frequency = ForecastFrequency.MONTHLY, cycleAnchored = true)
        val data = ForecastDataset(rules = listOf(salary, groceries), cycleRules = listOf(initialCycle, changedCycle))

        assertEquals(PaydayBudget(payday, 69, 100000, 0, 36000), budget(data))
    }

    @Test fun whenSalaryArrivesTodayNoExpensesAreDueBeforeIt() {
        val salary = scheduled("salary-today", today, 400000, Movement.INCOME, Category.SALARY)
            .copy(occurredAt = at(today) + 1)
        val expense = line("expense-today", today, 20000).rule

        assertEquals(PaydayBudget(today, 0, 100000, 0),
            budget(ForecastDataset(rules = listOf(expense)), listOf(salary)))
    }

    private fun budget(data: ForecastDataset, records: List<Record> = emptyList()): PaydayBudget? =
        PaydayBudgets.fromForecasts(100000, data, records, zone, at(today))
}
