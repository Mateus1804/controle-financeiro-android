package com.mateuswilliam.financas.data

import androidx.room.*
import com.mateuswilliam.financas.core.*
import java.time.LocalDate

@Entity(tableName = "forecast_rules", primaryKeys = ["itemId", "effectiveFrom"])
data class ForecastRuleEntity(val itemId:String, val effectiveFrom:String, val title:String,
 val kind:String, val amountCents:Long, val category:Category, val frequency:String,
 val startDate:String, val dayOfMonth:Int, val installmentCount:Int, val cycleAnchored:Boolean,
 val group:String, val estimated:Boolean, val active:Boolean, val statementKey:String,
 val statementTotal:Boolean, val account:String, val legacySource:String?) {
 fun model() = ForecastRule(itemId, LocalDate.parse(effectiveFrom), title, ForecastKind.valueOf(kind), amountCents,
 category, ForecastFrequency.valueOf(frequency), LocalDate.parse(startDate), dayOfMonth, installmentCount,
 cycleAnchored, ForecastGroup.valueOf(group), estimated, active, statementKey, statementTotal, account, legacySource)
 companion object { fun from(r:ForecastRule) = ForecastRuleEntity(r.itemId,r.effectiveFrom.toString(),r.title,r.kind.name,
 r.amountCents,r.category,r.frequency.name,r.startDate.toString(),r.dayOfMonth,r.installmentCount,r.cycleAnchored,
 r.group.name,r.estimated,r.active,r.statementKey,r.statementTotal,r.account,r.legacySource) }
}
@Entity(tableName = "forecast_overrides", primaryKeys = ["itemId", "occurrenceKey"])
data class ForecastOverrideEntity(val itemId:String,val occurrenceKey:String,val amountCents:Long?,val date:String?,
 val ignored:Boolean,val active:Boolean?,val title:String?,val category:Category?) {
 fun model() = ForecastOverride(itemId,occurrenceKey,amountCents,date?.let(LocalDate::parse),ignored,active,title,category)
 companion object { fun from(o:ForecastOverride) = ForecastOverrideEntity(o.itemId,o.occurrenceKey,o.amountCents,
 o.date?.toString(),o.ignored,o.active,o.title,o.category) }
}
@Entity(tableName = "cycle_rules")
data class CycleRuleEntity(@PrimaryKey val effectiveStart:String,val day:Int) {
 fun model() = CycleRule(LocalDate.parse(effectiveStart),day)
}
@Entity(tableName = "reserve_events")
data class ReserveEventEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val kind:String,val amountCents:Long,
 val createdAt:Long,val reason:String,val cycleStart:String?) {
 fun model() = ReserveEvent(id,ReserveEventKind.valueOf(kind),amountCents,createdAt,reason,cycleStart?.let(LocalDate::parse))
}
@Entity(tableName = "forecast_meta")
data class ForecastMetaEntity(@PrimaryKey val key:String,val value:String)
