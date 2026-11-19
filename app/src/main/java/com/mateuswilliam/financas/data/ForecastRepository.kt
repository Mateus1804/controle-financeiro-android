package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import androidx.room.InvalidationTracker
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.util.UUID

/** Every emission and validation reads the five tables in a single Room transaction. */
class ForecastRepository(private val database:FinanceDatabase) {
    private val dao = database.financeDao()
    val dataset:Flow<ForecastDataset> = callbackFlow {
        val observer = object:InvalidationTracker.Observer("forecast_rules","forecast_overrides","cycle_rules","reserve_events","forecast_meta","records") {
            override fun onInvalidated(tables:Set<String>) { trySend(Unit) }
        }
        database.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }.conflate().map { database.withTransaction { readDataset() } }.distinctUntilChanged()

    private suspend fun readDataset() = ForecastDataset(dao.allForecastRules().map { it.model() },
        dao.allForecastOverrides().map { it.model() },dao.allCycleRules().map { it.model() },
        dao.allReserveEvents().map { it.model() },dao.allForecastMeta().firstOrNull { it.key=="reserveGoal" }?.value?.toLong())

    suspend fun saveRule(
        rule: ForecastRule, replaceFuture: Boolean = true, replaceOccurrenceKey: String? = null,
    ) = database.withTransaction {
        ForecastValidation.error(rule)?.let { throw IllegalArgumentException(it) }
        val linked = dao.allRecords().filter { it.forecastItemId == rule.itemId }
        require(linked.all { (if (it.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE) == rule.kind }) {
            "Há lançamentos vinculados. Crie outra previsão para mudar entre receita e despesa."
        }
        val history = dao.allForecastRules().filter { it.itemId == rule.itemId }.map { it.model() }
        val previous = history.filter { it.effectiveFrom <= rule.effectiveFrom }.maxByOrNull { it.effectiveFrom }
        val restartInstallments = replaceFuture && previous != null && previous.frequency != ForecastFrequency.INSTALLMENTS &&
            rule.active && rule.frequency == ForecastFrequency.INSTALLMENTS &&
            history.any { it.frequency == ForecastFrequency.INSTALLMENTS && it.effectiveFrom < rule.effectiveFrom }
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
            val override = dao.allForecastOverrides().firstOrNull { it.itemId == rule.itemId && it.occurrenceKey == key }
            if (override != null) {
                if (override.ignored || override.active != null) {
                    dao.upsertForecastOverride(override.copy(amountCents = null, date = null, title = null, category = null))
                } else dao.deleteForecastOverride(rule.itemId, key)
            }
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
        val proposed=CycleConfiguration.rulesAfterChange(day,today,readDataset())
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
