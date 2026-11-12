package com.mateuswilliam.financas.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class ForecastKind { INCOME, EXPENSE }
enum class ForecastFrequency { ONCE, MONTHLY, INSTALLMENTS }
enum class ForecastGroup { COMMITMENT, VARIABLE }
data class ForecastRule(
    val itemId: String, val effectiveFrom: LocalDate, val title: String,
    val kind: ForecastKind, val amountCents: Long, val category: Category,
    val frequency: ForecastFrequency, val startDate: LocalDate,
    val dayOfMonth: Int = startDate.dayOfMonth, val installmentCount: Int = 1,
    val cycleAnchored: Boolean = false, val group: ForecastGroup = ForecastGroup.COMMITMENT,
    val estimated: Boolean = false, val active: Boolean = true,
    val statementKey: String = "", val statementTotal: Boolean = false,
    val account: String = "", val legacySource: String? = null,
)
data class ForecastOverride(
    val itemId: String, val occurrenceKey: String, val amountCents: Long? = null,
    val date: LocalDate? = null, val ignored: Boolean = false, val active: Boolean? = null,
    val title: String? = null, val category: Category? = null,
)
data class CycleRule(val effectiveStart: LocalDate, val day: Int)
data class BudgetPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init { require(start < endExclusive) }
    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}
enum class ReserveEventKind { SET, ADD, WITHDRAW }
data class ReserveEvent(
    val id: Long, val kind: ReserveEventKind, val amountCents: Long, val createdAt: Long,
    val reason: String, val cycleStart: LocalDate? = null,
)
data class ForecastDataset(
    val rules: List<ForecastRule> = emptyList(), val overrides: List<ForecastOverride> = emptyList(),
    val cycleRules: List<CycleRule> = emptyList(), val reserveEvents: List<ReserveEvent> = emptyList(),
    val reserveGoalCents: Long? = null,
    val recordLinks: Map<Pair<String, String>, Set<String>> = emptyMap(),
    val monthlySalaryReferences: Map<YearMonth, Long> = emptyMap(),
)
data class ForecastLine(
    val itemId: String, val occurrenceKey: String, val title: String, val date: LocalDate,
    val amountCents: Long, val kind: ForecastKind, val category: Category, val group: ForecastGroup,
    val frequency: ForecastFrequency, val estimated: Boolean, val ignored: Boolean,
    val included: Boolean, val statementKey: String, val statementTotal: Boolean, val rule: ForecastRule,
)
data class ForecastReport(
    val period: BudgetPeriod, val lines: List<ForecastLine>, val incomeCents: Long,
    val commitmentsCents: Long, val variableCents: Long, val totalExpensesCents: Long,
    val marginCents: Long, val expensePercentage: BigDecimal?, val allocatedCents: Long,
    val unallocatedCents: Long, val reserveBalanceCents: Long?,
)
