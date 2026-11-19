package com.mateuswilliam.financas.data

import androidx.room.withTransaction
import androidx.room.InvalidationTracker
import com.mateuswilliam.financas.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.time.LocalDate

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

    suspend fun saveRule(rule:ForecastRule,replaceFuture:Boolean=true) = database.withTransaction {
        require(ForecastValidation.error(rule)==null) { ForecastValidation.error(rule) ?: "Previsão inválida." }
        val linked = dao.allRecords().filter { it.forecastItemId == rule.itemId }
        require(linked.all { (if (it.movement == Movement.INCOME) ForecastKind.INCOME else ForecastKind.EXPENSE) == rule.kind }) {
            "Há lançamentos vinculados. Crie outra previsão para mudar entre receita e despesa."
        }
        if(replaceFuture) dao.deleteFutureForecastRules(rule.itemId,rule.effectiveFrom.toString())
        dao.upsertForecastRule(ForecastRuleEntity.from(rule))
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
