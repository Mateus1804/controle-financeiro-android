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
        val observer = object:InvalidationTracker.Observer("forecast_rules","forecast_overrides","cycle_rules","reserve_events","forecast_meta","recurring_months","occurrences","commitments") {
            override fun onInvalidated(tables:Set<String>) { trySend(Unit) }
        }
        database.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }.conflate().map { database.withTransaction { readDataset() } }.distinctUntilChanged()

    private suspend fun readDataset() = ForecastDataset(dao.allForecastRules().map { it.model() },
        dao.allForecastOverrides().map { it.model() },dao.allCycleRules().map { it.model() },
        dao.allReserveEvents().map { it.model() },dao.allForecastMeta().firstOrNull { it.key=="reserveGoal" }?.value?.toLong(), readRecordLinks())

    // These keys match the importer. Links can span months; a payment is counted on its own date.
    private suspend fun readRecordLinks(): Map<Pair<String, String>, Set<String>> {
        val links = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        fun link(item: String, occurrence: String, record: String?) {
            if (record != null) links.getOrPut(item to occurrence) { mutableSetOf() }.add(record)
        }
        val months = dao.allRecurringMonths()
        val occurrences = dao.allOccurrences()
        val recurringIds = dao.allRecurring().mapTo(hashSetOf()) { it.id }
        val occupied = months.mapTo(hashSetOf()) { it.recurringId to it.month }
        val dates = months.mapTo(hashSetOf()) { it.recurringId to it.dueDate }
        for (row in months) link("legacy:recurring:${row.recurringId}", "month:${row.month}", row.recordId)
        val separateOccurrences = hashSetOf<Pair<String, String>>()
        for (row in occurrences) {
            val month = row.dueDate.take(7)
            if ((row.recurringId to row.dueDate) in dates || occupied.add(row.recurringId to month))
                link("legacy:recurring:${row.recurringId}", "month:$month", row.recordId)
            else {
                separateOccurrences.add(row.recurringId to row.dueDate)
                link("legacy:occurrence:${row.recurringId}:${row.dueDate}", "once", row.recordId)
            }
        }
        val occurrenceDates = occurrences.mapTo(hashSetOf()) { it.recurringId to it.dueDate }
        for (row in dao.allCommitments()) {
            val id = row.recurringId
            if (id != null && (id in recurringIds || (id to row.dueDate) in dates || (id to row.dueDate) in occurrenceDates)) {
                val month = row.dueDate.take(7)
                if ((id to row.dueDate) in separateOccurrences)
                    link("legacy:occurrence:$id:${row.dueDate}", "once", row.recordId)
                else link("legacy:recurring:$id", "month:$month", row.recordId)
            } else link("legacy:commitment:${row.id}", "once", row.recordId)
        }
        return links
    }

    suspend fun initialize() = database.withTransaction {
        if (dao.allForecastMeta().none { it.key=="legacyImported" }) {
            LegacyForecastImporter.import(dao)
            dao.upsertForecastMeta(ForecastMetaEntity("legacyImported","1"))
        }
    }
    suspend fun saveRule(rule:ForecastRule,replaceFuture:Boolean=true) = database.withTransaction {
        require(ForecastValidation.error(rule)==null) { ForecastValidation.error(rule) ?: "Previsão inválida." }
        if(replaceFuture) dao.deleteFutureForecastRules(rule.itemId,rule.effectiveFrom.toString())
        dao.upsertForecastRule(ForecastRuleEntity.from(rule))
    }
    suspend fun saveOverride(value:ForecastOverride) = database.withTransaction {
        require(dao.allForecastRules().any { it.itemId==value.itemId }) { "Previsão não encontrada." }
        val amount=value.amountCents
        val title=value.title
        require(amount==null || amount!=0L && amount in -Money.MAX_CENTS..Money.MAX_CENTS)
        require(amount==null || amount>=0 || dao.allForecastRules().any { it.itemId==value.itemId && it.legacySource!=null })
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
    suspend fun addReserveEvent(kind:ReserveEventKind,cents:Long,reason:String,cycleStart:LocalDate?=null) = database.withTransaction {
        require(cents in 0..Money.MAX_CENTS && (kind==ReserveEventKind.SET || cents>0) && reason.isNotBlank() && reason.length<=200 && '\u0000' !in reason)
        val current=readDataset()
        val balance=ForecastCalculator.reserveBalance(current.reserveEvents)
        if(kind!=ReserveEventKind.SET) require(balance!=null) { "Informe primeiro o saldo da reserva." }
        if(kind==ReserveEventKind.WITHDRAW) require(cents <= (balance ?: 0)) { "Saldo insuficiente na reserva." }
        if(kind==ReserveEventKind.ADD) {
            require(Math.addExact(balance ?: 0,cents)<=Money.MAX_CENTS)
            if(cycleStart!=null) {
                val period=CycleCalendar.periodContaining(cycleStart,current.cycleRules)
                require(period.start==cycleStart) { "Ciclo inválido." }
                require(cents<=ForecastCalculator.report(current,period).unallocatedCents.coerceAtLeast(0)) { "A destinação supera a sobra disponível." }
            }
        }
        require(cycleStart==null || kind==ReserveEventKind.ADD)
        dao.insertReserveEvent(ReserveEventEntity(kind=kind.name,amountCents=cents,createdAt=System.currentTimeMillis(),reason=reason.trim(),cycleStart=cycleStart?.toString()))
        Unit
    }
    suspend fun setReserveGoal(cents:Long?) = database.withTransaction {
        require(cents==null || cents in 0..Money.MAX_CENTS)
        // Preserve import provenance while removing only the optional goal.
        val meta=dao.allForecastMeta().filterNot { it.key=="reserveGoal" }
        dao.clearForecastMeta(); meta.forEach { dao.upsertForecastMeta(it) }
        if(cents!=null) dao.upsertForecastMeta(ForecastMetaEntity("reserveGoal",cents.toString()))
    }
}
