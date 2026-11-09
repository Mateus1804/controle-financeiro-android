package com.mateuswilliam.financas.data

import com.mateuswilliam.financas.core.*
import org.json.JSONObject
import java.time.LocalDate
import com.mateuswilliam.financas.data.BackupSnapshot.text
import com.mateuswilliam.financas.data.BackupSnapshot.long
import com.mateuswilliam.financas.data.BackupSnapshot.integer
import com.mateuswilliam.financas.data.BackupSnapshot.bool
import com.mateuswilliam.financas.data.BackupSnapshot.nullableText

internal object ForecastSnapshots {
    private fun json(vararg fields:Pair<String,Any?>)=JSONObject().apply { fields.forEach { (k,v)->put(k,v ?: JSONObject.NULL) } }
    fun ruleJson(r:ForecastRuleEntity)=json("itemId" to r.itemId,"effectiveFrom" to r.effectiveFrom,"title" to r.title,
        "kind" to r.kind,"amountCents" to r.amountCents,"category" to r.category.name,"frequency" to r.frequency,
        "startDate" to r.startDate,"dayOfMonth" to r.dayOfMonth,"installmentCount" to r.installmentCount,
        "cycleAnchored" to r.cycleAnchored,"group" to r.group,"estimated" to r.estimated,"active" to r.active,
        "statementKey" to r.statementKey,"statementTotal" to r.statementTotal,"account" to r.account,"legacySource" to r.legacySource)
    fun rule(j:JSONObject)=ForecastRuleEntity(j.text("itemId",320),j.text("effectiveFrom",10),j.text("title",256),
        j.text("kind",20),j.long("amountCents"),Category.valueOf(j.text("category",30)),j.text("frequency",20),
        j.text("startDate",10),j.integer("dayOfMonth",1,31),j.integer("installmentCount",1,600),j.bool("cycleAnchored"),
        j.text("group",20),j.bool("estimated"),j.bool("active"),j.text("statementKey",200),j.bool("statementTotal"),
        j.text("account",80),j.nullableText("legacySource",512))
    fun overrideJson(o:ForecastOverrideEntity)=json("itemId" to o.itemId,"occurrenceKey" to o.occurrenceKey,"amountCents" to o.amountCents,
        "date" to o.date,"ignored" to o.ignored,"active" to o.active,"title" to o.title,"category" to o.category?.name)
    fun overrideRow(j:JSONObject)=ForecastOverrideEntity(j.text("itemId",320),j.text("occurrenceKey",40),
        if(j.isNull("amountCents")) null else j.long("amountCents"),j.nullableText("date",10),j.bool("ignored"),
        if(j.isNull("active")) null else j.bool("active"),j.nullableText("title",256),j.nullableText("category",30)?.let(Category::valueOf))
    fun validOccurrenceKey(key:String):Boolean = runCatching {
        when {
            key=="once" -> true
            key.startsWith("month:") -> { java.time.YearMonth.parse(key.removePrefix("month:")); true }
            key.startsWith("cycle:") -> { LocalDate.parse(key.removePrefix("cycle:")); true }
            key.startsWith("installment:") -> key.removePrefix("installment:").toInt() in 1..600
            else -> false
        }
    }.getOrDefault(false)
    fun validate(s:FinanceSnapshot) {
        fun unique(values:List<String>) { require(values.distinct().size==values.size) { "Previsões repetidas no backup." } }
        unique(s.forecastRules.map { "${it.itemId}:${it.effectiveFrom}" })
        unique(s.forecastOverrides.map { "${it.itemId}:${it.occurrenceKey}" })
        unique(s.cycleRules.map { it.effectiveStart }); unique(s.reserveEvents.map { it.id.toString() });unique(s.forecastMeta.map { it.key })
        s.forecastRules.forEach { require(ForecastValidation.error(it.model())==null) { "Regra de previsão inválida." } }
        val ids=s.forecastRules.map { it.itemId }.toSet()
        s.forecastOverrides.forEach { o ->
            require(o.itemId in ids && o.occurrenceKey.matches(Regex("once|month:\\d{4}-\\d{2}|installment:[1-9]\\d*|cycle:\\d{4}-\\d{2}-\\d{2}")))
            require(validOccurrenceKey(o.occurrenceKey))
            require(o.amountCents==null || o.amountCents != 0L && o.amountCents in -Money.MAX_CENTS..Money.MAX_CENTS)
            require(o.amountCents==null || o.amountCents>=0 || s.forecastRules.any { it.itemId==o.itemId && it.legacySource!=null })
            o.date?.let(LocalDate::parse)
            require(o.title==null || o.title.isNotBlank() && o.title.length<=256 && '\u0000' !in o.title)
        }
        s.cycleRules.forEach { LocalDate.parse(it.effectiveStart); require(it.day in 1..31) }
        s.reserveEvents.forEach { e ->
            require(e.id>0 && e.amountCents in 0..Money.MAX_CENTS && e.createdAt>0 && e.reason.isNotBlank() && e.reason.length<=200 && '\u0000' !in e.reason)
            require(e.cycleStart==null || ReserveEventKind.valueOf(e.kind)==ReserveEventKind.ADD)
            e.cycleStart?.let { text ->
                val start=LocalDate.parse(text)
                require(CycleCalendar.periodContaining(start,s.cycleRules.map { it.model() }).start==start) { "Vínculo de destinação fora da fronteira do ciclo." }
            }
        }
        ForecastCalculator.reserveBalance(s.reserveEvents.map { it.model() })
        s.forecastMeta.forEach { m -> require(when(m.key) { "legacyImported" -> m.value=="1"; "reserveGoal" -> m.value.toLongOrNull()?.let { it in 0..Money.MAX_CENTS }==true; else -> false }) }
    }
}
