package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Cada emissão reúne os dados de planejamento em uma transação. */
class ForecastRepository(private val database:FinanceDatabase) {
    private val dao = database.financeDao()
    val dataset: Flow<ForecastDataset> = database.invalidationTracker.createFlow(
        "forecast_rules", "forecast_overrides", "cycle_rules", "reserve_events", "forecast_meta",
    ).conflate().map { database.withTransaction { readDataset() } }.distinctUntilChanged()

    private suspend fun readDataset() = ForecastDataset(dao.allForecastRules().map { it.model() },
        dao.allForecastOverrides().map { it.model() },dao.allCycleRules().map { it.model() },
        dao.allReserveEvents().map { it.model() },dao.allForecastMeta().firstOrNull { it.key=="reserveGoal" }?.value?.toLong())

    suspend fun saveRule(
        rule: ForecastRule, replaceFuture: Boolean = true, replaceOccurrenceKey: String? = null,
    ) = database.withTransaction {
        ForecastValidation.error(rule)?.let { throw IllegalArgumentException(it) }
        val linked = dao.recordsLinkedToForecast(rule.itemId)
        require(linked.all { (if (it.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE) == rule.kind }) {
            "Há lançamentos vinculados. Crie outra previsão para mudar entre receita e despesa."
        }
        val history = dao.forecastRulesFor(rule.itemId).map { it.model() }
        val overrides = dao.forecastOverridesFor(rule.itemId)
        val previous = history.filter { it.effectiveFrom <= rule.effectiveFrom }.maxByOrNull { it.effectiveFrom }
        val hadInstallments = history.any { it.frequency == ForecastFrequency.INSTALLMENTS && it.effectiveFrom < rule.effectiveFrom } ||
            overrides.any { it.occurrenceKey.startsWith("installment:") } ||
            linked.any { it.forecastOccurrenceKey?.startsWith("installment:") == true }
        val restartInstallments = replaceFuture && previous != null && previous.frequency != ForecastFrequency.INSTALLMENTS &&
            rule.active && rule.frequency == ForecastFrequency.INSTALLMENTS && hadInstallments
        val occurrenceKeys = linked.mapNotNull { it.forecastOccurrenceKey }.toSet()
        if (rule.active && occurrenceKeys.isNotEmpty() && history.any { !it.sameScheduleAs(rule) }) {
            val cycles = dao.allCycleRules().map { it.model() }
            val data = ForecastDataset(history, overrides.map { it.model() }, cycles)
            require(!data.hasLinkedScheduleInScope(rule, occurrenceKeys, replaceFuture, restartInstallments)) {
                "A mudança de frequência afetaria lançamentos já vinculados. Escolha um ciclo posterior ou crie outra previsão."
            }
        }
        val stop = history.filter { !it.active && it.effectiveFrom > rule.effectiveFrom }.minByOrNull { it.effectiveFrom }
        if (replaceFuture) dao.deleteFutureForecastRules(rule.itemId, rule.effectiveFrom.toString())
        val updated = if (restartInstallments) {
            // The restarted purchase has its own sequence. Existing links and adjustments stay on the original item.
            dao.upsertForecastRule(ForecastRuleEntity.from(requireNotNull(previous).copy(effectiveFrom = rule.effectiveFrom, active = false)))
            rule.copy(itemId = UUID.randomUUID().toString())
        } else rule
        dao.upsertForecastRule(ForecastRuleEntity.from(updated))
        if (replaceFuture && updated.active && stop != null) {
            dao.upsertForecastRule(ForecastRuleEntity.from(updated.copy(effectiveFrom = stop.effectiveFrom, active = false)))
        }
        replaceOccurrenceKey?.let { key ->
            val override = overrides.firstOrNull { it.occurrenceKey == key }
            val date = override?.date?.let(LocalDate::parse)
            val dayChanged = previous?.dayOfMonth != updated.dayOfMonth
            val firstMonth = YearMonth.from(updated.startDate)
            val firstDue = firstMonth.atDay(minOf(updated.dayOfMonth, firstMonth.lengthOfMonth()))
            val advancesFirstMonth = updated.frequency == ForecastFrequency.MONTHLY && !updated.cycleAnchored &&
                key == "month:$firstMonth" && updated.effectiveFrom <= updated.startDate && firstDue < updated.startDate
            val due = when {
                advancesFirstMonth && (date == null || dayChanged) -> maxOf(firstDue, updated.effectiveFrom)
                date != null && dayChanged -> {
                    val cycle = CycleCalendar.periodContaining(date, dao.allCycleRules().map { it.model() })
                    sequenceOf(YearMonth.from(date), YearMonth.from(cycle.start), YearMonth.from(cycle.endExclusive.minusDays(1)))
                        .map { it.atDay(minOf(updated.dayOfMonth, it.lengthOfMonth())) }
                        .firstOrNull { it in cycle } ?: date
                }
                else -> date
            }
            if (due != null || override?.ignored == true || override?.active != null) {
                val occurrence = override?.model() ?: ForecastOverride(updated.itemId, key)
                dao.upsertForecastOverride(ForecastOverrideEntity.from(occurrence.copy(
                    amountCents = null, date = due, title = null, category = null,
                )))
            } else if (override != null) dao.deleteForecastOverride(rule.itemId, key)
        }
    }
    suspend fun saveOverride(value:ForecastOverride) = database.withTransaction {
        require(dao.allForecastRules().any { it.itemId==value.itemId }) { "Previsão não encontrada." }
        val amount=value.amountCents
        val title=value.title
        require(amount==null || amount in 1..Money.MAX_CENTS)
        require(ForecastSnapshots.validOccurrenceKey(value.occurrenceKey))
        require(title==null || title.isNotBlank() && title.length<=256 && '\u0000' !in title)
        require(value.occurrenceKey.matches(Regex("once|month:\\d{4}-\\d{2}|installment:[1-9]\\d*|cycle:\\d{4}-\\d{2}-\\d{2}")))
        dao.upsertForecastOverride(ForecastOverrideEntity.from(value))
    }
    suspend fun setCycleDay(day:Int,today:LocalDate) = database.withTransaction {
        require(day in 1..31)
        val proposed = CycleConfiguration.rulesAfterChange(day, today, readDataset(), dao.linkedCycleKeys())
        dao.clearCycleRules()
        proposed.forEach { dao.upsertCycleRule(CycleRuleEntity(it.effectiveStart.toString(),it.day)) }
    }
    suspend fun setReserveGoal(cents:Long?) = database.withTransaction {
        require(cents==null || cents in 0..Money.MAX_CENTS)
        // Preserve import provenance while removing only the optional goal.
        val meta=dao.allForecastMeta().filterNot { it.key=="reserveGoal" }
        dao.clearForecastMeta(); meta.forEach { dao.upsertForecastMeta(it) }
        if(cents!=null) dao.upsertForecastMeta(ForecastMetaEntity("reserveGoal",cents.toString()))
    }
}

private fun ForecastRule.sameScheduleAs(other: ForecastRule) =
    frequency == other.frequency && cycleAnchored == other.cycleAnchored

private fun ForecastDataset.hasLinkedScheduleInScope(
    updated: ForecastRule, keys: Set<String>, replaceFuture: Boolean, restartInstallments: Boolean,
): Boolean {
    val start = if (updated.frequency == ForecastFrequency.ONCE) minOf(updated.effectiveFrom, updated.startDate) else updated.effectiveFrom
    val end = if (replaceFuture) null else rules.firstOrNull { it.effectiveFrom > updated.effectiveFrom }?.effectiveFrom
    return findForecastOccurrences(keys).any { line ->
        val effective = line.rule.effectiveFrom
        // Uma ocorrência antecipada pode pertencer a uma versão que a alteração remove.
        val overwritesRule = effective == updated.effectiveFrom || (replaceFuture && effective > updated.effectiveFrom)
        (restartInstallments || !line.rule.sameScheduleAs(updated)) &&
            (overwritesRule || line.date >= start && (end == null || line.date < end))
    }
}

internal fun ForecastDataset.findForecastOccurrences(keys: Set<String>): Sequence<ForecastLine> {
    val dates = buildSet {
        for (rule in rules) {
            add(rule.effectiveFrom)
            keys.mapNotNullTo(this) { rule.nominalDate(it) }
        }
        overrides.filter { it.occurrenceKey in keys }.mapNotNullTo(this) { it.date }
    }
    val forecasts = PendingForecasts(this, emptyList())
    return dates.asSequence().map { CycleCalendar.periodContaining(it, cycleRules) }.distinct()
        .flatMap { forecasts.lines(it) }
        .filter { it.occurrenceKey in keys && it.reusedInstallmentStart == null }
}

private fun ForecastRule.nominalDate(key: String): LocalDate? {
    val month = when (frequency) {
        ForecastFrequency.ONCE -> return startDate.takeIf { key == "once" }
        ForecastFrequency.MONTHLY -> {
            if (cycleAnchored) return if (key.startsWith("cycle:")) LocalDate.parse(key.removePrefix("cycle:")) else null
            if (key.startsWith("month:")) YearMonth.parse(key.removePrefix("month:")) else return null
        }
        ForecastFrequency.INSTALLMENTS -> {
            if (!key.startsWith("installment:")) return null
            val number = key.removePrefix("installment:").toIntOrNull()?.takeIf { it in 1..installmentCount } ?: return null
            YearMonth.from(startDate).plusMonths(number - 1L)
        }
    }
    return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
}
